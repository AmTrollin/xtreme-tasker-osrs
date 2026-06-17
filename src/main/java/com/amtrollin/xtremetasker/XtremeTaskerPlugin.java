package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.TaskGroupProgress;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.tasklist.TaskGroupUtils;
import com.amtrollin.xtremetasker.ui.TaskHudOverlay;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.amtrollin.xtremetasker.ui.XtremeTaskerPanelOverlay;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.amtrollin.xtremetasker.verification.CombatAchievementService;
import com.amtrollin.xtremetasker.verification.PrerequisiteTrackerService;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@PluginDescriptor(
        name = "Xtreme Tasker",
        description = "Progressive random task generator using Combat Achievements and collection log entries, with completion tracking.",
        tags = {"tasks", "combat achievements", "collection log"}
)
public class XtremeTaskerPlugin extends Plugin implements TaskerService {
    private static final int ANCIENT_PAGE_FIRST_ITEM_ID = 11341;
    private static final int ANCIENT_PAGE_LAST_ITEM_ID = 11366;

        /**
         * Returns the roll skip notice if the current tier is exhausted for the active filter,
         * but before rolling into the next tier. Used to warn the user before rolling.
         */
        public String getPendingRollSkipNotice() {
            TaskTier currentTier = getCurrentTier();
            if (currentTier == null) return null;
    
            XtremeTaskerConfig.RollSourceFilter sourceFilter = config.rollSourceFilter();
            if (sourceFilter == XtremeTaskerConfig.RollSourceFilter.ALL) return null;
    
            List<XtremeTask> available = tasks.stream()
                    .filter(t -> t.getTier() == currentTier)
                    .filter(t -> !isTaskCompleted(t))
                    .filter(t -> {
                        if (sourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY) {
                            return t.getSource() == TaskSource.COMBAT_ACHIEVEMENT;
                        } else if (sourceFilter == XtremeTaskerConfig.RollSourceFilter.CLOG_ONLY) {
                            return isCollectionLogSyncSource(t.getSource());
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
    
            if (!available.isEmpty()) return null; // Still tasks left in this tier for this filter
    
            TaskTier nextTier = nextAvailableTierForSource(sourceFilter, currentTier);
            if (nextTier == null) return null;

            return buildSourceFilteredRollNotice(sourceFilter, currentTier, nextTier);
        }
    private static final String CONFIG_GROUP = "xtremetasker";
    private static final String SKIP_SINGLE_INCOMPLETE_CONFIRM_KEY = "skipSingleIncompleteConfirm";
    private static final String STATE_KEY_PREFIX = "state_";
    private static final int STATE_BACKUP_COUNT = 3;
    private static final int STATE_SCHEMA_VERSION = 1;
    private static final String STATE_CORRUPT_SUFFIX = "_corrupt";
    private static final String STATE_FILE_DIR_NAME = "xtreme-tasker-states";
    private static final String STATE_FILE_SUFFIX = ".json";
    private static final DateTimeFormatter SAVE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final List<TaskTier> PROGRESSION = List.of(
            TaskTier.EASY,
            TaskTier.MEDIUM,
            TaskTier.HARD,
            TaskTier.ELITE,
            TaskTier.MASTER
    );

    @Inject
    private Client client;
    @Inject
    private XtremeTaskerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private XtremeTaskerOverlay overlay;
    private XtremeTaskerPanelOverlay panelOverlay;
    @Inject
    private TaskHudOverlay taskHudOverlay;
    @Inject
    private MouseManager mouseManager;
    @Inject
    private ConfigManager configManager;
    @Inject
    private ClientThread clientThread;
    @Inject
    private KeyManager keyManager;
    @Inject
    private CollectionLogService collectionLogService;
    @Inject
    private CombatAchievementService combatAchievementService;
    @Inject
    private PrerequisiteTrackerService prerequisiteTrackerService;
    @Inject
    private ChatMessageManager chatMessageManager;
    @Inject
    private ItemManager itemManager;
    @Inject
    private Gson gson;
    private final Random random = new Random();

    private final Set<String> manualCompletedTaskIds = new HashSet<>();
    private final Set<String> syncedCompletedTaskIds = new HashSet<>();
    private final Set<String> retiredTaskIds = new HashSet<>();
    private final Map<String, Long> manualCompletionTimestamps = new HashMap<>();
    private final Map<String, Long> syncedCompletionTimestamps = new HashMap<>();
    private final Map<String, Long> taskTimeTicksById = new HashMap<>();
    private final Map<String, Long> completedTaskTimeTicksById = new HashMap<>();
    private final List<String> syncCompletionCandidateTaskIds = new ArrayList<>();
    private final List<String> syncMismatchTaskIds = new ArrayList<>();
    private List<XtremeTask> syncMismatchTasksCache = Collections.emptyList();
    private boolean syncMismatchTasksCacheValid = false;
    private int syncMismatchTasksCacheIdsHash = 0;
    private int syncMismatchTasksCacheManualHash = 0;
    private int syncMismatchTasksCacheSyncedHash = 0;
    private int syncMismatchTasksCacheTaskCount = 0;
    private int syncMismatchTasksCacheCapturedItemCount = -1;
    private final EnumMap<TaskSource, List<String>> lastSyncedTaskNames = new EnumMap<>(TaskSource.class);
    private String syncMismatchTitle = "";

    private final EnumMap<TaskTier, Integer> totalByTier = new EnumMap<>(TaskTier.class);
    private final EnumMap<TaskTier, Integer> doneByTier = new EnumMap<>(TaskTier.class);

    @Getter
    @Setter
    private XtremeTask currentTask;

    private String activeAccountKey = null;
    private String pendingAccountKey = null;
    private int pendingAccountKeyTicks = 0;
    private String loadedStateAccountKey = null;
    private long loadedStateAtMillis = 0L;
    private String currentTaskId = null;
    private String undoableCompletedTaskId = null;
    private int skippedTaskCount = 0;
    private String currentTaskCollectionLogBaselineSignature = null;
    private Integer currentTaskCollectionLogBaselineCount = null;

    private final List<XtremeTask> tasks = new ArrayList<>();
    private boolean taskPackLoaded = false;
    private int lastSeenPackVersion = 0;
    private int lastKnownTaskCount = 0;
    private final Set<String> knownTaskIds = new HashSet<>();
    /** Version of the last successfully loaded pack, independent of per-account state. */
    private int loadedPackVersion = 0;

    private boolean dirty = false;
    private int flushTickCounter = 0;
    private static final int FLUSH_EVERY_TICKS = 10; // ~6s (game tick ~0.6s)

    private final Map<String, Integer> caTaskIdsByName = new HashMap<>();
    private final Map<String, Integer> caTaskIdsByNormalizedName = new HashMap<>();

    private long rollAnimEndMs = 0L;
    private boolean inWorld = false;
    // Session-only: cleared on logout, never persisted
    private final Set<String> newTaskIds = new HashSet<>();
    // Set during rollRandomTask when a tier-skip occurs due to source filter exhaustion
    private String rollSkipNotice = null;
    private String lastSyncResult = null;
    private long lastSyncResultAtEpochMillis = 0L;
    private String lastSyncResultAtLocalTime = null;
    private String lastCombatAchievementSyncResult = null;
    private long lastCombatAchievementSyncResultAtEpochMillis = 0L;
    private String lastCombatAchievementSyncResultAtLocalTime = null;
    private String lastCollectionLogSyncResult = null;
    private long lastCollectionLogSyncResultAtEpochMillis = 0L;
    private String lastCollectionLogSyncResultAtLocalTime = null;
    private boolean combatAchievementSyncedTasksExpanded = false;
    private boolean collectionLogSyncedTasksExpanded = false;

    List<XtremeTask> tasksForTesting()
    {
        return tasks;
    }

    Set<String> manualCompletedTaskIdsForTesting()
    {
        return manualCompletedTaskIds;
    }

    Set<String> syncedCompletedTaskIdsForTesting()
    {
        return syncedCompletedTaskIds;
    }

    Map<String, Long> manualCompletionTimestampsForTesting()
    {
        return manualCompletionTimestamps;
    }

    List<String> syncMismatchTaskIdsForTesting()
    {
        return syncMismatchTaskIds;
    }

    String syncMismatchTitleForTesting()
    {
        return syncMismatchTitle;
    }

    void setCollectionLogServiceForTesting(CollectionLogService collectionLogService)
    {
        this.collectionLogService = collectionLogService;
        attachCollectionLogCacheListener();
    }

    void setSyncMismatchTitleForTesting(String syncMismatchTitle)
    {
        this.syncMismatchTitle = syncMismatchTitle;
    }


    @Override
    protected void startUp() {
        log.info("Xtreme Tasker started");

        attachCollectionLogCacheListener();
        collectionLogService.startUp();

        updateOverlayState();
        updateTaskHudState();
        rebuildTierCounts();

        keyManager.registerKeyListener(overlay.getKeyListener());
        mouseManager.registerMouseListener(overlay.getMouseAdapter());
        mouseManager.registerMouseWheelListener(overlay.getMouseWheelListener());

        clientThread.invokeLater(() -> {
            // If the plugin is (re)started while already logged in (e.g. RuneLite auto-updates
            // the plugin mid-session), onGameStateChanged(LOGGED_IN) will not fire again.
            // The account hash can be stale briefly while logging in, so account state is loaded
            // after the same hash has been observed on consecutive game ticks.
            String key = getAccountKey();
            if (key != null) {
                inWorld = true;
                pendingAccountKey = key;
                pendingAccountKeyTicks = 1;
            }
            reloadTaskPackInternal();
        });
    }

    @Override
    protected void shutDown() {
        log.info("Xtreme Tasker stopped");

        if (collectionLogService != null)
        {
            collectionLogService.setCacheChangeListener(null);
        }
        collectionLogService.shutDown();

        saveActiveState("plugin shutdown");

        overlayManager.remove(overlay);
        overlayManager.remove(panelOverlay);
        overlayManager.remove(taskHudOverlay);
        keyManager.unregisterKeyListener(overlay.getKeyListener());
        mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
        mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());

        currentTask = null;
        currentTaskId = null;
        undoableCompletedTaskId = null;
        currentTaskCollectionLogBaselineSignature = null;
        currentTaskCollectionLogBaselineCount = null;

        manualCompletedTaskIds.clear();
        syncedCompletedTaskIds.clear();
        retiredTaskIds.clear();
        manualCompletionTimestamps.clear();
        syncedCompletionTimestamps.clear();
        taskTimeTicksById.clear();
        completedTaskTimeTicksById.clear();

        activeAccountKey = null;
        pendingAccountKey = null;
        pendingAccountKeyTicks = 0;
        loadedStateAccountKey = null;
        loadedStateAtMillis = 0L;
        lastSeenPackVersion = 0;
        knownTaskIds.clear();
        loadedPackVersion = 0;

        tasks.clear();
        taskPackLoaded = false;

        rebuildTierCounts();
    }

    private void attachCollectionLogCacheListener()
    {
        if (collectionLogService != null)
        {
            collectionLogService.setCacheChangeListener(this::onCollectionLogCacheChanged);
        }
    }

    private void onCollectionLogCacheChanged()
    {
        syncMismatchTasksCacheValid = false;
        dirty = true;

        if (activeAccountKey != null)
        {
            persistIfPossible();
        }
    }

    private synchronized void saveActiveState(String reason) {
        try {
            if (client.getGameState() != GameState.LOGGED_IN) {
                log.debug("Skipped XtremeTasker state save during {} because the client is not logged in.", reason);
                return;
            }

            String key = activeAccountKey;
            if (key == null) {
                key = getAccountKey();
            }

            if (key != null) {
                saveStateForAccount(key);
                dirty = false;
                log.debug("Saved XtremeTasker state during {}", reason);
            }
        } catch (Exception e) {
            log.warn("Failed to save XtremeTasker state during {}", reason, e);
        }
    }

    private void updateOverlayState() {
        if (panelOverlay == null) {
            panelOverlay = new XtremeTaskerPanelOverlay(overlay);
        }
        overlayManager.add(overlay);
        overlayManager.add(panelOverlay);
    }

    private void updateTaskHudState() {
        if (config.showTaskHud()) {
            overlayManager.add(taskHudOverlay);
        } else {
            overlayManager.remove(taskHudOverlay);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

        updateOverlayState();
        updateTaskHudState();
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event) {
        reloadActiveCharacterStateAfterProfileChange("RuneLite profile change");
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        reloadActiveCharacterStateAfterProfileChange("RuneScape profile change");
    }

    private void reloadActiveCharacterStateAfterProfileChange(String reason) {
        clientThread.invokeLater(() -> {
            if (client.getGameState() != GameState.LOGGED_IN) {
                return;
            }

            if (activeAccountKey != null) {
                saveStateForAccount(activeAccountKey);
            }

            String key = getAccountKey();
            if (key == null) {
                beginAccountKeyStabilization(null);
                return;
            }

            activeAccountKey = key;
            loadStateForAccount(activeAccountKey);
            dirty = false;
            overlay.reloadIconPosition();
            maybeFireVersionNudge();
            rebuildTierCounts();
            log.debug("Reloaded XtremeTasker state after {}", reason);
        });
    }

    @Provides
    XtremeTaskerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(XtremeTaskerConfig.class);
    }

    // overlay still calls getDummyTasks()
    public List<XtremeTask> getDummyTasks() {
        return tasks;
    }

    public boolean hasTaskPackLoaded() {
        return taskPackLoaded && !tasks.isEmpty();
    }

    public boolean isTaskCompleted(XtremeTask task) {
        String id = task.getId();
        return manualCompletedTaskIds.contains(id) || syncedCompletedTaskIds.contains(id);
    }

    @Override
    public TaskGroupProgress getTaskGroupProgress(XtremeTask task)
    {
        List<XtremeTask> group = TaskGroupUtils.groupFor(tasks, task);
        int completed = 0;
        for (XtremeTask groupedTask : group)
        {
            if (isTaskCompleted(groupedTask))
            {
                completed++;
            }
        }
        return new TaskGroupProgress(completed, group.isEmpty() ? 1 : group.size());
    }

    @Override
    public List<XtremeTask> getTaskGroupInstances(XtremeTask task)
    {
        return TaskGroupUtils.groupFor(tasks, task);
    }

    /** Returns completion metadata for a task, or null if not completed. */
    public CompletionInfo getCompletionInfo(XtremeTask task)
    {
        if (task == null) return null;
        String id = task.getId();
        Long manualTs = manualCompletionTimestamps.get(id);
        if (manualTs != null)
        {
            return new CompletionInfo(manualTs, CompletionInfo.Source.MANUAL);
        }
        Long syncedTs = syncedCompletionTimestamps.get(id);
        if (syncedTs != null)
        {
            return new CompletionInfo(syncedTs, CompletionInfo.Source.SYNCED);
        }
        // Completed but no timestamp (migrated from old save)
        if (isTaskCompleted(task))
        {
            return new CompletionInfo(0L, manualCompletedTaskIds.contains(id)
                    ? CompletionInfo.Source.MANUAL : CompletionInfo.Source.SYNCED);
        }
        return null;
    }

    @Override
    public XtremeTask getMostRecentCompletedTask()
    {
        XtremeTask recentTask = null;
        long recentTimestamp = 0L;

        for (XtremeTask task : tasks)
        {
            CompletionInfo info = getCompletionInfo(task);
            if (info != null && info.timestamp > recentTimestamp)
            {
                recentTask = task;
                recentTimestamp = info.timestamp;
            }
        }

        return decorateCurrentSequenceTask(recentTask);
    }

    public boolean isOverlayEnabled() {
        return true;
    }

    public boolean isLoggedIn() {
        return inWorld;
    }

    public boolean isNewTask(XtremeTask task) {
        return task != null && task.getId() != null && newTaskIds.contains(task.getId());
    }

    @Override
    public boolean isTaskGroupNew(XtremeTask task)
    {
        for (XtremeTask groupedTask : TaskGroupUtils.groupFor(tasks, task))
        {
            if (isNewTask(groupedTask))
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasNewTasks() {
        return !newTaskIds.isEmpty();
    }

    public boolean isRolling() {
        return System.currentTimeMillis() < rollAnimEndMs;
    }

    // ---------- right-click menu on XT icon ----------

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        if (!isOverlayEnabled()) return;
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) return;
        java.awt.Rectangle bounds = overlay.getIconBounds();
        if (bounds.width <= 0 || !bounds.contains(mouse.getX(), mouse.getY())) return;

        client.createMenuEntry(-1)
                .setOption("Move to original position")
                .setTarget("<col=ff9040>XT Icon</col>")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> overlay.resetIconPosition());
    }

    // ---------- account persistence ----------

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        GameState gs = event.getGameState();

        if (gs == GameState.LOGGED_IN)
        {
            beginAccountKeyStabilization(getAccountKey());
        }


        if (gs == GameState.LOGGED_IN) {
            inWorld = true;
        }

        if (gs == GameState.LOGIN_SCREEN) {
            if (activeAccountKey != null) {
                saveStateForAccount(activeAccountKey);
                log.debug("Saved XtremeTasker state at login screen");
            }
            inWorld = false;
            activeAccountKey = null;
            pendingAccountKey = null;
            pendingAccountKeyTicks = 0;
            loadedStateAccountKey = null;
            loadedStateAtMillis = 0L;
            dirty = false;
            newTaskIds.clear();
            clearLoadedState();
            rebuildTierCounts();
        }

        if (gs == GameState.HOPPING) {
            if (activeAccountKey != null) {
                saveStateForAccount(activeAccountKey);
                log.debug("Saved XtremeTasker state before hopping");
            }
        }

    }

    private void beginAccountKeyStabilization(String key)
    {
        if (key == null)
        {
            pendingAccountKey = null;
            pendingAccountKeyTicks = 0;
            return;
        }

        if (key.equals(activeAccountKey))
        {
            pendingAccountKey = null;
            pendingAccountKeyTicks = 0;
            return;
        }

        if (key.equals(pendingAccountKey))
        {
            pendingAccountKeyTicks++;
        }
        else
        {
            pendingAccountKey = key;
            pendingAccountKeyTicks = 1;
        }
    }

    private void loadPendingAccountIfStable()
    {
        String key = getAccountKey();
        beginAccountKeyStabilization(key);

        if (pendingAccountKey == null || pendingAccountKeyTicks < 2)
        {
            return;
        }

        switchActiveAccount(pendingAccountKey);
        pendingAccountKey = null;
        pendingAccountKeyTicks = 0;
    }

    private void switchActiveAccount(String accountKey)
    {
        if (accountKey == null || accountKey.equals(activeAccountKey))
        {
            return;
        }

        if (activeAccountKey != null)
        {
            saveStateForAccount(activeAccountKey);
        }

        activeAccountKey = accountKey;
        loadStateForAccount(activeAccountKey);
        dirty = false;
        log.debug("Loaded XtremeTasker state for active account {}", activeAccountKey);

        // Re-read icon position config so each account's saved position is applied.
        overlay.reloadIconPosition();

        // Fire any pending version nudge now that we have the correct lastSeenPackVersion.
        maybeFireVersionNudge();
        detectAndAnnounceNewTasksForActiveAccount();

        loadCombatAchievementMappings();
    }

    private void detectAndAnnounceNewTasksForActiveAccount()
    {
        if (!taskPackLoaded || tasks.isEmpty())
        {
            return;
        }

        int previousKnownCount = lastKnownTaskCount;
        detectNewTaskIds(tasks, previousKnownCount);

        Set<String> validIds = tasks.stream()
                .map(XtremeTask::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        lastKnownTaskCount = tasks.size();
        knownTaskIds.clear();
        knownTaskIds.addAll(validIds);

        if (!newTaskIds.isEmpty())
        {
            int newTaskCount = newTaskIds.size();
            chat("[Xtreme Tasker] You have " + newTaskCount + " new task"
                    + (newTaskCount == 1 ? "" : "s") + "! Open the Tasks tab to see them.");
        }

        dirty = true;
        persistIfPossible();
    }

    private String getAccountKey() {
        String legacyKey = getLegacyAccountKey();
        String characterName = getCharacterNameForAccountKey();
        if (legacyKey == null || characterName == null) {
            return null;
        }

        return legacyKey + "_" + accountNameKey(characterName);
    }

    private String getLegacyAccountKey() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }

        long hash = client.getAccountHash();
        if (hash == -1L) {
            return null;
        }

        return Long.toUnsignedString(hash);
    }

    private String getCharacterNameForAccountKey() {
        try {
            if (client.getLocalPlayer() == null) {
                return null;
            }
            return safeTrim(client.getLocalPlayer().getName());
        } catch (Exception e) {
            return null;
        }
    }

    String accountNameKey(String characterName) {
        String normalized = safeTrim(characterName);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.toLowerCase(Locale.ROOT);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    String legacyAccountKeyFromScopedKey(String accountKey) {
        String trimmed = safeTrim(accountKey);
        if (trimmed == null) {
            return null;
        }

        int separator = trimmed.indexOf('_');
        return separator <= 0 ? trimmed : trimmed.substring(0, separator);
    }

    private String stateConfigKeyForAccount(String accountKey) {
        return STATE_KEY_PREFIX + accountKey;
    }

    private String stateBackupConfigKeyForAccount(String accountKey, int index) {
        return stateConfigKeyForAccount(accountKey) + "_backup_" + index;
    }

    private String stateCorruptConfigKeyForAccount(String accountKey) {
        return stateConfigKeyForAccount(accountKey) + STATE_CORRUPT_SUFFIX;
    }

    private Path stateFileForAccount(String accountKey) {
        String safeAccountKey = safeTrim(accountKey);
        if (safeAccountKey == null) {
            safeAccountKey = "unknown";
        }
        safeAccountKey = safeAccountKey.replaceAll("[^A-Za-z0-9_-]", "_");
        return Paths.get(System.getProperty("user.home"), ".runelite", STATE_FILE_DIR_NAME,
                safeAccountKey + STATE_FILE_SUFFIX);
    }

    private Path stateBackupFileForAccount(String accountKey, int index) {
        Path stateFile = stateFileForAccount(accountKey);
        return stateFile.resolveSibling(stateFile.getFileName() + ".backup_" + index);
    }

    private Path stateCorruptFileForAccount(String accountKey) {
        Path stateFile = stateFileForAccount(accountKey);
        return stateFile.resolveSibling(stateFile.getFileName() + STATE_CORRUPT_SUFFIX);
    }

    private String readStateFileForAccount(String accountKey) {
        Path path = stateFileForAccount(accountKey);
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read XtremeTasker state file {}", path, e);
            return null;
        }
    }

    private void writeStateFileForAccount(String accountKey, String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }

        Path path = stateFileForAccount(accountKey);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to write XtremeTasker state file {}", path, e);
        }
    }

    private String readStateBackupFileForAccount(String accountKey, int index) {
        Path path = stateBackupFileForAccount(accountKey, index);
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read XtremeTasker state backup file {}", path, e);
            return null;
        }
    }

    private void writeStateBackupFileForAccount(String accountKey, int index, String json) {
        Path path = stateBackupFileForAccount(accountKey, index);
        try {
            Files.createDirectories(path.getParent());
            if (json == null || json.trim().isEmpty()) {
                Files.deleteIfExists(path);
            } else {
                Files.writeString(path, json, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to write XtremeTasker state backup file {}", path, e);
        }
    }

    // ---- Icon position persistence (global, not per-account) ----
    private static final String ICON_X_KEY = "iconX";
    private static final String ICON_Y_KEY = "iconY";
    private static final String ICON_FIXED_X_KEY = "iconFixedX";
    private static final String ICON_FIXED_Y_KEY = "iconFixedY";
    private static final String ICON_RESIZABLE_X_KEY = "iconResizableX";
    private static final String ICON_RESIZABLE_Y_KEY = "iconResizableY";

    public void saveIconPosition(int x, int y) {
        configManager.setConfiguration(CONFIG_GROUP, ICON_X_KEY, String.valueOf(x));
        configManager.setConfiguration(CONFIG_GROUP, ICON_Y_KEY, String.valueOf(y));
    }

    public void saveIconPosition(int x, int y, boolean resized) {
        String xKey = resized ? ICON_RESIZABLE_X_KEY : ICON_FIXED_X_KEY;
        String yKey = resized ? ICON_RESIZABLE_Y_KEY : ICON_FIXED_Y_KEY;
        configManager.setConfiguration(CONFIG_GROUP, xKey, String.valueOf(x));
        configManager.setConfiguration(CONFIG_GROUP, yKey, String.valueOf(y));
        if (resized) {
            saveIconPosition(x, y);
        }
    }

    public void clearIconPosition() {
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_X_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_Y_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_FIXED_X_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_FIXED_Y_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_RESIZABLE_X_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_RESIZABLE_Y_KEY);
    }

    /** Returns {x, y} if a saved position exists, otherwise null. */
    public int[] loadIconPosition() {
        String sx = configManager.getConfiguration(CONFIG_GROUP, ICON_X_KEY);
        String sy = configManager.getConfiguration(CONFIG_GROUP, ICON_Y_KEY);
        if (sx == null || sy == null) return null;
        try {
            return new int[]{Integer.parseInt(sx.trim()), Integer.parseInt(sy.trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns {x, y} if a saved position exists for the current viewport mode, otherwise null. */
    public int[] loadIconPosition(boolean resized) {
        String xKey = resized ? ICON_RESIZABLE_X_KEY : ICON_FIXED_X_KEY;
        String yKey = resized ? ICON_RESIZABLE_Y_KEY : ICON_FIXED_Y_KEY;
        String sx = configManager.getConfiguration(CONFIG_GROUP, xKey);
        String sy = configManager.getConfiguration(CONFIG_GROUP, yKey);
        if (sx == null || sy == null) {
            return resized ? loadIconPosition() : null;
        }
        try {
            return new int[]{Integer.parseInt(sx.trim()), Integer.parseInt(sy.trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- Panel position persistence (global, not per-account) ----
    private static final String PANEL_X_KEY = "panelX";
    private static final String PANEL_Y_KEY = "panelY";

    public void savePanelPosition(int x, int y) {
        configManager.setConfiguration(CONFIG_GROUP, PANEL_X_KEY, String.valueOf(x));
        configManager.setConfiguration(CONFIG_GROUP, PANEL_Y_KEY, String.valueOf(y));
    }

    public void clearPanelPosition() {
        configManager.unsetConfiguration(CONFIG_GROUP, PANEL_X_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, PANEL_Y_KEY);
    }

    /** Returns {x, y} if a saved panel position exists, otherwise null. */
    public int[] loadPanelPosition() {
        String sx = configManager.getConfiguration(CONFIG_GROUP, PANEL_X_KEY);
        String sy = configManager.getConfiguration(CONFIG_GROUP, PANEL_Y_KEY);
        if (sx == null || sy == null) return null;
        try {
            return new int[]{Integer.parseInt(sx.trim()), Integer.parseInt(sy.trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveStateForAccount(String accountKey) {
        if (accountKey == null) {
            return;
        }

        if (!canSaveForAccount(accountKey)) {
            return;
        }

        log.trace("Saved state snapshot: currentTaskId={}, manualDone={}, syncedDone={}",
                currentTaskId, manualCompletedTaskIds.size(), syncedCompletedTaskIds.size());

        String key = stateConfigKeyForAccount(accountKey);
        String previousJson = readStateFileForAccount(accountKey);
        if (previousJson == null || previousJson.trim().isEmpty()) {
            previousJson = configManager.getConfiguration(CONFIG_GROUP, key);
        }
        PersistedState previousState = parseAndValidateState(previousJson, "previous save");

        PersistedState state = buildPersistedState();
        state.setAccountKey(accountKey);

        String json = gson.toJson(state);
        PersistedState parsed = parseAndValidateState(json, "new save");
        if (parsed == null) {
            log.warn("Refusing to save XtremeTasker state because the new snapshot failed validation.");
            return;
        }
        boolean shouldFlushToDisk = previousState == null || isRecoveryRelevantStateChange(previousState, parsed);
        if (shouldFlushToDisk) {
            rotateStateBackups(accountKey, previousJson);
        }
        configManager.setConfiguration(CONFIG_GROUP, key, json);
        writeStateFileForAccount(accountKey, json);
        if (shouldFlushToDisk) {
            flushConfigToDisk("state save");
        }
    }

    private void flushConfigToDisk(String reason) {
        try {
            configManager.sendConfig();
        } catch (Exception e) {
            log.warn("Failed to flush XtremeTasker config during {}", reason, e);
        }
    }

    private boolean canSaveForAccount(String accountKey) {
        if (loadedStateAccountKey == null)
        {
            log.warn("Refusing to save XtremeTasker state for account {} before account state has been loaded.", accountKey);
            return false;
        }

        if (!accountKey.equals(loadedStateAccountKey))
        {
            log.warn("Refusing to save XtremeTasker state for account {} because loaded state belongs to {}.",
                    accountKey, loadedStateAccountKey);
            return false;
        }

        if (activeAccountKey != null && !accountKey.equals(activeAccountKey))
        {
            log.warn("Refusing to save XtremeTasker state for account {} because active account is {}.",
                    accountKey, activeAccountKey);
            return false;
        }

        return true;
    }

    private void loadStateForAccount(String accountKey)
    {
        if (accountKey == null)
        {
            clearLoadedState();
            loadedStateAccountKey = null;
            loadedStateAtMillis = 0L;
            rebuildTierCounts();
            return;
        }

        String json = readStateFileForAccount(accountKey);
        if (json == null || json.trim().isEmpty())
        {
            json = configManager.getConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey));
        }
        if (json == null || json.trim().isEmpty())
        {
            PersistedState legacyState = loadBestMatchingLegacyState(accountKey);
            if (legacyState != null) {
                legacyState.setAccountKey(accountKey);
                String legacyJson = gson.toJson(legacyState);
                configManager.setConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey), legacyJson);
                writeStateFileForAccount(accountKey, legacyJson);
                flushConfigToDisk("legacy character save import");
                chat("[Xtreme Tasker] Progress save was imported for " + getAccountDisplayNameForMessage() + ".");
                applyPersistedState(legacyState);
                loadedStateAccountKey = accountKey;
                loadedStateAtMillis = System.currentTimeMillis();
                resolveCurrentTaskIfPossible();
                rebuildTierCounts();
                return;
            }

            PersistedState backup = loadNewestValidBackup(accountKey);
            if (backup != null) {
                String backupJson = gson.toJson(backup);
                configManager.setConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey), backupJson);
                writeStateFileForAccount(accountKey, backupJson);
                flushConfigToDisk("backup restore");
                chat("[Xtreme Tasker] Progress save was restored from a backup.");
                applyPersistedState(backup);
                loadedStateAccountKey = accountKey;
                loadedStateAtMillis = System.currentTimeMillis();
                resolveCurrentTaskIfPossible();
                rebuildTierCounts();
                return;
            }

            clearLoadedState();
            loadedStateAccountKey = accountKey;
            loadedStateAtMillis = System.currentTimeMillis();
            rebuildTierCounts();
            return;
        }

        PersistedState state = parseAndValidateState(json, "primary save");
        if (state == null) {
            preserveCorruptState(accountKey, json);
            state = loadNewestValidBackup(accountKey);

            if (state != null) {
                String repairedJson = gson.toJson(state);
                configManager.setConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey), repairedJson);
                writeStateFileForAccount(accountKey, repairedJson);
                flushConfigToDisk("backup repair");
                chat("[Xtreme Tasker] Progress save was repaired from a backup.");
            } else {
                log.warn("Failed to parse persisted XtremeTasker state and no valid backup was found. Leaving progress empty for account {}.", accountKey);
                chat("[Xtreme Tasker] Progress save could not be read, and no valid backup was found. The broken save was preserved.");
                clearLoadedState();
                loadedStateAccountKey = accountKey;
                loadedStateAtMillis = System.currentTimeMillis();
                rebuildTierCounts();
                return;
            }
        }

        writeStateFileForAccount(accountKey, gson.toJson(state));
        applyPersistedState(state);
        loadedStateAccountKey = accountKey;
        loadedStateAtMillis = System.currentTimeMillis();
        resolveCurrentTaskIfPossible();
        rebuildTierCounts();
    }

    private PersistedState buildPersistedState() {
        PersistedState state = new PersistedState();
        state.setSchemaVersion(STATE_SCHEMA_VERSION);
        long savedAt = System.currentTimeMillis();
        state.setSavedAtEpochMillis(savedAt);
        state.setSavedAtLocalTime(formatSaveTime(savedAt));
        state.setPluginVersion(getPluginVersionSafe());
        state.setAccountDisplayName(getAccountDisplayNameSafe());
        state.setManualCompletedTaskIds(new HashSet<>(manualCompletedTaskIds));
        state.setSyncedCompletedTaskIds(new HashSet<>(syncedCompletedTaskIds));
        state.setRetiredTaskIds(new HashSet<>(retiredTaskIds));
        state.setCurrentTaskId(currentTaskId);
        state.setUndoableCompletedTaskId(undoableCompletedTaskId);
        state.setSkippedTaskCount(skippedTaskCount);
        state.setCurrentTaskCollectionLogBaselineSignature(currentTaskCollectionLogBaselineSignature);
        state.setCurrentTaskCollectionLogBaselineCount(currentTaskCollectionLogBaselineCount);
        state.setLastSeenPackVersion(lastSeenPackVersion);
        state.setLastKnownTaskCount(lastKnownTaskCount);
        state.setLastSyncResult(lastSyncResult);
        state.setLastSyncResultAtEpochMillis(lastSyncResultAtEpochMillis);
        state.setLastSyncResultAtLocalTime(lastSyncResultAtLocalTime);
        state.setLastCombatAchievementSyncResult(lastCombatAchievementSyncResult);
        state.setLastCombatAchievementSyncResultAtEpochMillis(lastCombatAchievementSyncResultAtEpochMillis);
        state.setLastCombatAchievementSyncResultAtLocalTime(lastCombatAchievementSyncResultAtLocalTime);
        state.setLastCollectionLogSyncResult(lastCollectionLogSyncResult);
        state.setLastCollectionLogSyncResultAtEpochMillis(lastCollectionLogSyncResultAtEpochMillis);
        state.setLastCollectionLogSyncResultAtLocalTime(lastCollectionLogSyncResultAtLocalTime);
        state.setLastCombatAchievementSyncedTaskNames(getLastSyncedTaskNames(TaskSource.COMBAT_ACHIEVEMENT));
        state.setLastCollectionLogSyncedTaskNames(getLastSyncedTaskNames(TaskSource.COLLECTION_LOG));
        state.setCombatAchievementSyncedTasksExpanded(combatAchievementSyncedTasksExpanded);
        state.setCollectionLogSyncedTasksExpanded(collectionLogSyncedTasksExpanded);
        state.setSyncCompletionCandidateTaskIds(new ArrayList<>(syncCompletionCandidateTaskIds));
        state.setSyncMismatchTaskIds(new ArrayList<>(syncMismatchTaskIds));
        state.setSyncMismatchTitle(syncMismatchTitle);
        state.setKnownTaskIds(new HashSet<>(knownTaskIds));
        state.setManualCompletionTimestamps(new HashMap<>(manualCompletionTimestamps));
        state.setSyncedCompletionTimestamps(new HashMap<>(syncedCompletionTimestamps));
        state.setTaskTimeTicksById(new HashMap<>(taskTimeTicksById));
        state.setCompletedTaskTimeTicksById(new HashMap<>(completedTaskTimeTicksById));
        state.setCollectionLogItemIds(new HashSet<>(collectionLogService.getCachedItemIds()));
        state.setCollectionLogItemOrder(new HashMap<>(collectionLogService.getCachedItemOrder()));
        return state;
    }

    private String formatSaveTime(long epochMillis) {
        return SAVE_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private String getAccountDisplayNameSafe() {
        try {
            if (client.getLocalPlayer() != null && safeTrim(client.getLocalPlayer().getName()) != null) {
                return safeTrim(client.getLocalPlayer().getName());
            }
            return safeTrim(client.getLauncherDisplayName());
        } catch (Exception e) {
            return null;
        }
    }

    private PersistedState parseAndValidateState(String json, String source) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            PersistedState state = gson.fromJson(json, PersistedState.class);
            if (state == null || !isValidPersistedState(state)) {
                log.warn("Invalid XtremeTasker persisted state from {}", source);
                return null;
            }
            return state;
        } catch (Exception e) {
            log.warn("Failed to parse XtremeTasker persisted state from {}", source, e);
            return null;
        }
    }

    private boolean isValidPersistedState(PersistedState state) {
        return isValidIdSet(state.getManualCompletedTaskIds())
                && isValidIdSet(state.getSyncedCompletedTaskIds())
                && isValidIdSet(state.getRetiredTaskIds())
                && isValidIdSet(state.getKnownTaskIds())
                && isValidIdCollection(state.getSyncCompletionCandidateTaskIds())
                && isValidIdCollection(state.getSyncMismatchTaskIds())
                && isValidLongMap(state.getManualCompletionTimestamps())
                && isValidLongMap(state.getSyncedCompletionTimestamps())
                && isValidLongMap(state.getTaskTimeTicksById())
                && isValidLongMap(state.getCompletedTaskTimeTicksById())
                && isValidPositiveIntegerSet(state.getCollectionLogItemIds())
                && (state.getCurrentTaskCollectionLogBaselineCount() == null
                || state.getCurrentTaskCollectionLogBaselineCount() >= 0)
                && state.getSkippedTaskCount() >= 0
                && state.getLastSeenPackVersion() >= 0
                && state.getLastKnownTaskCount() >= 0;
    }

    private boolean isValidIdSet(Set<String> ids) {
        return isValidIdCollection(ids);
    }

    private boolean isValidPositiveIntegerSet(Set<Integer> ids)
    {
        if (ids == null)
        {
            return true;
        }

        for (Integer id : ids)
        {
            if (id == null || id <= 0)
            {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIdCollection(Collection<String> ids) {
        if (ids == null) {
            return true;
        }
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidLongMap(Map<String, Long> values) {
        if (values == null) {
            return true;
        }
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                return false;
            }
            Long value = entry.getValue();
            if (value == null || value < 0L) {
                return false;
            }
        }
        return true;
    }

    private int completedCount(PersistedState state) {
        return completedIdSet(state).size();
    }

    private Set<String> completedIdSet(PersistedState state) {
        Set<String> ids = new HashSet<>();
        if (state.getManualCompletedTaskIds() != null) {
            ids.addAll(state.getManualCompletedTaskIds());
        }
        if (state.getSyncedCompletedTaskIds() != null) {
            ids.addAll(state.getSyncedCompletedTaskIds());
        }
        if (state.getRetiredTaskIds() != null) {
            ids.addAll(state.getRetiredTaskIds());
        }
        return ids;
    }

    private boolean isRecoveryRelevantStateChange(PersistedState previous, PersistedState next) {
        return !Objects.equals(safeTrim(previous.getCurrentTaskId()), safeTrim(next.getCurrentTaskId()))
                || previous.getSkippedTaskCount() != next.getSkippedTaskCount()
                || !Objects.equals(safeTrim(previous.getAccountKey()), safeTrim(next.getAccountKey()))
                || !Objects.equals(safeTrim(previous.getAccountDisplayName()), safeTrim(next.getAccountDisplayName()))
                || previous.getLastSeenPackVersion() != next.getLastSeenPackVersion()
                || previous.getLastKnownTaskCount() != next.getLastKnownTaskCount()
                || !normalizedSet(previous.getManualCompletedTaskIds()).equals(normalizedSet(next.getManualCompletedTaskIds()))
                || !normalizedSet(previous.getSyncedCompletedTaskIds()).equals(normalizedSet(next.getSyncedCompletedTaskIds()))
                || !normalizedSet(previous.getRetiredTaskIds()).equals(normalizedSet(next.getRetiredTaskIds()))
                || !normalizedSet(previous.getKnownTaskIds()).equals(normalizedSet(next.getKnownTaskIds()))
                || !normalizedList(previous.getSyncCompletionCandidateTaskIds()).equals(normalizedList(next.getSyncCompletionCandidateTaskIds()))
                || !normalizedList(previous.getSyncMismatchTaskIds()).equals(normalizedList(next.getSyncMismatchTaskIds()))
                || !Objects.equals(safeTrim(previous.getSyncMismatchTitle()), safeTrim(next.getSyncMismatchTitle()))
                || !normalizedLongMap(previous.getManualCompletionTimestamps()).equals(normalizedLongMap(next.getManualCompletionTimestamps()))
                || !normalizedLongMap(previous.getSyncedCompletionTimestamps()).equals(normalizedLongMap(next.getSyncedCompletionTimestamps()));
    }

    private Set<String> normalizedSet(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<String, Long> normalizedLongMap(Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
    }

    private List<String> normalizedList(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void rotateStateBackups(String accountKey, String currentJson) {
        if (currentJson == null || currentJson.trim().isEmpty()) {
            return;
        }

        if (parseAndValidateState(currentJson, "backup candidate") == null) {
            preserveCorruptState(accountKey, currentJson);
            return;
        }

        for (int i = STATE_BACKUP_COUNT; i >= 2; i--) {
            String previousBackup = configManager.getConfiguration(CONFIG_GROUP, stateBackupConfigKeyForAccount(accountKey, i - 1));
            String backupKey = stateBackupConfigKeyForAccount(accountKey, i);
            if (previousBackup == null || previousBackup.trim().isEmpty()) {
                configManager.unsetConfiguration(CONFIG_GROUP, backupKey);
            } else {
                configManager.setConfiguration(CONFIG_GROUP, backupKey, previousBackup);
            }
        }

        configManager.setConfiguration(CONFIG_GROUP, stateBackupConfigKeyForAccount(accountKey, 1), currentJson);

        for (int i = STATE_BACKUP_COUNT; i >= 2; i--) {
            String previousBackup = readStateBackupFileForAccount(accountKey, i - 1);
            writeStateBackupFileForAccount(accountKey, i, previousBackup);
        }
        writeStateBackupFileForAccount(accountKey, 1, currentJson);
    }

    private PersistedState loadNewestValidBackup(String accountKey) {
        for (int i = 1; i <= STATE_BACKUP_COUNT; i++) {
            String backupJson = readStateBackupFileForAccount(accountKey, i);
            if (backupJson == null || backupJson.trim().isEmpty()) {
                backupJson = configManager.getConfiguration(CONFIG_GROUP, stateBackupConfigKeyForAccount(accountKey, i));
            }
            PersistedState backup = parseAndValidateState(backupJson, "backup " + i);
            if (backup != null) {
                log.warn("Recovered XtremeTasker state for account {} from backup {}", accountKey, i);
                return backup;
            }
        }
        return null;
    }

    private PersistedState loadBestMatchingLegacyState(String scopedAccountKey) {
        String legacyAccountKey = legacyAccountKeyFromScopedKey(scopedAccountKey);
        String characterName = getCharacterNameForAccountKey();
        if (legacyAccountKey == null || characterName == null || legacyAccountKey.equals(scopedAccountKey)) {
            return null;
        }

        PersistedState best = null;
        PersistedState primary = parseAndValidateState(
                configManager.getConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(legacyAccountKey)),
                "legacy primary save"
        );
        if (isPersistedStateForCharacter(primary, characterName)) {
            best = primary;
        }

        for (int i = 1; i <= STATE_BACKUP_COUNT; i++) {
            PersistedState backup = parseAndValidateState(
                    configManager.getConfiguration(CONFIG_GROUP, stateBackupConfigKeyForAccount(legacyAccountKey, i)),
                    "legacy backup " + i
            );
            if (!isPersistedStateForCharacter(backup, characterName)) {
                continue;
            }

            if (best == null || isBetterProgressRecoveryCandidate(backup, best)) {
                best = backup;
            }
        }

        for (PersistedState profileState : loadMatchingLegacyStatesFromProfileFiles(legacyAccountKey, characterName)) {
            if (best == null || isBetterProgressRecoveryCandidate(profileState, best)) {
                best = profileState;
            }
        }

        if (best != null) {
            log.warn("Imported legacy XtremeTasker state for character {} from shared account hash {} (completed={})",
                    characterName, legacyAccountKey, completedCount(best));
        }
        return best;
    }

    private List<PersistedState> loadMatchingLegacyStatesFromProfileFiles(String legacyAccountKey, String characterName) {
        Path runeliteDir = Paths.get(System.getProperty("user.home"), ".runelite");
        if (!Files.isDirectory(runeliteDir)) {
            return Collections.emptyList();
        }

        List<PersistedState> states = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(runeliteDir, 4)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".properties"))
                    .forEach(path -> loadMatchingLegacyStatesFromProfileFile(path, legacyAccountKey, characterName, states));
        } catch (IOException e) {
            log.warn("Failed to scan RuneLite profile files for XtremeTasker legacy saves.", e);
        }
        return states;
    }

    private void loadMatchingLegacyStatesFromProfileFile(
            Path path,
            String legacyAccountKey,
            String characterName,
            List<PersistedState> states)
    {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception e) {
            log.debug("Skipped RuneLite profile file {} while scanning XtremeTasker legacy saves.", path, e);
            return;
        }

        loadMatchingLegacyStateFromProperties(properties, legacyAccountKey, characterName,
                stateConfigKeyForAccount(legacyAccountKey), "legacy profile primary save", states);
        for (int i = 1; i <= STATE_BACKUP_COUNT; i++) {
            loadMatchingLegacyStateFromProperties(properties, legacyAccountKey, characterName,
                    stateBackupConfigKeyForAccount(legacyAccountKey, i), "legacy profile backup " + i, states);
        }
    }

    private void loadMatchingLegacyStateFromProperties(
            Properties properties,
            String legacyAccountKey,
            String characterName,
            String stateKey,
            String source,
            List<PersistedState> states)
    {
        String json = properties.getProperty(CONFIG_GROUP + "." + stateKey);
        PersistedState state = parseAndValidateState(json, source);
        if (isPersistedStateForCharacter(state, characterName)
                && legacyAccountKey.equals(safeTrim(state.getAccountKey()))) {
            states.add(state);
        }
    }

    private boolean isPersistedStateForCharacter(PersistedState state, String characterName) {
        if (state == null || characterName == null) {
            return false;
        }

        String savedDisplayName = safeTrim(state.getAccountDisplayName());
        String currentDisplayName = safeTrim(characterName);
        return savedDisplayName != null
                && currentDisplayName != null
                && savedDisplayName.equalsIgnoreCase(currentDisplayName);
    }

    private String getAccountDisplayNameForMessage() {
        String name = getCharacterNameForAccountKey();
        return name == null ? "this character" : name;
    }

    private boolean isBetterProgressRecoveryCandidate(PersistedState candidate, PersistedState currentBest) {
        int candidateCompleted = completedCount(candidate);
        int bestCompleted = completedCount(currentBest);
        if (candidateCompleted != bestCompleted) {
            return candidateCompleted > bestCompleted;
        }
        return candidate.getSavedAtEpochMillis() > currentBest.getSavedAtEpochMillis();
    }

    private void preserveCorruptState(String accountKey, String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, stateCorruptConfigKeyForAccount(accountKey), json);
        Path path = stateCorruptFileForAccount(accountKey);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to preserve corrupt XtremeTasker state file {}", path, e);
        }
    }

    private void applyPersistedState(PersistedState state) {
        clearLoadedState();

        if (state.getManualCompletedTaskIds() != null) {
            manualCompletedTaskIds.addAll(state.getManualCompletedTaskIds());
        }
        if (state.getSyncedCompletedTaskIds() != null) {
            syncedCompletedTaskIds.addAll(state.getSyncedCompletedTaskIds());
        }
        if (state.getRetiredTaskIds() != null) {
            retiredTaskIds.addAll(state.getRetiredTaskIds());
        }
        if (state.getManualCompletionTimestamps() != null) {
            manualCompletionTimestamps.putAll(state.getManualCompletionTimestamps());
        }
        if (state.getSyncedCompletionTimestamps() != null) {
            syncedCompletionTimestamps.putAll(state.getSyncedCompletionTimestamps());
        }
        if (state.getTaskTimeTicksById() != null) {
            taskTimeTicksById.putAll(state.getTaskTimeTicksById());
        }
        if (state.getCompletedTaskTimeTicksById() != null) {
            completedTaskTimeTicksById.putAll(state.getCompletedTaskTimeTicksById());
        }
        collectionLogService.restoreCachedItemState(state.getCollectionLogItemIds(), state.getCollectionLogItemOrder());

        currentTaskId = safeTrim(state.getCurrentTaskId());
        undoableCompletedTaskId = safeTrim(state.getUndoableCompletedTaskId());
        skippedTaskCount = Math.max(0, state.getSkippedTaskCount());
        currentTaskCollectionLogBaselineSignature = safeTrim(state.getCurrentTaskCollectionLogBaselineSignature());
        currentTaskCollectionLogBaselineCount = state.getCurrentTaskCollectionLogBaselineCount();
        lastSeenPackVersion = state.getLastSeenPackVersion();
        lastKnownTaskCount = state.getLastKnownTaskCount();
        lastSyncResult = safeTrim(state.getLastSyncResult());
        lastSyncResultAtEpochMillis = state.getLastSyncResultAtEpochMillis();
        lastSyncResultAtLocalTime = safeTrim(state.getLastSyncResultAtLocalTime());
        if (lastSyncResultAtLocalTime == null && lastSyncResultAtEpochMillis > 0L)
        {
            lastSyncResultAtLocalTime = formatSaveTime(lastSyncResultAtEpochMillis);
        }
        lastCombatAchievementSyncResult = safeTrim(state.getLastCombatAchievementSyncResult());
        lastCombatAchievementSyncResultAtEpochMillis = state.getLastCombatAchievementSyncResultAtEpochMillis();
        lastCombatAchievementSyncResultAtLocalTime = safeTrim(state.getLastCombatAchievementSyncResultAtLocalTime());
        if (lastCombatAchievementSyncResultAtLocalTime == null && lastCombatAchievementSyncResultAtEpochMillis > 0L)
        {
            lastCombatAchievementSyncResultAtLocalTime = formatSaveTime(lastCombatAchievementSyncResultAtEpochMillis);
        }
        lastCollectionLogSyncResult = safeTrim(state.getLastCollectionLogSyncResult());
        lastCollectionLogSyncResultAtEpochMillis = state.getLastCollectionLogSyncResultAtEpochMillis();
        lastCollectionLogSyncResultAtLocalTime = safeTrim(state.getLastCollectionLogSyncResultAtLocalTime());
        if (lastCollectionLogSyncResultAtLocalTime == null && lastCollectionLogSyncResultAtEpochMillis > 0L)
        {
            lastCollectionLogSyncResultAtLocalTime = formatSaveTime(lastCollectionLogSyncResultAtEpochMillis);
        }
        setLastSyncedTaskNames(TaskSource.COMBAT_ACHIEVEMENT, state.getLastCombatAchievementSyncedTaskNames());
        setLastSyncedTaskNames(TaskSource.COLLECTION_LOG, state.getLastCollectionLogSyncedTaskNames());
        combatAchievementSyncedTasksExpanded = state.isCombatAchievementSyncedTasksExpanded();
        collectionLogSyncedTasksExpanded = state.isCollectionLogSyncedTasksExpanded();
        migrateLegacyLastSyncResultIfNeeded();
        if (state.getSyncCompletionCandidateTaskIds() != null)
        {
            syncCompletionCandidateTaskIds.addAll(state.getSyncCompletionCandidateTaskIds());
        }
        if (state.getSyncMismatchTaskIds() != null)
        {
            syncMismatchTaskIds.addAll(state.getSyncMismatchTaskIds());
        }
        syncMismatchTitle = safeTrim(state.getSyncMismatchTitle());
        if (!syncMismatchTaskIds.isEmpty() && syncMismatchTitle == null)
        {
            syncMismatchTitle = "Review completed tasks";
        }
        if (state.getKnownTaskIds() != null) {
            knownTaskIds.addAll(state.getKnownTaskIds());
        }
    }

    private void clearLoadedState() {
        manualCompletedTaskIds.clear();
        syncedCompletedTaskIds.clear();
        retiredTaskIds.clear();
        manualCompletionTimestamps.clear();
        syncedCompletionTimestamps.clear();
        taskTimeTicksById.clear();
        completedTaskTimeTicksById.clear();
        currentTask = null;
        currentTaskId = null;
        undoableCompletedTaskId = null;
        skippedTaskCount = 0;
        currentTaskCollectionLogBaselineSignature = null;
        currentTaskCollectionLogBaselineCount = null;
        loadedStateAccountKey = null;
        loadedStateAtMillis = 0L;
        lastSeenPackVersion = 0;
        lastKnownTaskCount = 0;
        lastSyncResult = null;
        lastSyncResultAtEpochMillis = 0L;
        lastSyncResultAtLocalTime = null;
        lastCombatAchievementSyncResult = null;
        lastCombatAchievementSyncResultAtEpochMillis = 0L;
        lastCombatAchievementSyncResultAtLocalTime = null;
        lastCollectionLogSyncResult = null;
        lastCollectionLogSyncResultAtEpochMillis = 0L;
        lastCollectionLogSyncResultAtLocalTime = null;
        combatAchievementSyncedTasksExpanded = false;
        collectionLogSyncedTasksExpanded = false;
        knownTaskIds.clear();
        lastSyncedTaskNames.clear();
        collectionLogService.resetCachedItemIds();
        syncCompletionCandidateTaskIds.clear();
        clearSyncMismatchReviewState();
    }

    public List<String> getLastSyncedTaskNames(TaskSource source)
    {
        if (source == null)
        {
            return Collections.emptyList();
        }

        List<String> names = lastSyncedTaskNames.get(source);
        if (names == null || names.isEmpty())
        {
            return Collections.emptyList();
        }
        return new ArrayList<>(names);
    }

    private void setLastSyncedTaskNames(TaskSource source, List<String> newlySyncedTaskIds)
    {
        if (source == null)
        {
            return;
        }

        if (newlySyncedTaskIds == null || newlySyncedTaskIds.isEmpty())
        {
            lastSyncedTaskNames.put(source, Collections.emptyList());
            return;
        }

        Map<String, XtremeTask> byId = tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));

        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : newlySyncedTaskIds)
        {
            if (id == null || !seen.add(id))
            {
                continue;
            }

            XtremeTask task = byId.get(id);
            if (task != null && task.getName() != null)
            {
                names.add(sequenceDisplayName(task));
            }
            else
            {
                names.add(id);
            }
        }

        lastSyncedTaskNames.put(source, names);
    }

    @Override
    public List<String> getLastCombatAchievementSyncedTaskNames()
    {
        return getLastSyncedTaskNames(TaskSource.COMBAT_ACHIEVEMENT);
    }

    @Override
    public List<String> getLastCollectionLogSyncedTaskNames()
    {
        return getLastSyncedTaskNames(TaskSource.COLLECTION_LOG);
    }

    @Override
    public boolean isCombatAchievementSyncedTasksExpanded()
    {
        return combatAchievementSyncedTasksExpanded;
    }

    @Override
    public boolean isCollectionLogSyncedTasksExpanded()
    {
        return collectionLogSyncedTasksExpanded;
    }

    public void setCombatAchievementSyncedTasksExpanded(boolean expanded)
    {
        combatAchievementSyncedTasksExpanded = expanded;
    }

    public void setCollectionLogSyncedTasksExpanded(boolean expanded)
    {
        collectionLogSyncedTasksExpanded = expanded;
    }

    private void migrateLegacyLastSyncResultIfNeeded()
    {
        if (lastSyncResult == null || lastSyncResult.trim().isEmpty())
        {
            return;
        }

        if (lastSyncResult.startsWith("Combat Achievement") && lastCombatAchievementSyncResult == null)
        {
            lastCombatAchievementSyncResult = lastSyncResult;
            lastCombatAchievementSyncResultAtEpochMillis = lastSyncResultAtEpochMillis;
            lastCombatAchievementSyncResultAtLocalTime = lastSyncResultAtLocalTime;
        }
        else if (lastSyncResult.startsWith("Collection Log") && lastCollectionLogSyncResult == null)
        {
            lastCollectionLogSyncResult = lastSyncResult;
            lastCollectionLogSyncResultAtEpochMillis = lastSyncResultAtEpochMillis;
            lastCollectionLogSyncResultAtLocalTime = lastSyncResultAtLocalTime;
        }
    }

    private String getPluginVersionSafe() {
        Package pkg = getClass().getPackage();
        return pkg == null ? null : pkg.getImplementationVersion();
    }


    private void resolveCurrentTaskIfPossible()
    {
        if (currentTaskId == null || tasks.isEmpty())
        {
            return;
        }

        String id = currentTaskId;
        currentTask = tasks.stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .map(this::decorateCurrentSequenceTask)
                .orElse(null);

        // If we can't resolve it (pack changed), don't keep saving a dead ID forever
        if (currentTask == null)
        {
            currentTaskId = null;
            clearCurrentTaskCollectionLogBaseline();
        }

        XtremeTask undoableTask = findTaskById(undoableCompletedTaskId);
        if (undoableTask == null || !isTaskCompleted(undoableTask))
        {
            undoableCompletedTaskId = null;
        }
    }

    private void retireUnknownCompletedTaskIds(Set<String> validIds) {
        Set<String> restored = retiredTaskIds.stream()
                .filter(validIds::contains)
                .collect(Collectors.toSet());
        if (!restored.isEmpty()) {
            manualCompletedTaskIds.addAll(restored);
            retiredTaskIds.removeAll(restored);
            log.info("Restored {} retired XtremeTasker task id(s) that are present in the current task pack.", restored.size());
        }

        int before = retiredTaskIds.size();
        retireUnknownIdsFrom(manualCompletedTaskIds, validIds);
        retireUnknownIdsFrom(syncedCompletedTaskIds, validIds);
        int retiredNow = retiredTaskIds.size() - before;
        if (retiredNow > 0) {
            log.info("Retired {} XtremeTasker completed task id(s) no longer present in the current task pack.", retiredNow);
        }
    }

    private void retireUnknownIdsFrom(Set<String> completedIds, Set<String> validIds) {
        Set<String> unknownIds = completedIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !validIds.contains(id))
                .collect(Collectors.toSet());
        if (unknownIds.isEmpty()) {
            return;
        }

        retiredTaskIds.addAll(unknownIds);
        completedIds.removeAll(unknownIds);
        for (String id : unknownIds)
        {
            completedTaskTimeTicksById.remove(id);
        }
        if (unknownIds.contains(undoableCompletedTaskId))
        {
            undoableCompletedTaskId = null;
        }
    }

    private void detectNewTaskIds(List<XtremeTask> loadedTasks, int previousKnownCount) {
        newTaskIds.clear();

        if (!knownTaskIds.isEmpty()) {
            for (XtremeTask task : loadedTasks) {
                String id = task.getId();
                if (id != null && !knownTaskIds.contains(id)) {
                    newTaskIds.add(id);
                }
            }
            return;
        }

        // Migration fallback for users saved before knownTaskIds existed.
        if (previousKnownCount > 0 && loadedTasks.size() > previousKnownCount) {
            for (int i = previousKnownCount; i < loadedTasks.size(); i++) {
                String id = loadedTasks.get(i).getId();
                if (id != null) {
                    newTaskIds.add(id);
                }
            }
        }
    }


    // ---------- tier counts / progress ----------

    private void rebuildTierCounts() {
        totalByTier.clear();
        doneByTier.clear();

        for (TaskTier tier : TaskTier.values()) {
            totalByTier.put(tier, 0);
            doneByTier.put(tier, 0);
        }

        for (XtremeTask t : tasks) {
            TaskTier tier = t.getTier();
            totalByTier.put(tier, totalByTier.getOrDefault(tier, 0) + 1);
        }

        for (XtremeTask t : tasks) {
            if (isTaskCompleted(t)) {
                TaskTier tier = t.getTier();
                doneByTier.put(tier, doneByTier.getOrDefault(tier, 0) + 1);
            }
        }
    }

    public String getItemName(int itemId) {
        ItemComposition item = itemManager.getItemComposition(itemId);
        if (item == null || item.getName() == null || item.getName().trim().isEmpty())
        {
            return "Item " + itemId;
        }
        return item.getName();
    }

    public boolean isCollectionLogItemObtained(int itemId) {
        return collectionLogService != null && collectionLogService.isItemObtained(itemId);
    }

    public int getPendingAncientPageDropCountSinceLastSync() {
        return collectionLogService == null ? 0 : collectionLogService.getPendingAncientPageDropCountSinceLastSync();
    }

    public int countObtainedCollectionLogItems(int[] itemIds) {
        return collectionLogService == null ? 0 : Math.toIntExact(collectionLogService.countObtained(itemIds));
    }

    public Integer getCurrentTaskCollectionLogBaselineCount(String requirementSignature)
    {
        String signature = safeTrim(requirementSignature);
        if (signature == null
                || currentTaskCollectionLogBaselineSignature == null
                || !signature.equals(currentTaskCollectionLogBaselineSignature))
        {
            return null;
        }
        return currentTaskCollectionLogBaselineCount;
    }

    public boolean isCurrentTaskCompletionCriteriaMet()
    {
        XtremeTask task = getCurrentTask();
        if (task == null || isTaskCompleted(task))
        {
            return false;
        }

        if (!areKnownPrerequisitesSatisfied(task))
        {
            return false;
        }

        TaskVerification verification = task.getVerification();
        if (verification == null)
        {
            return false;
        }

        if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG)
        {
            return isCurrentCollectionLogRequirementSatisfied(task, verification);
        }

        if (verification.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY)
        {
            return prerequisiteTrackerService.isDiaryComplete(verification.getRegion(), verification.getDifficulty());
        }

        if (verification.getType() == TaskVerification.VerificationType.SKILL
                && verification.getExperience() != null
                && verification.getCount() != null)
        {
            return prerequisiteTrackerService.countSkillsAt99(verification.getExperience().keySet()) >= verification.getCount();
        }

        if (verification.getType() == TaskVerification.VerificationType.COMBAT_ACHIEVEMENT)
        {
            Integer taskId = resolveCombatAchievementTaskId(task);
            return taskId != null && combatAchievementService.isTaskComplete(taskId);
        }

        return false;
    }

    private boolean areKnownPrerequisitesSatisfied(XtremeTask task)
    {
        List<PrerequisiteStatus> statuses = getPrerequisiteStatuses(task);
        if (statuses == null || statuses.isEmpty())
        {
            return true;
        }

        for (PrerequisiteStatus status : statuses)
        {
            if (status != null && !status.isCompleted())
            {
                return false;
            }
        }
        return true;
    }

    private boolean isCurrentCollectionLogRequirementSatisfied(XtremeTask task, TaskVerification verification)
    {
        ItemRequirement requirement = resolveCollectionLogRequirement(task);
        if (requirement == null)
        {
            return false;
        }

        int totalObtainedCount = countObtainedCollectionLogItems(requirement.itemIds);
        List<XtremeTask> group = countedCollectionLogGroupFor(task, verification);
        if (group.size() <= 1)
        {
            return totalObtainedCount >= requirement.requiredCount;
        }

        int currentIndex = requirementIndex(task, group);
        if (currentIndex < 0)
        {
            return totalObtainedCount >= requirement.requiredCount;
        }

        List<Integer> thresholds = countedGroupThresholds(group);
        int previousThreshold = currentIndex <= 0 ? 0 : thresholdAt(thresholds, currentIndex - 1);
        int currentThreshold = Math.max(thresholdAt(thresholds, currentIndex), previousThreshold);
        int currentRequired = Math.max(1, currentThreshold - previousThreshold);

        int completedCount = 0;
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && isTaskCompleted(groupedTask))
            {
                completedCount++;
            }
        }

        int completedThreshold = thresholdAt(thresholds, completedCount - 1);
        Integer baselineCount = getCurrentTaskCollectionLogBaselineCount(collectionLogRequirementSignature(requirement.itemIds));
        int baseline = baselineCount != null
                ? Math.min(Math.max(0, baselineCount), Math.max(0, completedThreshold))
                : Math.max(previousThreshold, completedThreshold);
        return Math.max(0, totalObtainedCount - baseline) >= currentRequired;
    }

    private static int requirementIndex(XtremeTask task, List<XtremeTask> group)
    {
        if (task == null || task.getId() == null || group == null)
        {
            return -1;
        }

        for (int i = 0; i < group.size(); i++)
        {
            XtremeTask groupedTask = group.get(i);
            if (groupedTask != null && task.getId().equals(groupedTask.getId()))
            {
                return i;
            }
        }
        return -1;
    }

    private static int thresholdAt(List<Integer> thresholds, int index)
    {
        if (thresholds == null || thresholds.isEmpty() || index < 0)
        {
            return 0;
        }
        return Math.max(0, thresholds.get(Math.min(index, thresholds.size() - 1)));
    }

    public long getCollectionLogItemObtainedOrder(int itemId) {
        return collectionLogService == null ? Long.MAX_VALUE : collectionLogService.getObtainedItemOrder(itemId);
    }

    public java.awt.image.BufferedImage getItemImage(int itemId) {
        return itemManager.getImage(itemId);
    }

    public int getTierTotal(TaskTier tier) {
        return totalByTier.getOrDefault(tier, 0);
    }

    public int getTierDone(TaskTier tier) {
        return doneByTier.getOrDefault(tier, 0);
    }

    public int getTierPercent(TaskTier tier) {
        int total = getTierTotal(tier);
        if (total <= 0) return 0;

        int done = getTierDone(tier);
        return (int) ((done * 100L) / total); // integer division = floor
    }


    public String getTierProgressLabel(TaskTier tier) {
        int total = getTierTotal(tier);
        int done = getTierDone(tier);

        int pct = (total <= 0)
                ? 0
                : (int) ((done * 100L) / total); // integer division = floor

        return done + "/" + total + " (" + pct + "%)";
    }


    public TaskTier getCurrentTier() {
        for (TaskTier tier : PROGRESSION) {
            boolean hasIncomplete = tasks.stream().anyMatch(t -> t.getTier() == tier && !isTaskCompleted(t));
            if (hasIncomplete) return tier;
        }
        return null;
    }

    private XtremeTask findTaskById(String id)
    {
        if (id == null || id.trim().isEmpty())
        {
            return null;
        }

        return tasks.stream()
                .filter(task -> id.equals(task.getId()))
                .findFirst()
                .orElse(null);
    }

    public Long getTaskTimeTicks(XtremeTask task)
    {
        if (task == null) return null;
        String id = task.getId();
        Long liveTicks = taskTimeTicksById.get(id);
        if (id != null && id.equals(currentTaskId))
        {
            return liveTicks;
        }

        Long completedTicks = completedTaskTimeTicksById.get(id);
        return completedTicks != null ? completedTicks : liveTicks;
    }

    // ---------- core actions ----------

    public XtremeTask rollRandomTask() {
        return rollRandomTaskExcluding(null);
    }

    private XtremeTask rollRandomTaskExcluding(String excludedTaskId) {
        rollSkipNotice = null;
        TaskTier currentTier = getCurrentTier();
        if (currentTier == null) return null;

        XtremeTaskerConfig.RollSourceFilter sourceFilter = config.rollSourceFilter();

        List<XtremeTask> available = tasks.stream()
                .filter(t -> t.getTier() == currentTier)
                .filter(t -> !isTaskCompleted(t))
                .filter(t -> matchesRollSourceFilter(t, sourceFilter))
                .filter(t -> excludedTaskId == null || !excludedTaskId.equals(t.getId()))
                .collect(Collectors.toList());

        if (available.isEmpty() && excludedTaskId != null)
        {
            available = tasks.stream()
                    .filter(t -> t.getTier() == currentTier)
                    .filter(t -> !isTaskCompleted(t))
                    .filter(t -> matchesRollSourceFilter(t, sourceFilter))
                    .collect(Collectors.toList());
        }

        if (!available.isEmpty()) {
            return normalizeRolledTask(available.get(random.nextInt(available.size())));
        }

        // Current tier's filtered source is exhausted — find next tier with available tasks
        if (sourceFilter != XtremeTaskerConfig.RollSourceFilter.ALL) {
            for (int i = PROGRESSION.indexOf(currentTier) + 1; i < PROGRESSION.size(); i++) {
                TaskTier nextTier = PROGRESSION.get(i);
                List<XtremeTask> nextAvailable = tasks.stream()
                        .filter(t -> t.getTier() == nextTier)
                        .filter(t -> !isTaskCompleted(t))
                        .filter(t -> matchesRollSourceFilter(t, sourceFilter))
                        .collect(Collectors.toList());

                if (!nextAvailable.isEmpty()) {
                    rollSkipNotice = buildSourceFilteredRollNotice(sourceFilter, currentTier, nextTier);
                    return normalizeRolledTask(nextAvailable.get(random.nextInt(nextAvailable.size())));
                }
            }
        }

        return null;
    }

    private XtremeTask normalizeRolledTask(XtremeTask task)
    {
        if (task == null)
        {
            return null;
        }

        TaskVerification verification = task.getVerification();
        if (!isCountedCollectionLogSync(verification)
                || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG)
        {
            return task;
        }

        List<XtremeTask> group = countedCollectionLogGroupFor(task, verification);
        if (group.isEmpty())
        {
            return task;
        }

        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && !isTaskCompleted(groupedTask))
            {
                return groupedTask;
            }
        }

        return task;
    }

    private XtremeTask decorateCurrentSequenceTask(XtremeTask task)
    {
        SequenceStep step = sequenceStepForCurrentDisplay(task);
        if (step == null)
        {
            return task;
        }

        String name = sequenceTaskDisplayName(task, step);
        String wikiItemName = sequenceWikiItemName(task.getName(), step.label, step.itemName);
        String wikiUrl = wikiItemName == null || wikiItemName.trim().isEmpty()
                ? task.getWikiUrl()
                : "https://oldschool.runescape.wiki/w/" + wikiItemName.trim().replace(' ', '_');

        return new XtremeTask(
                task.getId(),
                name,
                task.getSource(),
                task.getTier(),
                step.itemId > 0 ? step.itemId : task.getIconItemId(),
                task.getIconKey(),
                task.getDescription(),
                step.prereqs == null || step.prereqs.isEmpty() ? task.getPrereqs() : step.prereqs,
                wikiUrl,
                task.getVerification(),
                task.getTip()
        );
    }

    private static String sequenceTaskDisplayName(XtremeTask task, SequenceStep step)
    {
        if (step == null)
        {
            return task == null ? "" : task.getName();
        }

        if (isMtaWandSequenceTask(task, step))
        {
            switch (step.itemId)
            {
                case 6908:
                    return "Obtain Beginner MTA wand";
                case 6910:
                    return "Upgrade to Apprentice MTA wand";
                case 6912:
                    return "Upgrade to Teacher MTA wand";
                case 6914:
                    return "Upgrade to Master MTA wand";
                default:
                    break;
            }
        }

        if (step.itemId == 4119 && task.getName() != null && task.getName().toLowerCase(Locale.ROOT).contains("metal boots"))
        {
            return task.getName().replace("next tier", "first tier") + " (" + step.label + ")";
        }

        return task.getName() + " (" + step.label + ")";
    }

    private String sequenceDisplayName(XtremeTask task)
    {
        if (task == null)
        {
            return "";
        }

        XtremeTask decorated = decorateCurrentSequenceTask(task);
        if (decorated == null || decorated.getName() == null)
        {
            return task.getName();
        }
        return decorated.getName();
    }

    private SequenceStep sequenceStepForCurrentDisplay(XtremeTask task)
    {
        if (task == null || task.getName() == null || !isDisplaySequenceTask(task.getName()))
        {
            return null;
        }

        TaskVerification verification = task.getVerification();
        if (verification == null
                || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG
                || verification.getCount() == null
                || verification.getCount() <= 0)
        {
            return null;
        }

        int[] itemIds = verification.getItemIds();
        int index = verification.getCount() - 1;
        if (itemIds == null || index < 0 || index >= itemIds.length)
        {
            return null;
        }

        SequenceItemMeta meta = SEQUENCE_ITEM_META.get(itemIds[index]);
        if (meta != null)
        {
            return new SequenceStep(meta.label, meta.prereqs, itemIds[index], meta.wikiItemName);
        }

        List<SequencePrereqLine> prereqLines = sequencePrereqLines(task.getPrereqs());
        if (index >= prereqLines.size())
        {
            return null;
        }

        SequencePrereqLine prereqLine = prereqLines.get(index);
        String label = prereqLine.label;
        if (label == null || label.isEmpty())
        {
            return null;
        }

        return new SequenceStep(label, prereqLine.prereqs, itemIds[index], "");
    }

    private static List<SequencePrereqLine> sequencePrereqLines(String prereqs)
    {
        if (prereqs == null || prereqs.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        List<SequencePrereqLine> out = new ArrayList<>();
        String formatted = prereqs.replace("\r", "").replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
        for (String line : formatted.split("\n"))
        {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }

            int colon = trimmed.indexOf(':');
            if (colon <= 0)
            {
                continue;
            }

            String label = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (!label.isEmpty())
            {
                out.add(new SequencePrereqLine(label, value));
            }
        }
        return out;
    }

    private static boolean isDisplaySequenceTask(String name)
    {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.contains("next tier")
                || normalized.contains("next reward")
                || isMtaWandSequenceTaskName(normalized);
    }

    private static boolean isMtaWandSequenceTask(XtremeTask task, SequenceStep step)
    {
        return step != null
                && (step.itemId == 6908 || step.itemId == 6910 || step.itemId == 6912 || step.itemId == 6914)
                && isMtaWandSequenceTaskName(task == null ? null : task.getName());
    }

    private static boolean isMtaWandSequenceTaskName(String name)
    {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.contains("mta wand")
                || normalized.contains("magic training arena wand")
                || normalized.equals("upgrade to apprentice mta wand")
                || normalized.equals("upgrade to teacher mta wand")
                || normalized.equals("upgrade to master mta wand");
    }

    private static String sequenceWikiItemName(String taskName, String label, String itemName)
    {
        String cleanItemName = itemName == null ? "" : itemName.trim();
        if (!cleanItemName.isEmpty() && !cleanItemName.toLowerCase(Locale.ROOT).startsWith("item "))
        {
            return cleanItemName;
        }

        String cleanLabel = label == null ? "" : label.trim();
        if (cleanLabel.isEmpty())
        {
            return cleanItemName;
        }

        String lowerTaskName = taskName == null ? "" : taskName.toLowerCase(Locale.ROOT);
        if (lowerTaskName.contains("boots") && !cleanLabel.toLowerCase(Locale.ROOT).endsWith("boots"))
        {
            return cleanLabel + " boots";
        }

        return cleanLabel;
    }

    private static final Map<Integer, SequenceItemMeta> SEQUENCE_ITEM_META = Map.ofEntries(
            Map.entry(4119, new SequenceItemMeta("Bronze", "10 Slayer", "Bronze boots")),
            Map.entry(4121, new SequenceItemMeta("Iron", "17 Slayer (Cave slimes) or 25 Slayer (Cockatrices)", "Iron boots")),
            Map.entry(4123, new SequenceItemMeta("Steel", "30 Slayer (Pyrefiends) or 33 Slayer and 33 Firemaking (Harpie bug swarms)", "Steel boots")),
            Map.entry(4125, new SequenceItemMeta("Black", "50 Slayer", "Black boots")),
            Map.entry(4127, new SequenceItemMeta("Mithril", "52 Slayer", "Mithril boots")),
            Map.entry(4129, new SequenceItemMeta("Adamant", "75 Slayer (Gargoyles) or Giant frogs", "Adamant boots")),
            Map.entry(4131, new SequenceItemMeta("Rune", "80 Slayer", "Rune boots")),
            Map.entry(6908, new SequenceItemMeta("Beginner wand", "", "Beginner wand")),
            Map.entry(6910, new SequenceItemMeta("Apprentice wand", "", "Apprentice wand")),
            Map.entry(6912, new SequenceItemMeta("Teacher wand", "", "Teacher wand")),
            Map.entry(6914, new SequenceItemMeta("Master wand", "", "Master wand")),
            Map.entry(31734, new SequenceItemMeta("Ralph's fabric roll", "30 Sailing; 14 Construction", "Ralph%27s fabric roll")),
            Map.entry(31733, new SequenceItemMeta("Barrel stand", "30 Sailing; 14 Construction", "Barrel stand")),
            Map.entry(31732, new SequenceItemMeta("Stormy key", "30 Sailing; 14 Construction", "Stormy key")),
            Map.entry(31746, new SequenceItemMeta("Gurtob's fabric roll", "55 Sailing; 47 Construction", "Gurtob%27s fabric roll")),
            Map.entry(31745, new SequenceItemMeta("Captured wind mote", "55 Sailing; 47 Construction", "Captured wind mote")),
            Map.entry(31744, new SequenceItemMeta("Fetid key", "55 Sailing; 47 Construction", "Fetid key")),
            Map.entry(31758, new SequenceItemMeta("Gwyna's fabric roll", "72 Sailing; 70 Construction", "Gwyna%27s fabric roll")),
            Map.entry(31757, new SequenceItemMeta("Heart of Ithell", "72 Sailing; 70 Construction", "Heart of ithell")),
            Map.entry(31756, new SequenceItemMeta("Serrated key", "72 Sailing; 70 Construction", "Serrated key"))
    );

    private static final class SequenceItemMeta
    {
        private final String label;
        private final String prereqs;
        private final String wikiItemName;

        private SequenceItemMeta(String label, String prereqs, String wikiItemName)
        {
            this.label = label == null ? "" : label;
            this.prereqs = prereqs == null ? "" : prereqs;
            this.wikiItemName = wikiItemName == null ? "" : wikiItemName;
        }
    }

    private static final class SequencePrereqLine
    {
        private final String label;
        private final String prereqs;

        private SequencePrereqLine(String label, String prereqs)
        {
            this.label = label == null ? "" : label;
            this.prereqs = prereqs == null ? "" : prereqs;
        }
    }

    private static final class SequenceStep
    {
        private final String label;
        private final String prereqs;
        private final int itemId;
        private final String itemName;

        private SequenceStep(String label, String prereqs, int itemId, String itemName)
        {
            this.label = label == null ? "" : label;
            this.prereqs = prereqs == null ? "" : prereqs;
            this.itemId = itemId;
            this.itemName = itemName == null ? "" : itemName;
        }
    }

    private static String prettyTier(TaskTier t) {
        if (t == null) return "?";
        String s = t.name();
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private TaskTier nextAvailableTierForSource(XtremeTaskerConfig.RollSourceFilter sourceFilter, TaskTier currentTier) {
        int currentIndex = PROGRESSION.indexOf(currentTier);
        if (currentIndex < 0) return null;

        for (int i = currentIndex + 1; i < PROGRESSION.size(); i++) {
            TaskTier tier = PROGRESSION.get(i);
            boolean hasAvailable = tasks.stream()
                    .anyMatch(t -> t.getTier() == tier
                            && !isTaskCompleted(t)
                            && matchesRollSourceFilter(t, sourceFilter));
            if (hasAvailable) {
                return tier;
            }
        }
        return null;
    }

    private boolean matchesRollSourceFilter(XtremeTask task, XtremeTaskerConfig.RollSourceFilter sourceFilter) {
        if (task == null) return false;
        if (sourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY) {
            return task.getSource() == TaskSource.COMBAT_ACHIEVEMENT;
        }
        if (sourceFilter == XtremeTaskerConfig.RollSourceFilter.CLOG_ONLY) {
            return isCollectionLogSyncSource(task.getSource());
        }
        return true;
    }

    private static boolean isCollectionLogSyncSource(TaskSource source)
    {
        return source == TaskSource.COLLECTION_LOG || source == TaskSource.DIARY_ACHIEVEMENT;
    }

    private String buildSourceFilteredRollNotice(
            XtremeTaskerConfig.RollSourceFilter sourceFilter,
            TaskTier currentTier,
            TaskTier rollTier
    ) {
        String sourceLabel = sourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY ? "CAs" : "CLOGs/ADs";
        String otherLabel = sourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY ? "CLOGs/ADs" : "CAs";
        String currentTierLabel = prettyTier(currentTier);
        String rollTierLabel = prettyTier(rollTier);

        boolean skippedMultipleTiers = PROGRESSION.indexOf(rollTier) - PROGRESSION.indexOf(currentTier) > 1;
        if (skippedMultipleTiers) {
            return "All " + currentTierLabel + " " + sourceLabel + " are complete. "
                    + "Your next " + sourceLabel + " task is " + rollTierLabel + ". "
                    + otherLabel + " are still keeping overall progress in " + currentTierLabel + ".";
        }

        return "All " + currentTierLabel + " " + sourceLabel + " are complete. "
                + "Your next " + sourceLabel + " task is " + rollTierLabel + ". "
                + "\nEnable " + otherLabel + " to officially finish the " + currentTierLabel + " tier.";
    }

    public void rollRandomTaskAndPersist()
    {
        if (!hasTaskPackLoaded())
        {
            chat("No tasks loaded. Load tasks in Rules tab");
            return;
        }

        XtremeTask cur = getCurrentTask();
        if (cur != null && !isTaskCompleted(cur))
        {
            return;
        }

        if (undoableCompletedTaskId != null)
        {
            freezeCompletedTaskTime(undoableCompletedTaskId);
            undoableCompletedTaskId = null;
        }

        XtremeTask newTask = rollRandomTask();
        currentTask = decorateCurrentSequenceTask(newTask);
        rollAnimEndMs = System.currentTimeMillis() + com.amtrollin.xtremetasker.ui.style.UiConstants.ROLL_ANIM_MS;

        currentTaskId = (currentTask != null) ? currentTask.getId() : null;
        captureCurrentTaskCollectionLogBaseline();
        dirty = true;
        persistIfPossible(); // writes immediately if activeAccountKey != null
    }

    @Override
    public void skipCurrentTaskAndPersist()
    {
        if (!config.enableTaskSkipping())
        {
            return;
        }

        if (!hasTaskPackLoaded())
        {
            chat("No tasks loaded. Load tasks in Rules tab");
            return;
        }

        XtremeTask cur = getCurrentTask();
        if (cur == null)
        {
            return;
        }

        if (undoableCompletedTaskId != null)
        {
            freezeCompletedTaskTime(undoableCompletedTaskId);
            undoableCompletedTaskId = null;
        }

        String skippedId = cur.getId();
        skippedTaskCount++;

        XtremeTask newTask = rollRandomTaskExcluding(skippedId);
        currentTask = decorateCurrentSequenceTask(newTask);
        rollAnimEndMs = System.currentTimeMillis() + com.amtrollin.xtremetasker.ui.style.UiConstants.ROLL_ANIM_MS;

        currentTaskId = (currentTask != null) ? currentTask.getId() : null;
        captureCurrentTaskCollectionLogBaseline();
        dirty = true;
        persistIfPossible();
    }

    public void completeCurrentTaskAndPersist()
    {
        XtremeTask cur = getCurrentTask();
        if (cur == null) return;

        String id = cur.getId();
        manualCompletedTaskIds.add(id);
        manualCompletionTimestamps.put(id, System.currentTimeMillis());
        snapshotCompletedTaskTime(id);
        // If previously synced, remove synced timestamp so manual takes precedence
        syncedCompletionTimestamps.remove(id);
        clientThread.invokeLater(() -> client.playSoundEffect(3925)); // collection log new-entry ding

        undoableCompletedTaskId = id;
        currentTask = null;
        currentTaskId = null;

        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    public boolean canUndoRecentTaskCompletion()
    {
        if (undoableCompletedTaskId == null || undoableCompletedTaskId.trim().isEmpty())
        {
            return false;
        }

        XtremeTask task = findTaskById(undoableCompletedTaskId);
        return task != null && isTaskCompleted(task);
    }

    public void undoCurrentTaskCompletionAndPersist()
    {
        if (!canUndoRecentTaskCompletion())
        {
            return;
        }

        String id = undoableCompletedTaskId;
        XtremeTask task = findTaskById(id);
        if (task == null)
        {
            undoableCompletedTaskId = null;
            return;
        }

        manualCompletedTaskIds.remove(id);
        syncedCompletedTaskIds.remove(id);
        manualCompletionTimestamps.remove(id);
        syncedCompletionTimestamps.remove(id);
        completedTaskTimeTicksById.remove(id);
        currentTask = decorateCurrentSequenceTask(task);
        currentTaskId = id;
        undoableCompletedTaskId = null;

        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    public void toggleTaskCompletedAndPersist(XtremeTask task)
    {
        String id = task.getId();
        if (id == null || id.trim().isEmpty())
        {
            log.warn("Refusing to toggle completion for task with null/blank id: {}", task.getName());
            return;
        }

        boolean wasComplete = isTaskCompleted(task);
        if (wasComplete)
        {
            // Remove from both sets so the task becomes truly incomplete.
            // (syncedCompletedTaskIds alone would keep it stuck as complete.)
            markTaskIncomplete(id);
        }
        else
        {
            manualCompletedTaskIds.add(id);
            manualCompletionTimestamps.put(id, System.currentTimeMillis());
            snapshotCompletedTaskTime(id);
            syncedCompletionTimestamps.remove(id);
        }

        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    @Override
    public void toggleTaskGroupProgressAndPersist(XtremeTask task)
    {
        TaskGroupProgress progress = getTaskGroupProgress(task);
        if (progress.getTotal() <= 1)
        {
            toggleTaskCompletedAndPersist(task);
            return;
        }

        int nextCompleted = progress.isComplete() ? 0 : progress.getCompleted() + 1;
        setTaskGroupCompletedCountAndPersist(task, nextCompleted);
    }

    @Override
    public void setTaskGroupCompletedCountAndPersist(XtremeTask task, int completedCount)
    {
        List<XtremeTask> group = TaskGroupUtils.groupFor(tasks, task);
        if (group.isEmpty())
        {
            return;
        }

        int desired = Math.max(0, Math.min(group.size(), completedCount));
        int existingCompleted = getTaskGroupProgress(task).getCompleted();
        int remainingToAdd = desired - existingCompleted;
        int remainingToRemove = existingCompleted - desired;
        long now = System.currentTimeMillis();

        if (remainingToAdd > 0)
        {
            for (XtremeTask groupedTask : group)
            {
                if (remainingToAdd <= 0)
                {
                    break;
                }

                String id = groupedTask.getId();
                if (id == null || id.trim().isEmpty() || isTaskCompleted(groupedTask))
                {
                    continue;
                }

                manualCompletedTaskIds.add(id);
                manualCompletionTimestamps.putIfAbsent(id, now);
                snapshotCompletedTaskTime(id);
                syncedCompletionTimestamps.remove(id);
                remainingToAdd--;
            }
        }
        else if (remainingToRemove > 0)
        {
            for (int i = group.size() - 1; i >= 0; i--)
            {
                if (remainingToRemove <= 0)
                {
                    break;
                }

                XtremeTask groupedTask = group.get(i);
                String id = groupedTask.getId();
                if (id == null || id.trim().isEmpty() || !isTaskCompleted(groupedTask))
                {
                    continue;
                }

                markTaskIncomplete(id);
                remainingToRemove--;
            }
        }

        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    private void markTaskIncomplete(String id)
    {
        manualCompletedTaskIds.remove(id);
        syncedCompletedTaskIds.remove(id);
        manualCompletionTimestamps.remove(id);
        syncedCompletionTimestamps.remove(id);
        taskTimeTicksById.remove(id);
        completedTaskTimeTicksById.remove(id);
        if (id != null && id.equals(undoableCompletedTaskId))
        {
            undoableCompletedTaskId = null;
        }
    }

    private void snapshotCompletedTaskTime(String id)
    {
        if (id == null || id.trim().isEmpty())
        {
            return;
        }

        completedTaskTimeTicksById.put(id, Math.max(0L, taskTimeTicksById.getOrDefault(id, 0L)));
    }

    private void freezeCompletedTaskTime(String id)
    {
        if (id == null || id.trim().isEmpty())
        {
            return;
        }

        Long completedTicks = completedTaskTimeTicksById.get(id);
        if (completedTicks != null)
        {
            taskTimeTicksById.put(id, completedTicks);
        }
    }

    private void captureCurrentTaskCollectionLogBaseline()
    {
        clearCurrentTaskCollectionLogBaseline();

        TaskVerification verification = currentTask == null ? null : currentTask.getVerification();
        if (currentTask == null
                || currentTask.getSource() != TaskSource.COLLECTION_LOG
                || verification == null
                || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG
                || verification.getItemIds() == null
                || verification.getItemIds().length == 0)
        {
            return;
        }

        currentTaskCollectionLogBaselineSignature = collectionLogRequirementSignature(verification.getItemIds());
        currentTaskCollectionLogBaselineCount = countObtainedCollectionLogItems(verification.getItemIds());
    }

    private void clearCurrentTaskCollectionLogBaseline()
    {
        currentTaskCollectionLogBaselineSignature = null;
        currentTaskCollectionLogBaselineCount = null;
    }

    private static String collectionLogRequirementSignature(int[] itemIds)
    {
        if (itemIds == null || itemIds.length == 0)
        {
            return null;
        }

        String signature = Arrays.stream(itemIds)
                .filter(itemId -> itemId > 0)
                .distinct()
                .sorted()
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        return signature.isEmpty() ? null : signature;
    }

    @Subscribe
    public void onGameTick(net.runelite.api.events.GameTick tick)
    {
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            loadPendingAccountIfStable();
        }

        if (activeAccountKey == null)
        {
            return;
        }

        // Accumulate in-game ticks for the current task, or for the just-completed
        // undoable task while it is waiting in the empty Current state.
        String timerTaskId = currentTaskId != null ? currentTaskId : undoableCompletedTaskId;
        if (timerTaskId != null && client.getGameState() == GameState.LOGGED_IN)
        {
            taskTimeTicksById.merge(timerTaskId, 1L, Long::sum);
            dirty = true;
        }

        if (!dirty)
        {
            flushTickCounter = 0;
            return;
        }

        flushTickCounter++;
        if (flushTickCounter >= FLUSH_EVERY_TICKS)
        {
            flushTickCounter = 0;
            saveStateForAccount(activeAccountKey);
            dirty = false;
            log.debug("Flushed XtremeTasker state");
        }
    }

    private void persistIfPossible()
    {
        if (!dirty || activeAccountKey == null)
        {
            return;
        }

        // Run the write on the client thread and block until it executes.
        clientThread.invoke(() ->
        {
            saveStateForAccount(activeAccountKey);
            dirty = false;
        });
    }

    // ---------- JSON task pack loading ----------

    public void reloadTaskPack() {
        clientThread.invokeLater(this::reloadTaskPackInternal);
    }

    @Override
    public void syncCombatAchievementsAndPersist()
    {
        if (!hasTaskPackLoaded())
        {
            setSyncResultAndChat(TaskSource.COMBAT_ACHIEVEMENT, "No tasks loaded. Load tasks first.");
            return;
        }

        setLastSyncedTaskNames(TaskSource.COMBAT_ACHIEVEMENT, null);
        clientThread.invokeLater(() -> {
            // Re-load mappings if empty (e.g. struct cache was cold at login).
            if (caTaskIdsByName.isEmpty())
            {
                loadCombatAchievementMappings();
            }

            int completionCandidates = 0;
            List<String> completionCandidateTaskIds = new ArrayList<>();

            for (XtremeTask task : tasks)
            {
                if (task.getSource() != TaskSource.COMBAT_ACHIEVEMENT)
                {
                    continue;
                }

                if (isTaskCompleted(task))
                {
                    continue;
                }

                Integer taskId = resolveCombatAchievementTaskId(task);
                if (taskId == null)
                {
                    continue;
                }

                if (combatAchievementService.isTaskComplete(taskId))
                {
                    String id = task.getId();
                    if (id != null && !id.trim().isEmpty())
                    {
                        completionCandidateTaskIds.add(id);
                        completionCandidates++;
                    }
                }
            }

            setLastSyncedTaskNames(TaskSource.COMBAT_ACHIEVEMENT, completionCandidateTaskIds);
            setSyncCompletionCandidatesForSource(TaskSource.COMBAT_ACHIEVEMENT, completionCandidateTaskIds);

            setSyncMismatchTasksForSource(TaskSource.COMBAT_ACHIEVEMENT,
                    findCombatAchievementSyncMismatches());

            if (completionCandidates > 0) {
                setSyncResultAndChat(TaskSource.COMBAT_ACHIEVEMENT, "Combat Achievement sync done! " + completionCandidates + " completed task(s) found. No tasks were marked complete automatically."
                        + syncMismatchResultSuffix(TaskSource.COMBAT_ACHIEVEMENT));
            } else {
                setSyncResultAndChat(TaskSource.COMBAT_ACHIEVEMENT, "Combat Achievement sync done! No new completions found."
                        + syncMismatchResultSuffix(TaskSource.COMBAT_ACHIEVEMENT));
            }

            rebuildTierCounts();
            dirty = true;
            if (activeAccountKey != null)
            {
                saveStateForAccount(activeAccountKey);
                dirty = false;
            }
        });
    }

    private List<XtremeTask> findCombatAchievementSyncMismatches()
    {
        List<XtremeTask> mismatches = new ArrayList<>();
        for (XtremeTask task : tasks)
        {
            if (task.getSource() != TaskSource.COMBAT_ACHIEVEMENT || !isTaskCompleted(task))
            {
                continue;
            }

            Integer taskId = resolveCombatAchievementTaskId(task);
            if (taskId == null)
            {
                mismatches.add(task);
                continue;
            }

            if (!combatAchievementService.isTaskComplete(taskId))
            {
                mismatches.add(task);
            }
        }
        return mismatches;
    }

    int findCollectionLogCompletionCandidatesFromCache(List<String> completionCandidateTaskIds)
    {
        int completionCandidates = 0;
        Set<String> processedCountedGroups = new HashSet<>();

        for (XtremeTask task : tasks)
        {
            if (!isCollectionLogSyncSource(task.getSource()))
            {
                continue;
            }

            if (isTaskCompleted(task))
            {
                continue;
            }

            TaskVerification verification = task.getVerification();
            if (verification == null)
            {
                continue;
            }

            if (isCountedCollectionLogSync(verification))
            {
                String groupKey = countedCollectionLogGroupKey(task, verification);
                if (groupKey == null)
                {
                    continue;
                }

                if (!processedCountedGroups.add(groupKey))
                {
                    continue;
                }

                List<XtremeTask> group = countedCollectionLogGroupFor(task, verification);
                int observedCount = observedCollectionLogSyncCount(task, verification);
                if (observedCount < 0)
                {
                    continue;
                }

                completionCandidates += addCountedCollectionLogGroupCompletionCandidates(group, observedCount, completionCandidateTaskIds);
                continue;
            }

            boolean complete = false;

            if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG)
            {
                ItemRequirement requirement = resolveCollectionLogRequirement(task);
                if (requirement != null)
                {
                    complete = collectionLogService.countObtained(requirement.itemIds) >= requirement.requiredCount;
                }
            }
            else if (verification.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY)
            {
                complete = prerequisiteTrackerService.isDiaryComplete(
                        verification.getRegion(), verification.getDifficulty());
            }
            else if (verification.getType() == TaskVerification.VerificationType.SKILL
                    && verification.getExperience() != null
                    && verification.getCount() != null)
            {
                int at99 = prerequisiteTrackerService.countSkillsAt99(verification.getExperience().keySet());
                complete = at99 >= verification.getCount();
            }

            if (complete)
            {
                String id = task.getId();
                if (id == null || id.trim().isEmpty())
                {
                    continue;
                }

                if (completionCandidateTaskIds != null)
                {
                    completionCandidateTaskIds.add(id);
                }
                completionCandidates++;
            }
        }

        return completionCandidates;
    }

    private boolean isCountedCollectionLogSync(TaskVerification verification)
    {
        if (verification == null || verification.getCount() == null)
        {
            return false;
        }

        return verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                || verification.getType() == TaskVerification.VerificationType.SKILL;
    }

    private int observedCollectionLogSyncCount(XtremeTask task, TaskVerification verification)
    {
        if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG)
        {
            ItemRequirement requirement = resolveCollectionLogRequirement(task);
            return requirement == null ? -1 : Math.toIntExact(collectionLogService.countObtained(requirement.itemIds));
        }

        if (verification.getType() == TaskVerification.VerificationType.SKILL
                && verification.getExperience() != null)
        {
            return prerequisiteTrackerService.countSkillsAt99(verification.getExperience().keySet());
        }

        return -1;
    }

    private String countedCollectionLogGroupKey(XtremeTask task, TaskVerification verification)
    {
        if (task == null || verification == null || !isCountedCollectionLogSync(verification))
        {
            return null;
        }

        StringBuilder key = new StringBuilder();
        key.append("source=").append(Objects.toString(task.getSource(), ""))
                .append("|type=").append(Objects.toString(verification.getType(), ""));

        if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG)
        {
            ItemRequirement requirement = resolveCollectionLogRequirement(task);
            if (requirement == null || requirement.itemIds == null || requirement.itemIds.length == 0)
            {
                return null;
            }

            int[] sortedItemIds = Arrays.stream(requirement.itemIds)
                    .filter(itemId -> itemId > 0)
                    .distinct()
                    .sorted()
                    .toArray();
            if (sortedItemIds.length == 0)
            {
                return null;
            }

            if (!isAncientPageRequirement(sortedItemIds))
            {
                key.append("|tier=").append(Objects.toString(task.getTier(), ""));
            }

            key.append("|items=");
            for (int itemId : sortedItemIds)
            {
                key.append(itemId).append(',');
            }
            return key.toString();
        }

        if (verification.getType() == TaskVerification.VerificationType.SKILL)
        {
            if (verification.getExperience() == null || verification.getExperience().isEmpty())
            {
                return null;
            }

            List<String> skillKeys = verification.getExperience().keySet().stream()
                    .filter(Objects::nonNull)
                    .map(skill -> skill.trim().toLowerCase(Locale.ROOT))
                    .filter(skill -> !skill.isEmpty())
                    .sorted()
                    .collect(Collectors.toList());
            if (skillKeys.isEmpty())
            {
                return null;
            }

            key.append("|tier=").append(Objects.toString(task.getTier(), ""));
            key.append("|skills=").append(String.join(",", skillKeys));
            return key.toString();
        }

        return null;
    }

    private static boolean isAncientPageRequirement(int[] itemIds)
    {
        int expectedCount = ANCIENT_PAGE_LAST_ITEM_ID - ANCIENT_PAGE_FIRST_ITEM_ID + 1;
        if (itemIds == null || itemIds.length != expectedCount)
        {
            return false;
        }

        int[] sorted = Arrays.stream(itemIds)
                .filter(itemId -> itemId > 0)
                .distinct()
                .sorted()
                .toArray();
        if (sorted.length != expectedCount)
        {
            return false;
        }

        for (int i = 0; i < sorted.length; i++)
        {
            if (sorted[i] != ANCIENT_PAGE_FIRST_ITEM_ID + i)
            {
                return false;
            }
        }
        return true;
    }

    private List<XtremeTask> countedCollectionLogGroupFor(XtremeTask task, TaskVerification verification)
    {
        String key = countedCollectionLogGroupKey(task, verification);
        if (key == null)
        {
            return Collections.emptyList();
        }

        List<XtremeTask> group = new ArrayList<>();
        for (XtremeTask candidate : tasks)
        {
            if (candidate == null || !isCollectionLogSyncSource(candidate.getSource()))
            {
                continue;
            }

            TaskVerification candidateVerification = candidate.getVerification();
            if (!isCountedCollectionLogSync(candidateVerification))
            {
                continue;
            }

            if (key.equals(countedCollectionLogGroupKey(candidate, candidateVerification)))
            {
                group.add(candidate);
            }
        }
        return group;
    }

    private int addCountedCollectionLogGroupCompletionCandidates(
            List<XtremeTask> group,
            int observedCount,
            List<String> completionCandidateTaskIds)
    {
        if (group == null || group.isEmpty())
        {
            return 0;
        }

        int desiredCompleted = desiredCompletedForCountedGroup(group, observedCount);
        int currentlyCompleted = 0;
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && isTaskCompleted(groupedTask))
            {
                currentlyCompleted++;
            }
        }

        int remainingToSync = Math.max(0, desiredCompleted - currentlyCompleted);
        if (remainingToSync <= 0)
        {
            return 0;
        }

        int completionCandidates = 0;
        for (XtremeTask groupedTask : group)
        {
            if (remainingToSync <= 0)
            {
                break;
            }

            String id = groupedTask.getId();
            if (id == null || id.trim().isEmpty() || isTaskCompleted(groupedTask))
            {
                continue;
            }

            if (completionCandidateTaskIds != null)
            {
                completionCandidateTaskIds.add(id);
            }
            completionCandidates++;
            remainingToSync--;
        }

        return completionCandidates;
    }

    private int desiredCompletedForCountedGroup(List<XtremeTask> group, int observedCount)
    {
        if (group == null || group.isEmpty() || observedCount <= 0)
        {
            return 0;
        }

        if (isDisplaySequenceGroup(group))
        {
            return desiredCompletedForDisplaySequenceGroup(group);
        }

        List<Integer> thresholds = countedGroupThresholds(group);
        int desired = 0;
        for (Integer threshold : thresholds)
        {
            if (threshold != null && observedCount >= threshold)
            {
                desired++;
            }
        }
        return Math.min(group.size(), desired);
    }

    private boolean isDisplaySequenceGroup(List<XtremeTask> group)
    {
        if (group == null || group.isEmpty())
        {
            return false;
        }

        for (XtremeTask task : group)
        {
            if (task != null && isDisplaySequenceTask(task.getName()))
            {
                return true;
            }
        }
        return false;
    }

    private int desiredCompletedForDisplaySequenceGroup(List<XtremeTask> group)
    {
        if (collectionLogService == null)
        {
            return 0;
        }

        int completed = 0;
        for (XtremeTask task : group)
        {
            TaskVerification verification = task == null ? null : task.getVerification();
            Integer count = verification == null ? null : verification.getCount();
            int[] itemIds = verification == null ? null : verification.getItemIds();
            int index = count == null ? -1 : count - 1;
            if (itemIds == null || index < 0 || index >= itemIds.length)
            {
                break;
            }

            if (!collectionLogService.isItemObtained(itemIds[index]))
            {
                break;
            }

            completed++;
        }
        return Math.min(group.size(), completed);
    }

    private List<Integer> countedGroupThresholds(List<XtremeTask> group)
    {
        List<Integer> explicit = new ArrayList<>();
        boolean strictlyIncreasing = true;
        int previous = 0;
        for (XtremeTask groupedTask : group)
        {
            TaskVerification verification = groupedTask == null ? null : groupedTask.getVerification();
            Integer count = verification == null ? null : verification.getCount();
            if (count == null || count <= 0)
            {
                strictlyIncreasing = false;
                count = 1;
            }
            if (!explicit.isEmpty() && count <= previous)
            {
                strictlyIncreasing = false;
            }
            explicit.add(count);
            previous = count;
        }

        if (strictlyIncreasing)
        {
            return explicit;
        }

        int base = explicit.stream().filter(Objects::nonNull).min(Integer::compareTo).orElse(1);
        List<Integer> inferred = new ArrayList<>(group.size());
        for (int i = 0; i < group.size(); i++)
        {
            inferred.add(base + i);
        }
        return inferred;
    }

    @Override
    public void syncCollectionLogsAndPersist()
    {
        if (!hasTaskPackLoaded())
        {
            setSyncResultAndChat(TaskSource.COLLECTION_LOG, "No tasks loaded. Load tasks first.");
            return;
        }

        setLastSyncedTaskNames(TaskSource.COLLECTION_LOG, null);
        clientThread.invokeLater(this::runCollectionLogSyncFromCache);
    }

    private void runCollectionLogSyncFromCache()
    {
        List<String> completionCandidateTaskIds = new ArrayList<>();
        int completionCandidates = findCollectionLogCompletionCandidatesFromCache(completionCandidateTaskIds);
        setLastSyncedTaskNames(TaskSource.COLLECTION_LOG, completionCandidateTaskIds);
        setSyncCompletionCandidatesForSource(TaskSource.COLLECTION_LOG, completionCandidateTaskIds);
        int capturedItems = collectionLogService.getCapturedItemCount();
        setSyncMismatchTasksForSource(TaskSource.COLLECTION_LOG,
                findCollectionLogSyncMismatches(capturedItems > 0));

        if (completionCandidates > 0)
        {
            setSyncResultAndChat(TaskSource.COLLECTION_LOG, "CLOG/AD sync done! " + completionCandidates + " completed task(s) found. No tasks were marked complete automatically."
                    + syncMismatchResultSuffix(TaskSource.COLLECTION_LOG));
        }
        else if (capturedItems == 0)
        {
            setSyncResultAndChat(TaskSource.COLLECTION_LOG, "CLOG/AD sync done! No CLOG items are cached yet this session - open your Collection Log, then sync again. Achievement Diaries were checked from in-game diary progress."
                    + syncMismatchResultSuffix(TaskSource.COLLECTION_LOG));
        }
        else
        {
            setSyncResultAndChat(TaskSource.COLLECTION_LOG, "CLOG/AD sync done! No new completions found."
                    + syncMismatchResultSuffix(TaskSource.COLLECTION_LOG));
        }

        if (capturedItems > 0)
        {
            collectionLogService.clearPendingAncientPageDropCountSinceLastSync();
        }

        rebuildTierCounts();
        dirty = true;
        if (activeAccountKey != null)
        {
            saveStateForAccount(activeAccountKey);
            dirty = false;
        }
    }

    List<XtremeTask> findCollectionLogSyncMismatches(boolean collectionLogCacheAvailable)
    {
        List<XtremeTask> mismatches = new ArrayList<>();
        Set<String> processedCountedGroups = new HashSet<>();

        for (XtremeTask task : tasks)
        {
            if (!isCollectionLogSyncSource(task.getSource()) || !isTaskCompleted(task))
            {
                continue;
            }

            TaskVerification verification = task.getVerification();
            if (verification == null)
            {
                continue;
            }

            if (isCountedCollectionLogSync(verification))
            {
                if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG && !collectionLogCacheAvailable)
                {
                    continue;
                }

                String groupKey = countedCollectionLogGroupKey(task, verification);
                if (groupKey == null)
                {
                    continue;
                }

                if (!processedCountedGroups.add(groupKey))
                {
                    continue;
                }

                int observedCount = observedCollectionLogSyncCount(task, verification);
                if (observedCount < 0)
                {
                    addCompletedTasksFromGroup(mismatches, countedCollectionLogGroupFor(task, verification));
                    continue;
                }

                List<XtremeTask> group = countedCollectionLogGroupFor(task, verification);
                int desiredCompleted = desiredCompletedForCountedGroup(group, observedCount);
                List<XtremeTask> completedGroup = completedTasksFromGroupInGroupOrder(group);
                for (int i = desiredCompleted; i < completedGroup.size(); i++)
                {
                    mismatches.add(completedGroup.get(i));
                }
                continue;
            }

            if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG && !collectionLogCacheAvailable)
            {
                continue;
            }

            if (!isCollectionLogTaskCompleteInGame(task, verification))
            {
                mismatches.add(task);
            }
        }

        return mismatches;
    }

    private void addCompletedTasksFromGroup(List<XtremeTask> mismatches, List<XtremeTask> group)
    {
        if (group == null || group.isEmpty())
        {
            return;
        }

        for (XtremeTask groupedTask : completedTasksFromGroupInGroupOrder(group))
        {
            mismatches.add(groupedTask);
        }
    }

    private List<XtremeTask> completedTasksFromGroupInGroupOrder(List<XtremeTask> group)
    {
        if (group == null || group.isEmpty())
        {
            return Collections.emptyList();
        }

        List<XtremeTask> completed = new ArrayList<>();
        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && isTaskCompleted(groupedTask))
            {
                completed.add(groupedTask);
            }
        }
        return completed;
    }

    private boolean isCollectionLogTaskCompleteInGame(XtremeTask task, TaskVerification verification)
    {
        if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG)
        {
            ItemRequirement requirement = resolveCollectionLogRequirement(task);
            return requirement != null
                    && collectionLogService.countObtained(requirement.itemIds) >= requirement.requiredCount;
        }

        if (verification.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY)
        {
            return prerequisiteTrackerService.isDiaryComplete(verification.getRegion(), verification.getDifficulty());
        }

        if (verification.getType() == TaskVerification.VerificationType.SKILL
                && verification.getExperience() != null
                && verification.getCount() != null)
        {
            return prerequisiteTrackerService.countSkillsAt99(verification.getExperience().keySet()) >= verification.getCount();
        }

        return false;
    }

    @Override
    public String getLastSyncResult()
    {
        if (lastCombatAchievementSyncResult != null && lastCollectionLogSyncResult != null)
        {
            return lastCombatAchievementSyncResultAtEpochMillis >= lastCollectionLogSyncResultAtEpochMillis
                    ? lastCombatAchievementSyncResult
                    : lastCollectionLogSyncResult;
        }
        return lastCombatAchievementSyncResult != null
                ? lastCombatAchievementSyncResult
                : lastCollectionLogSyncResult;
    }

    @Override
    public String getLastSyncResultAtLocalTime()
    {
        if (lastCombatAchievementSyncResult != null && lastCollectionLogSyncResult != null)
        {
            return lastCombatAchievementSyncResultAtEpochMillis >= lastCollectionLogSyncResultAtEpochMillis
                    ? lastCombatAchievementSyncResultAtLocalTime
                    : lastCollectionLogSyncResultAtLocalTime;
        }
        return lastCombatAchievementSyncResult != null
                ? lastCombatAchievementSyncResultAtLocalTime
                : lastCollectionLogSyncResultAtLocalTime;
    }

    @Override
    public String getLastCombatAchievementSyncResult()
    {
        return lastCombatAchievementSyncResult;
    }

    @Override
    public String getLastCombatAchievementSyncResultAtLocalTime()
    {
        return lastCombatAchievementSyncResultAtLocalTime;
    }

    @Override
    public String getLastCollectionLogSyncResult()
    {
        return lastCollectionLogSyncResult;
    }

    @Override
    public String getLastCollectionLogSyncResultAtLocalTime()
    {
        return lastCollectionLogSyncResultAtLocalTime;
    }

    @Override
    public boolean isCollectionLogSyncPending()
    {
        return false;
    }

    private void setSyncMismatchTasksForSource(TaskSource source, List<XtremeTask> mismatches)
    {
        if (source == null)
        {
            return;
        }

        Map<String, XtremeTask> byId = tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));

        syncMismatchTaskIds.removeIf(id -> {
            XtremeTask task = byId.get(id);
            return task == null || matchesSyncMismatchSource(task, source);
        });

        Set<String> seen = new HashSet<>();
        if (mismatches != null)
        {
            for (XtremeTask task : mismatches)
            {
                if (task != null
                        && matchesSyncMismatchSource(task, source)
                        && task.getId() != null
                        && seen.add(task.getId()))
                {
                    syncMismatchTaskIds.add(task.getId());
                }
            }
        }

        syncMismatchTitle = syncMismatchTaskIds.isEmpty()
                ? ""
                : "Review completed tasks";
    }

    private void setSyncCompletionCandidatesForSource(TaskSource source, List<String> candidateIds)
    {
        if (source == null)
        {
            return;
        }

        Map<String, XtremeTask> byId = tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));

        syncCompletionCandidateTaskIds.removeIf(id -> {
            XtremeTask task = byId.get(id);
            return task == null || matchesSyncMismatchSource(task, source);
        });

        Set<String> seen = new HashSet<>();
        if (candidateIds != null)
        {
            for (String id : candidateIds)
            {
                XtremeTask task = byId.get(id);
                if (task != null
                        && !isTaskCompleted(task)
                        && matchesSyncMismatchSource(task, source)
                        && id != null
                        && !id.trim().isEmpty()
                        && seen.add(id))
                {
                    syncCompletionCandidateTaskIds.add(id);
                }
            }
        }
    }

    private void setSyncMismatchTasks(String title, List<XtremeTask> mismatches)
    {
        syncMismatchTaskIds.clear();
        syncMismatchTitle = "";
        if (mismatches == null || mismatches.isEmpty())
        {
            return;
        }

        Set<String> seen = new HashSet<>();
        for (XtremeTask task : mismatches)
        {
            if (task != null && task.getId() != null && seen.add(task.getId()))
            {
                syncMismatchTaskIds.add(task.getId());
            }
        }
        if (!syncMismatchTaskIds.isEmpty())
        {
            syncMismatchTitle = title == null ? "Review completed tasks" : title;
        }
    }

    private String syncMismatchResultSuffix(TaskSource source)
    {
        int count = getSyncMismatchTasks(source).size();
        if (count <= 0)
        {
            return "";
        }
        String label = source == TaskSource.COMBAT_ACHIEVEMENT ? "CA" : "CLOG/AD";
        return " Review " + count + " " + label + " plugin completion(s) not found in game data.";
    }

    @Override
    public List<XtremeTask> getSyncMismatchTasks()
    {
        int capturedItemCount = collectionLogService == null ? -1 : collectionLogService.getCapturedItemCount();
        int idsHash = syncMismatchTaskIds.hashCode();
        int manualHash = manualCompletedTaskIds.hashCode();
        int syncedHash = syncedCompletedTaskIds.hashCode();
        int taskCount = tasks.size();
        if (syncMismatchTasksCacheValid
                && idsHash == syncMismatchTasksCacheIdsHash
                && manualHash == syncMismatchTasksCacheManualHash
                && syncedHash == syncMismatchTasksCacheSyncedHash
                && taskCount == syncMismatchTasksCacheTaskCount
                && capturedItemCount == syncMismatchTasksCacheCapturedItemCount)
        {
            return syncMismatchTasksCache;
        }

        pruneResolvedCollectionLogSyncMismatches();
        if (syncMismatchTaskIds.isEmpty())
        {
            updateSyncMismatchTasksCache(Collections.emptyList(), capturedItemCount);
            return syncMismatchTasksCache;
        }

        Map<String, XtremeTask> byId = tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));
        List<XtremeTask> out = new ArrayList<>();
        for (String id : syncMismatchTaskIds)
        {
            XtremeTask task = byId.get(id);
            if (task != null && isTaskCompleted(task))
            {
                out.add(decorateCurrentSequenceTask(task));
            }
        }
        updateSyncMismatchTasksCache(out, capturedItemCount);
        return syncMismatchTasksCache;
    }

    private void updateSyncMismatchTasksCache(List<XtremeTask> tasks, int capturedItemCount)
    {
        syncMismatchTasksCache = Collections.unmodifiableList(new ArrayList<>(tasks));
        syncMismatchTasksCacheValid = true;
        syncMismatchTasksCacheIdsHash = syncMismatchTaskIds.hashCode();
        syncMismatchTasksCacheManualHash = manualCompletedTaskIds.hashCode();
        syncMismatchTasksCacheSyncedHash = syncedCompletedTaskIds.hashCode();
        syncMismatchTasksCacheTaskCount = this.tasks.size();
        syncMismatchTasksCacheCapturedItemCount = capturedItemCount;
    }

    private void pruneResolvedCollectionLogSyncMismatches()
    {
        if (syncMismatchTaskIds.isEmpty()
                || collectionLogService == null
                || collectionLogService.getCapturedItemCount() <= 0)
        {
            return;
        }

        Map<String, XtremeTask> byId = tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));

        boolean hasCollectionLogItemMismatch = false;
        for (String id : syncMismatchTaskIds)
        {
            XtremeTask task = byId.get(id);
            TaskVerification verification = task == null ? null : task.getVerification();
            if (task != null
                    && task.getSource() == TaskSource.COLLECTION_LOG
                    && verification != null
                    && verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG)
            {
                hasCollectionLogItemMismatch = true;
                break;
            }
        }
        if (!hasCollectionLogItemMismatch)
        {
            return;
        }

        Set<String> currentCollectionLogMismatchIds = findCollectionLogItemSyncMismatchIds();

        boolean changed = syncMismatchTaskIds.removeIf(id -> {
            XtremeTask task = byId.get(id);
            TaskVerification verification = task == null ? null : task.getVerification();
            return task != null
                    && task.getSource() == TaskSource.COLLECTION_LOG
                    && verification != null
                    && verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                    && !currentCollectionLogMismatchIds.contains(id);
        });

        if (changed)
        {
            if (syncMismatchTaskIds.isEmpty())
            {
                syncMismatchTitle = "";
            }
            dirty = true;
            persistIfPossible();
        }
    }

    private Set<String> findCollectionLogItemSyncMismatchIds()
    {
        Set<String> mismatchIds = new HashSet<>();
        Set<String> processedCountedGroups = new HashSet<>();

        for (XtremeTask task : tasks)
        {
            if (task == null || task.getSource() != TaskSource.COLLECTION_LOG || !isTaskCompleted(task))
            {
                continue;
            }

            TaskVerification verification = task.getVerification();
            if (verification == null || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG)
            {
                continue;
            }

            if (isCountedCollectionLogSync(verification))
            {
                String groupKey = countedCollectionLogGroupKey(task, verification);
                if (groupKey == null || !processedCountedGroups.add(groupKey))
                {
                    continue;
                }

                int observedCount = observedCollectionLogSyncCount(task, verification);
                List<XtremeTask> group = countedCollectionLogGroupFor(task, verification);
                if (observedCount < 0)
                {
                    addCompletedTaskIdsFromGroup(mismatchIds, group);
                    continue;
                }

                int desiredCompleted = desiredCompletedForCountedGroup(group, observedCount);
                List<XtremeTask> completedGroup = completedTasksFromGroupInGroupOrder(group);
                for (int i = desiredCompleted; i < completedGroup.size(); i++)
                {
                    XtremeTask mismatchedTask = completedGroup.get(i);
                    if (mismatchedTask != null && mismatchedTask.getId() != null)
                    {
                        mismatchIds.add(mismatchedTask.getId());
                    }
                }
                continue;
            }

            ItemRequirement requirement = resolveCollectionLogRequirement(task);
            if (requirement == null
                    || collectionLogService.countObtained(requirement.itemIds) < requirement.requiredCount)
            {
                mismatchIds.add(task.getId());
            }
        }

        return mismatchIds;
    }

    private void addCompletedTaskIdsFromGroup(Set<String> mismatchIds, List<XtremeTask> group)
    {
        if (mismatchIds == null || group == null || group.isEmpty())
        {
            return;
        }

        for (XtremeTask groupedTask : completedTasksFromGroupInGroupOrder(group))
        {
            if (groupedTask != null && groupedTask.getId() != null)
            {
                mismatchIds.add(groupedTask.getId());
            }
        }
    }

    @Override
    public List<XtremeTask> getSyncMismatchTasks(TaskSource source)
    {
        if (source == null)
        {
            return getSyncMismatchTasks();
        }

        return getSyncMismatchTasks().stream()
                .filter(task -> matchesSyncMismatchSource(task, source))
                .collect(Collectors.toList());
    }

    private static boolean matchesSyncMismatchSource(XtremeTask task, TaskSource source)
    {
        if (task == null || source == null)
        {
            return false;
        }

        if (source == TaskSource.COLLECTION_LOG)
        {
            return isCollectionLogSyncSource(task.getSource());
        }

        return task.getSource() == source;
    }

    @Override
    public List<XtremeTask> getSyncCompletionCandidateTasks(TaskSource source)
    {
        if (syncCompletionCandidateTaskIds.isEmpty())
        {
            return Collections.emptyList();
        }

        Map<String, XtremeTask> byId = tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));

        List<XtremeTask> out = new ArrayList<>();
        boolean changed = false;
        for (String id : syncCompletionCandidateTaskIds)
        {
            XtremeTask task = byId.get(id);
            if (task == null || isTaskCompleted(task))
            {
                changed = true;
                continue;
            }

            if (source == null || matchesSyncMismatchSource(task, source))
            {
                out.add(decorateCurrentSequenceTask(task));
            }
        }

        if (changed)
        {
            syncCompletionCandidateTaskIds.removeIf(id -> {
                XtremeTask task = byId.get(id);
                return task == null || isTaskCompleted(task);
            });
            dirty = true;
            persistIfPossible();
        }

        return out;
    }

    @Override
    public void dismissSyncCompletionCandidateReview(TaskSource source)
    {
        if (source == null)
        {
            syncCompletionCandidateTaskIds.clear();
        }
        else
        {
            Map<String, XtremeTask> byId = tasks.stream()
                    .filter(Objects::nonNull)
                    .filter(task -> task.getId() != null)
                    .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));

            syncCompletionCandidateTaskIds.removeIf(id -> {
                XtremeTask task = byId.get(id);
                return task == null || matchesSyncMismatchSource(task, source);
            });
        }

        dirty = true;
        persistIfPossible();
    }

    @Override
    public void markSyncCompletionCandidateTasksCompleteAndPersist(List<XtremeTask> tasksToMark)
    {
        if (tasksToMark == null || tasksToMark.isEmpty())
        {
            return;
        }

        Set<String> ids = tasksToMark.stream()
                .filter(Objects::nonNull)
                .map(XtremeTask::getId)
                .filter(id -> id != null && !id.trim().isEmpty())
                .collect(Collectors.toSet());
        if (ids.isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();
        for (String id : ids)
        {
            XtremeTask task = findTaskById(id);
            if (task == null || isTaskCompleted(task))
            {
                continue;
            }

            syncedCompletedTaskIds.add(id);
            syncedCompletionTimestamps.putIfAbsent(id, now);
            snapshotCompletedTaskTime(id);
        }

        syncCompletionCandidateTaskIds.removeAll(ids);
        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    @Override
    public String getSyncMismatchTitle()
    {
        return syncMismatchTitle;
    }

    @Override
    public String getSyncMismatchGameProgressLabel(XtremeTask task)
    {
        if (task == null || task.getSource() != TaskSource.COLLECTION_LOG)
        {
            return null;
        }

        TaskVerification verification = task.getVerification();
        if (!isCountedCollectionLogSync(verification))
        {
            return null;
        }

        List<XtremeTask> group = countedCollectionLogGroupFor(task, verification);
        if (group.size() <= 1)
        {
            return null;
        }
        if (isDisplaySequenceGroup(group))
        {
            return null;
        }

        int observedCount = observedCollectionLogSyncCount(task, verification);
        if (observedCount < 0)
        {
            return null;
        }

        int desiredCompleted = desiredCompletedForCountedGroup(group, observedCount);
        return "Sync: " + desiredCompleted + "/" + group.size() + " completed";
    }

    @Override
    public void dismissSyncMismatchReview()
    {
        clearSyncMismatchReviewState();
        dirty = true;
        persistIfPossible();
    }

    @Override
    public void dismissSyncMismatchReview(TaskSource source)
    {
        if (source == null)
        {
            dismissSyncMismatchReview();
            return;
        }

        Map<String, XtremeTask> byId = tasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(XtremeTask::getId, task -> task, (first, ignored) -> first));

        syncMismatchTaskIds.removeIf(id -> {
            XtremeTask task = byId.get(id);
            return task == null || matchesSyncMismatchSource(task, source);
        });
        if (syncMismatchTaskIds.isEmpty())
        {
            syncMismatchTitle = "";
        }
        dirty = true;
        persistIfPossible();
    }

    private void clearSyncMismatchReviewState()
    {
        syncMismatchTaskIds.clear();
        syncMismatchTitle = "";
    }

    @Override
    public void markSyncMismatchTaskIncompleteAndPersist(XtremeTask task)
    {
        markSyncMismatchTasksIncompleteAndPersist(task == null ? Collections.emptyList() : Collections.singletonList(task));
    }

    @Override
    public void markSyncMismatchTasksIncompleteAndPersist(List<XtremeTask> tasksToMark)
    {
        if (tasksToMark == null || tasksToMark.isEmpty())
        {
            return;
        }

        Set<String> ids = tasksToMark.stream()
                .filter(Objects::nonNull)
                .map(XtremeTask::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty())
        {
            return;
        }

        for (String id : ids)
        {
            markTaskIncomplete(id);
        }
        syncMismatchTaskIds.removeAll(ids);
        if (currentTask != null && ids.contains(currentTask.getId()))
        {
            currentTask = null;
            currentTaskId = null;
            clearCurrentTaskCollectionLogBaseline();
        }
        if (syncMismatchTaskIds.isEmpty())
        {
            syncMismatchTitle = "";
        }
        rebuildTierCounts();
        dirty = true;
        persistIfPossible();
    }

    @Override
    public void markAllSyncMismatchTasksIncompleteAndPersist()
    {
        List<XtremeTask> mismatches = getSyncMismatchTasks();
        if (mismatches.isEmpty())
        {
            dismissSyncMismatchReview();
            return;
        }

        markSyncMismatchTasksIncompleteAndPersist(mismatches);
    }

    @Override
    public void debugCollectionLogCacheAndReport()
    {
        clientThread.invokeLater(() -> {
            Set<Integer> cachedIds = collectionLogService.getCachedItemIds();
            int capturedItems = cachedIds.size();

            // Count how many CLOG tasks have valid (deserialized) verification requirements
            long tasksWithClogReq = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
                    .filter(t -> {
                        TaskVerification v = t.getVerification();
                        return v != null && v.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                                && resolveCollectionLogRequirement(t) != null;
                    })
                    .count();

            long tasksWithDiaryReq = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.DIARY_ACHIEVEMENT)
                    .filter(t -> {
                        TaskVerification v = t.getVerification();
                        return v != null && v.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY
                                && v.getRegion() != null && v.getDifficulty() != null;
                    })
                    .count();

            long tasksWithSkillReq = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
                    .filter(t -> {
                        TaskVerification v = t.getVerification();
                        return v != null && v.getType() == TaskVerification.VerificationType.SKILL
                                && v.getExperience() != null && v.getCount() != null;
                    })
                    .count();

            long totalClogTasks = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
                    .count();
            long totalDiaryTasks = tasks.stream()
                    .filter(t -> t.getSource() == TaskSource.DIARY_ACHIEVEMENT)
                    .count();
            long totalCollectionLogSyncTasks = totalClogTasks + totalDiaryTasks;

            if (capturedItems == 0)
            {
                chat("CLOG debug: 0 items cached. Open the Collection Log in-game to populate."
                        + " Tasks with requirements loaded: clog=" + tasksWithClogReq
                        + " diary=" + tasksWithDiaryReq
                        + " skill=" + tasksWithSkillReq + "/" + totalCollectionLogSyncTasks + " total.");
            }
            else
            {
                chat("CLOG debug: " + capturedItems + " item(s) cached: " + cachedIds
                        + ". Tasks with requirements loaded: clog=" + tasksWithClogReq
                        + " diary=" + tasksWithDiaryReq
                        + " skill=" + tasksWithSkillReq + "/" + totalCollectionLogSyncTasks + " total.");
            }
        });
    }

    @Override
    public List<PrerequisiteStatus> getPrerequisiteStatuses(XtremeTask task)
    {
        if (task == null || task.getPrereqs() == null)
        {
            return List.of();
        }

        return prerequisiteTrackerService.evaluate(task.getPrereqs());
    }

    /**
     * Sends a one-time chat nudge when the bundled task pack is newer than what the user
     * last saw. Safe to call multiple times; only fires once per version bump.
     * Must be called after both loadedPackVersion and lastSeenPackVersion are current.
     */
    private void maybeFireVersionNudge() {
        if (loadedPackVersion == 0) return; // pack not loaded yet
        boolean isFirstLoad = (lastSeenPackVersion == 0 && loadedPackVersion == 1);
        log.debug("Checking task-pack version: loadedPackVersion={}, lastSeenPackVersion={}, isFirstLoad={}",
                loadedPackVersion, lastSeenPackVersion, isFirstLoad);
        if (!isFirstLoad && loadedPackVersion > lastSeenPackVersion) {
            log.debug("Task-pack version advanced from {} to {}", lastSeenPackVersion, loadedPackVersion);
            lastSeenPackVersion = loadedPackVersion;
            dirty = true;
            persistIfPossible();
        }
    }

    private void reloadTaskPackInternal() {
        try {
            final int previousKnownCount = lastKnownTaskCount;
            final boolean isFirstLoad = (lastSeenPackVersion == 0 && loadedPackVersion == 0);

            InputStream in = XtremeTaskerPlugin.class
                    .getClassLoader()
                    .getResourceAsStream("task_data/tasks.json");

            if (in == null) {
                throw new IllegalStateException("tasks.json resource not found");
            }

            String json;
            try (in) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            TaskPack pack = gson.fromJson(json, TaskPack.class);

            if (pack == null || pack.tasks == null) {
                throw new IllegalArgumentException("Invalid tasks.json");
            }

            List<XtremeTask> loaded = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            for (TaskDef d : pack.tasks) {
                if (d == null) continue;

                TaskTier tier = (d.tier == TaskTier.GRANDMASTER) ? TaskTier.MASTER : d.tier;

                String id = ensureId(d.id, d.name, d.source, tier);

                XtremeTask task = new XtremeTask(
                        id,
                        safeTrim(d.name),
                        d.source,
                        tier,
                        d.iconItemId != null ? d.iconItemId : d.displayItemId,
                        safeTrim(d.iconKey),
                        safeTrim(d.description),
                        safeTrim(d.prereqs),
                        safeTrim(d.wikiUrl),
                        d.verification,
                        safeTrim(d.tip)
                );

                if (!seenIds.add(task.getId())) {
                    // IMPORTANT: Do NOT rename duplicates; it breaks persisted completion mapping when pack order changes.
                    log.warn("Duplicate task id in tasks.json: {} (name={}). Skipping duplicate.", task.getId(), task.getName());
                    continue;
                }

                loaded.add(task);
            }

            tasks.clear();
            tasks.addAll(loaded);
            taskPackLoaded = !tasks.isEmpty();

            Set<String> validIds = tasks.stream()
                    .map(XtremeTask::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            retireUnknownCompletedTaskIds(validIds);

            if (currentTaskId != null && !validIds.contains(currentTaskId))
            {
                currentTaskId = null;
                currentTask = null;
            }
            else if (currentTask != null && !validIds.contains(currentTask.getId()))
            {
                currentTask = null;
            }


            resolveCurrentTaskIfPossible();

            rebuildTierCounts();

            loadedPackVersion = pack.version;

            // Fire the version nudge if we're already logged in; otherwise onGameStateChanged
            // will call maybeFireVersionNudge() once the account state has been loaded.
            if (client.getGameState() == GameState.LOGGED_IN) {
                maybeFireVersionNudge();
            }

            dirty = true;
            persistIfPossible();

            detectNewTaskIds(tasks, previousKnownCount);
            int newTaskCount = newTaskIds.size();
            if (!isFirstLoad && newTaskCount > 0) {
                chat("[Xtreme Tasker] You have " + newTaskCount + " new task" + (newTaskCount == 1 ? "" : "s") + "! Open the Tasks tab to see them.");
            } else if (!isFirstLoad) {
                log.debug("Task list loaded with {} tasks.", tasks.size());
            }
            // Update the known count now that user has acknowledged the reload
            lastKnownTaskCount = tasks.size();
            knownTaskIds.clear();
            knownTaskIds.addAll(validIds);
            dirty = true;
            persistIfPossible();

        } catch (Exception e) {
            log.error("Failed to load embedded tasks.json", e);
            tasks.clear();
            taskPackLoaded = false;
            rebuildTierCounts();
            chat("Failed to load tasks.json (see logs).");
        }
    }

    private static class TaskPack {
        int version;
        List<TaskDef> tasks;
    }

    private static class TaskDef {
        String id;
        String name;
        TaskSource source;
        TaskTier tier;

        Integer iconItemId;
        Integer displayItemId; // CLOG tasks use this field name in JSON
        String iconKey;

        String description;
        String prereqs;
        String wikiUrl;
        String tip;
        TaskVerification verification;
    }

    // CA struct params. Source: osrs-reldo task-types.json intParamMap/stringParamMap.
    private static final int CA_STRUCT_PARAM_NAME = 1308;
    private static final int CA_STRUCT_PARAM_TASK_ID = 1306; // varplayer bit index (NOT sortId/display order)

    private void loadCombatAchievementMappings()
    {
        caTaskIdsByName.clear();
        caTaskIdsByNormalizedName.clear();

        try (InputStream in = XtremeTaskerPlugin.class
                .getClassLoader()
                .getResourceAsStream("task_data/ca_structs.json"))
        {
            if (in == null)
            {
                log.warn("CA structs resource not found (task_data/ca_structs.json)");
                return;
            }

            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            CaStructEntry[] entries = gson.fromJson(json, CaStructEntry[].class);

            int loaded = 0;
            for (CaStructEntry entry : entries)
            {
                net.runelite.api.StructComposition struct = client.getStructComposition(entry.structId);
                if (struct == null)
                {
                    continue;
                }

                String taskName = struct.getStringValue(CA_STRUCT_PARAM_NAME);
                if (taskName == null || taskName.isEmpty())
                {
                    continue;
                }

                // Param 1306 is the game's internal task ID = the varplayer bit index.
                // sortId from ca_structs.json is only display order and must NOT be used here.
                int taskId = struct.getIntValue(CA_STRUCT_PARAM_TASK_ID);
                if (taskId < 0)
                {
                    continue;
                }

                caTaskIdsByName.put(taskName, taskId);
                caTaskIdsByNormalizedName.put(normalizeName(taskName), taskId);
                loaded++;
            }

            log.debug("Loaded {} combat achievement name-to-taskId mappings from {} structs", loaded, entries.length);
            if (loaded == 0)
            {
                log.warn("CA mapping loaded 0 entries — struct cache may be cold. Mappings will be retried on the next CA sync.");
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to load combat achievement mappings", e);
        }
    }

    private static final class CaStructEntry
    {
        int structId;
        int sortId;
    }

    private Integer resolveCombatAchievementTaskId(XtremeTask task)
    {
        TaskVerification verification = task.getVerification();
        if (verification != null
                && verification.getType() == TaskVerification.VerificationType.COMBAT_ACHIEVEMENT
                && verification.getTaskId() != null)
        {
            return verification.getTaskId();
        }

        Integer byName = caTaskIdsByName.get(task.getName());
        if (byName != null)
        {
            return byName;
        }

        return caTaskIdsByNormalizedName.get(normalizeName(task.getName()));
    }

    private ItemRequirement resolveCollectionLogRequirement(XtremeTask task)
    {
        TaskVerification verification = task.getVerification();
        if (verification != null
                && verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                && verification.getItemIds() != null
                && verification.getItemIds().length > 0)
        {
            int count = verification.getCount() == null ? 1 : Math.max(1, verification.getCount());
            return new ItemRequirement(verification.getItemIds(), count);
        }

        if (task.getIconItemId() != null && task.getIconItemId() > 0)
        {
            return new ItemRequirement(new int[]{task.getIconItemId()}, 1);
        }

        return null;
    }

    private static String normalizeName(String value)
    {
        if (value == null)
        {
            return "";
        }

        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static final class ItemRequirement
    {
        private final int[] itemIds;
        private final int requiredCount;

        private ItemRequirement(int[] itemIds, int requiredCount)
        {
            this.itemIds = itemIds;
            this.requiredCount = requiredCount;
        }
    }

    private void chat(String msg) {
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .name("")
                .sender("")
                .value(msg)
                .runeLiteFormattedMessage(new ChatMessageBuilder().append(msg).build())
                .build());
    }

    private void setSyncResultAndChat(TaskSource source, String msg)
    {
        lastSyncResult = msg;
        lastSyncResultAtEpochMillis = System.currentTimeMillis();
        lastSyncResultAtLocalTime = formatSaveTime(lastSyncResultAtEpochMillis);
        if (source == TaskSource.COMBAT_ACHIEVEMENT)
        {
            lastCombatAchievementSyncResult = msg;
            lastCombatAchievementSyncResultAtEpochMillis = lastSyncResultAtEpochMillis;
            lastCombatAchievementSyncResultAtLocalTime = lastSyncResultAtLocalTime;
        }
        else if (source == TaskSource.COLLECTION_LOG)
        {
            lastCollectionLogSyncResult = msg;
            lastCollectionLogSyncResultAtEpochMillis = lastSyncResultAtEpochMillis;
            lastCollectionLogSyncResultAtLocalTime = lastSyncResultAtLocalTime;
        }
        chat(msg);
    }

    private static String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String ensureId(String rawId, String name, TaskSource source, TaskTier tier) {
        String id = safeTrim(rawId);
        if (id != null) {
            return id;
        }

        String n = safeTrim(name);
        if (n == null) n = "unnamed";

        String s = (source == null) ? "UNKNOWN_SOURCE" : source.name();
        String t = (tier == null) ? "UNKNOWN_TIER" : tier.name();

        String base = (n + "|" + s + "|" + t).toLowerCase(Locale.ROOT);
        return "gen_" + Integer.toHexString(base.hashCode());
    }

    public void pushGameMessage(String msg)
    {
        if (msg == null || msg.trim().isEmpty())
        {
            return;
        }

        clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.CONSOLE, "", msg, "")
        );
    }

    @Override
    public XtremeTaskerConfig.RollSourceFilter getRollSourceFilter()
    {
        return config.rollSourceFilter();
    }

    @Override
    public boolean isTaskSkippingEnabled()
    {
        return config.enableTaskSkipping();
    }

    @Override
    public int getSkippedTaskCount()
    {
        return skippedTaskCount;
    }

    @Override
    public boolean condenseRepeatedTasks()
    {
        return config.condenseRepeatedTasks();
    }

    @Override
    public void toggleCondenseRepeatedTasks()
    {
        configManager.setConfiguration(CONFIG_GROUP, "condenseRepeatedTasks", !config.condenseRepeatedTasks());
    }

    @Override
    public boolean skipSingleIncompleteConfirmation()
    {
        return Boolean.parseBoolean(configManager.getConfiguration(CONFIG_GROUP, SKIP_SINGLE_INCOMPLETE_CONFIRM_KEY));
    }

    @Override
    public void setSkipSingleIncompleteConfirmation(boolean skip)
    {
        configManager.setConfiguration(CONFIG_GROUP, SKIP_SINGLE_INCOMPLETE_CONFIRM_KEY, String.valueOf(skip));
    }

    @Override
    public String getRollSkipNotice()
    {
        return rollSkipNotice;
    }

    @Override
    public boolean showTips()
    {
        return true;
    }
}
