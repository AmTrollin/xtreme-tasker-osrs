package com.amtrollin.xtremetasker.persistence;

import com.amtrollin.xtremetasker.XtremeTaskerConfig;
import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.amtrollin.xtremetasker.ui.text.UiText;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

/**
 * Stores per-account {@link PersistedState} in two places: the RuneLite config and a JSON file
 * under {@code ~/.runelite/xtreme-tasker-states}. Keeps rotating backups, preserves corrupt
 * saves, and recovers progress from legacy (account-hash-only) keys.
 */
@Slf4j
@Singleton
public class PersistedStateStore
{
    private static final String CONFIG_GROUP = XtremeTaskerConfig.GROUP;
    private static final String STATE_KEY_PREFIX = "state_";
    private static final int STATE_BACKUP_COUNT = 3;
    private static final String STATE_CORRUPT_SUFFIX = "_corrupt";
    private static final String STATE_FILE_DIR_NAME = "xtreme-tasker-states";
    private static final String STATE_FILE_SUFFIX = ".json";

    private final ConfigManager configManager;
    private final Gson gson;

    @Inject
    public PersistedStateStore(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    // ---- Account keys ----

    public static String accountNameKey(String characterName)
    {
        String normalized = trimToNull(characterName);
        if (normalized == null)
        {
            return null;
        }

        normalized = normalized.toLowerCase(Locale.ROOT);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    public static String legacyAccountKeyFromScopedKey(String accountKey)
    {
        String trimmed = trimToNull(accountKey);
        if (trimmed == null)
        {
            return null;
        }

        int separator = trimmed.indexOf('_');
        return separator <= 0 ? trimmed : trimmed.substring(0, separator);
    }

    // ---- Public API ----

    /** Reads the primary save JSON, preferring the state file over the config entry. */
    public String readPrimaryJson(String accountKey)
    {
        String json = readFile(stateFile(accountKey));
        if (isBlank(json))
        {
            json = configManager.getConfiguration(CONFIG_GROUP, stateConfigKey(accountKey));
        }
        return json;
    }

    /**
     * Replaces the primary save (config + file) with a recovered state and flushes config to disk.
     */
    public void restorePrimary(String accountKey, PersistedState state, String reason)
    {
        String json = gson.toJson(state);
        configManager.setConfiguration(CONFIG_GROUP, stateConfigKey(accountKey), json);
        writePrimaryFile(accountKey, json);
        flushConfig(reason);
    }

    /** Mirrors a loaded state to the state file, so saves that only exist in config gain a file copy. */
    public void mirrorToFile(String accountKey, PersistedState state)
    {
        writePrimaryFile(accountKey, gson.toJson(state));
    }

    /**
     * Saves {@code state} as the primary save. If it differs meaningfully from the previous save,
     * the previous save is rotated into the backups and config is flushed to disk.
     */
    public void save(String accountKey, PersistedState state)
    {
        String previousJson = readPrimaryJson(accountKey);
        PersistedState previousState = parse(previousJson, "previous save");

        String json = gson.toJson(state);
        PersistedState parsed = parse(json, "new save");
        if (parsed == null)
        {
            log.warn(UiText.get("log.save.invalid"));
            return;
        }

        boolean shouldFlushToDisk = previousState == null || isRecoveryRelevantStateChange(previousState, parsed);
        if (shouldFlushToDisk)
        {
            rotateBackups(accountKey, previousJson);
        }
        configManager.setConfiguration(CONFIG_GROUP, stateConfigKey(accountKey), json);
        writePrimaryFile(accountKey, json);
        if (shouldFlushToDisk)
        {
            flushConfig("state save");
        }
    }

    public PersistedState loadNewestValidBackup(String accountKey)
    {
        for (int i = 1; i <= STATE_BACKUP_COUNT; i++)
        {
            String backupJson = readFile(backupFile(accountKey, i));
            if (isBlank(backupJson))
            {
                backupJson = configManager.getConfiguration(CONFIG_GROUP, backupConfigKey(accountKey, i));
            }
            PersistedState backup = parse(backupJson, "backup " + i);
            if (backup != null)
            {
                log.warn("Recovered XtremeTasker state for account {} from backup {}", accountKey, i);
                return backup;
            }
        }
        return null;
    }

    /**
     * Finds the legacy (account-hash-only) save or backup that belongs to {@code characterName}
     * and has the most progress.
     */
    public PersistedState loadBestMatchingLegacyState(String scopedAccountKey, String characterName)
    {
        String legacyAccountKey = legacyAccountKeyFromScopedKey(scopedAccountKey);
        if (legacyAccountKey == null || characterName == null || legacyAccountKey.equals(scopedAccountKey))
        {
            return null;
        }

        PersistedState best = null;
        PersistedState primary = parse(
                configManager.getConfiguration(CONFIG_GROUP, stateConfigKey(legacyAccountKey)),
                "legacy primary save");
        if (isPersistedStateForCharacter(primary, characterName))
        {
            best = primary;
        }

        for (int i = 1; i <= STATE_BACKUP_COUNT; i++)
        {
            PersistedState backup = parse(
                    configManager.getConfiguration(CONFIG_GROUP, backupConfigKey(legacyAccountKey, i)),
                    "legacy backup " + i);
            if (!isPersistedStateForCharacter(backup, characterName))
            {
                continue;
            }

            if (best == null || isBetterProgressRecoveryCandidate(backup, best))
            {
                best = backup;
            }
        }

        if (best != null)
        {
            log.warn(UiText.format("log.legacy_import", characterName, legacyAccountKey, completedCount(best)));
        }
        return best;
    }

    public void preserveCorrupt(String accountKey, String json)
    {
        if (isBlank(json))
        {
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, corruptConfigKey(accountKey), json);
        writeFile(corruptFile(accountKey), json);
    }

    /** Parses and validates a save. Returns null for blank, malformed, or invalid JSON. */
    public PersistedState parse(String json, String source)
    {
        if (isBlank(json))
        {
            return null;
        }

        try
        {
            PersistedState state = gson.fromJson(json, PersistedState.class);
            if (state == null || !isValid(state))
            {
                log.warn("Invalid XtremeTasker persisted state from {}", source);
                return null;
            }
            return state;
        }
        catch (Exception e)
        {
            log.warn("Failed to parse XtremeTasker persisted state from {}", source, e);
            return null;
        }
    }

    // ---- Validation ----

    private static boolean isValid(PersistedState state)
    {
        return isValidIdCollection(state.getManualCompletedTaskIds())
                && isValidIdCollection(state.getSyncedCompletedTaskIds())
                && isValidIdCollection(state.getRetiredTaskIds())
                && isValidIdCollection(state.getKnownTaskIds())
                && isValidIdCollection(state.getSyncCompletionCandidateTaskIds())
                && isValidIdCollection(state.getSyncMismatchTaskIds())
                && isValidLongMap(state.getManualCompletionTimestamps())
                && isValidLongMap(state.getSyncedCompletionTimestamps())
                && isValidLongMap(state.getTaskTimeTicksById())
                && isValidLongMap(state.getCompletedTaskTimeTicksById())
                && isValidIdCollection(state.getCompletedBeforeRolledTaskIds())
                && isValidPositiveIntegerSet(state.getCollectionLogItemIds())
                && state.getCombatAchievementLastSyncSeenAtMillis() >= 0L
                && state.getCollectionLogLastSyncSeenAtMillis() >= 0L
                && (state.getCurrentTaskCollectionLogBaselineCount() == null
                || state.getCurrentTaskCollectionLogBaselineCount() >= 0)
                && state.getLastSeenPackVersion() >= 0
                && state.getLastKnownTaskCount() >= 0;
    }

    private static boolean isValidIdCollection(Collection<String> ids)
    {
        return ids == null || ids.stream().noneMatch(PersistedStateStore::isBlank);
    }

    private static boolean isValidPositiveIntegerSet(Set<Integer> ids)
    {
        return ids == null || ids.stream().allMatch(id -> id != null && id > 0);
    }

    private static boolean isValidLongMap(Map<String, Long> values)
    {
        return values == null || values.entrySet().stream()
                .allMatch(e -> !isBlank(e.getKey()) && e.getValue() != null && e.getValue() >= 0L);
    }

    // ---- Recovery heuristics ----

    /** True when the change between two saves is worth a backup rotation and a disk flush. */
    static boolean isRecoveryRelevantStateChange(PersistedState previous, PersistedState next)
    {
        return !Objects.equals(trimToNull(previous.getCurrentTaskId()), trimToNull(next.getCurrentTaskId()))
                || !Objects.equals(trimToNull(previous.getAccountKey()), trimToNull(next.getAccountKey()))
                || !Objects.equals(trimToNull(previous.getAccountDisplayName()), trimToNull(next.getAccountDisplayName()))
                || previous.getLastSeenPackVersion() != next.getLastSeenPackVersion()
                || previous.getLastKnownTaskCount() != next.getLastKnownTaskCount()
                || !normalizedSet(previous.getManualCompletedTaskIds()).equals(normalizedSet(next.getManualCompletedTaskIds()))
                || !normalizedSet(previous.getSyncedCompletedTaskIds()).equals(normalizedSet(next.getSyncedCompletedTaskIds()))
                || !normalizedSet(previous.getRetiredTaskIds()).equals(normalizedSet(next.getRetiredTaskIds()))
                || !normalizedSet(previous.getKnownTaskIds()).equals(normalizedSet(next.getKnownTaskIds()))
                || !normalizedList(previous.getSyncCompletionCandidateTaskIds()).equals(normalizedList(next.getSyncCompletionCandidateTaskIds()))
                || !normalizedList(previous.getSyncMismatchTaskIds()).equals(normalizedList(next.getSyncMismatchTaskIds()))
                || !Objects.equals(trimToNull(previous.getSyncMismatchTitle()), trimToNull(next.getSyncMismatchTitle()))
                || !normalizedLongMap(previous.getManualCompletionTimestamps()).equals(normalizedLongMap(next.getManualCompletionTimestamps()))
                || !normalizedLongMap(previous.getSyncedCompletionTimestamps()).equals(normalizedLongMap(next.getSyncedCompletionTimestamps()));
    }

    private static Set<String> normalizedSet(Set<String> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static List<String> normalizedList(List<String> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static Map<String, Long> normalizedLongMap(Map<String, Long> values)
    {
        if (values == null || values.isEmpty())
        {
            return Map.of();
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
    }

    private static int completedCount(PersistedState state)
    {
        Set<String> ids = new HashSet<>();
        if (state.getManualCompletedTaskIds() != null)
        {
            ids.addAll(state.getManualCompletedTaskIds());
        }
        if (state.getSyncedCompletedTaskIds() != null)
        {
            ids.addAll(state.getSyncedCompletedTaskIds());
        }
        if (state.getRetiredTaskIds() != null)
        {
            ids.addAll(state.getRetiredTaskIds());
        }
        return ids.size();
    }

    private static boolean isBetterProgressRecoveryCandidate(PersistedState candidate, PersistedState currentBest)
    {
        int candidateCompleted = completedCount(candidate);
        int bestCompleted = completedCount(currentBest);
        if (candidateCompleted != bestCompleted)
        {
            return candidateCompleted > bestCompleted;
        }
        return candidate.getSavedAtEpochMillis() > currentBest.getSavedAtEpochMillis();
    }

    private static boolean isPersistedStateForCharacter(PersistedState state, String characterName)
    {
        if (state == null)
        {
            return false;
        }

        String savedDisplayName = trimToNull(state.getAccountDisplayName());
        String currentDisplayName = trimToNull(characterName);
        return savedDisplayName != null
                && currentDisplayName != null
                && savedDisplayName.equalsIgnoreCase(currentDisplayName);
    }

    // ---- Storage ----

    private void rotateBackups(String accountKey, String currentJson)
    {
        if (isBlank(currentJson))
        {
            return;
        }

        if (parse(currentJson, "backup candidate") == null)
        {
            preserveCorrupt(accountKey, currentJson);
            return;
        }

        for (int i = STATE_BACKUP_COUNT; i >= 2; i--)
        {
            String previousBackup = configManager.getConfiguration(CONFIG_GROUP, backupConfigKey(accountKey, i - 1));
            String backupKey = backupConfigKey(accountKey, i);
            if (isBlank(previousBackup))
            {
                configManager.unsetConfiguration(CONFIG_GROUP, backupKey);
            }
            else
            {
                configManager.setConfiguration(CONFIG_GROUP, backupKey, previousBackup);
            }
        }
        configManager.setConfiguration(CONFIG_GROUP, backupConfigKey(accountKey, 1), currentJson);

        for (int i = STATE_BACKUP_COUNT; i >= 2; i--)
        {
            writeBackupFile(accountKey, i, readFile(backupFile(accountKey, i - 1)));
        }
        writeBackupFile(accountKey, 1, currentJson);
    }

    private void flushConfig(String reason)
    {
        try
        {
            configManager.sendConfig();
        }
        catch (Exception e)
        {
            log.warn("Failed to flush XtremeTasker config during {}", reason, e);
        }
    }

    private void writePrimaryFile(String accountKey, String json)
    {
        if (!isBlank(json))
        {
            writeFile(stateFile(accountKey), json);
        }
    }

    private void writeBackupFile(String accountKey, int index, String json)
    {
        if (!isBlank(json))
        {
            writeFile(backupFile(accountKey, index), json);
            return;
        }

        Path path = backupFile(accountKey, index);
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException e)
        {
            log.warn("Failed to delete XtremeTasker state file {}", path, e);
        }
    }

    private static String readFile(Path path)
    {
        if (!Files.isRegularFile(path))
        {
            return null;
        }

        try
        {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            log.warn("Failed to read XtremeTasker state file {}", path, e);
            return null;
        }
    }

    private static void writeFile(Path path, String json)
    {
        try
        {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            log.warn("Failed to write XtremeTasker state file {}", path, e);
        }
    }

    // ---- Keys and paths ----

    private static String stateConfigKey(String accountKey)
    {
        return STATE_KEY_PREFIX + accountKey;
    }

    private static String backupConfigKey(String accountKey, int index)
    {
        return stateConfigKey(accountKey) + "_backup_" + index;
    }

    private static String corruptConfigKey(String accountKey)
    {
        return stateConfigKey(accountKey) + STATE_CORRUPT_SUFFIX;
    }

    private static Path stateFile(String accountKey)
    {
        String safeAccountKey = trimToNull(accountKey);
        if (safeAccountKey == null)
        {
            safeAccountKey = "unknown";
        }
        safeAccountKey = safeAccountKey.replaceAll("[^A-Za-z0-9_-]", "_");
        return RuneLite.RUNELITE_DIR.toPath().resolve(STATE_FILE_DIR_NAME).resolve(safeAccountKey + STATE_FILE_SUFFIX);
    }

    private static Path backupFile(String accountKey, int index)
    {
        Path stateFile = stateFile(accountKey);
        return stateFile.resolveSibling(stateFile.getFileName() + ".backup_" + index);
    }

    private static Path corruptFile(String accountKey)
    {
        Path stateFile = stateFile(accountKey);
        return stateFile.resolveSibling(stateFile.getFileName() + STATE_CORRUPT_SUFFIX);
    }

    private static boolean isBlank(String s)
    {
        return s == null || s.trim().isEmpty();
    }

    private static String trimToNull(String s)
    {
        if (s == null)
        {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
