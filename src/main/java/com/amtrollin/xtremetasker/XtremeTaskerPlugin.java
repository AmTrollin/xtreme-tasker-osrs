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
import com.google.gson.GsonBuilder;
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
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Xtreme Tasker",
        description = "Progressive random task generator using Combat Achievements and collection log entries, with completion tracking.",
        tags = {"tasks", "combat achievements", "collection log"}
)
public class XtremeTaskerPlugin extends Plugin implements TaskerService {

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
                            return t.getSource() == TaskSource.COLLECTION_LOG;
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
    private static final int MAX_COMPLETION_REGRESSION_WITHOUT_CURRENT_TASK_CHANGE = 3;
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

    private final Gson gson = new GsonBuilder().create();
    private final Random random = new Random();

    private final Set<String> manualCompletedTaskIds = new HashSet<>();
    private final Set<String> syncedCompletedTaskIds = new HashSet<>();
    private final Set<String> retiredTaskIds = new HashSet<>();
    private final Map<String, Long> manualCompletionTimestamps = new HashMap<>();
    private final Map<String, Long> syncedCompletionTimestamps = new HashMap<>();
    private final Map<String, Long> taskTimeTicksById = new HashMap<>();
    private final List<String> syncMismatchTaskIds = new ArrayList<>();
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

    private final List<XtremeTask> tasks = new ArrayList<>();
    private boolean taskPackLoaded = false;
    private int lastSeenPackVersion = 0;
    private int lastKnownTaskCount = 0;
    private final Set<String> knownTaskIds = new HashSet<>();
    /** Version of the last successfully loaded pack, independent of per-account state. */
    private int loadedPackVersion = 0;

    private boolean dirty = false;
    private boolean allowCompletionRegressionSave = false;
    private int flushTickCounter = 0;
    private int pendingCollectionLogSyncTicks = -1;
    private static final int FLUSH_EVERY_TICKS = 10; // ~6s (game tick ~0.6s)
    private Thread shutdownSaveHook;

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


    @Override
    protected void startUp() {
        log.info("Xtreme Tasker started");

        registerShutdownSaveHook();
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

        collectionLogService.shutDown();
        unregisterShutdownSaveHook();

        saveActiveState("plugin shutdown");

        overlayManager.remove(overlay);
        overlayManager.remove(panelOverlay);
        overlayManager.remove(taskHudOverlay);
        keyManager.unregisterKeyListener(overlay.getKeyListener());
        mouseManager.unregisterMouseListener(overlay.getMouseAdapter());
        mouseManager.unregisterMouseWheelListener(overlay.getMouseWheelListener());

        currentTask = null;
        currentTaskId = null;

        manualCompletedTaskIds.clear();
        syncedCompletedTaskIds.clear();
        retiredTaskIds.clear();

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

    private void registerShutdownSaveHook() {
        if (shutdownSaveHook != null) {
            return;
        }

        shutdownSaveHook = new Thread(() -> saveActiveState("JVM shutdown hook"), "xtreme-tasker-save");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownSaveHook);
        } catch (IllegalStateException e) {
            log.debug("Could not register shutdown save hook because JVM shutdown is already in progress.", e);
            shutdownSaveHook = null;
        }
    }

    private void unregisterShutdownSaveHook() {
        if (shutdownSaveHook == null) {
            return;
        }

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownSaveHook);
        } catch (IllegalStateException ignored) {
            // JVM shutdown is already in progress; let the hook run.
        }
        shutdownSaveHook = null;
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

        return recentTask;
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
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }

        long hash = client.getAccountHash();
        if (hash == -1L) {
            return null;
        }

        return Long.toUnsignedString(hash);
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

    // ---- Icon position persistence (global, not per-account) ----
    private static final String ICON_X_KEY = "iconX";
    private static final String ICON_Y_KEY = "iconY";

    public void saveIconPosition(int x, int y) {
        configManager.setConfiguration(CONFIG_GROUP, ICON_X_KEY, String.valueOf(x));
        configManager.setConfiguration(CONFIG_GROUP, ICON_Y_KEY, String.valueOf(y));
    }

    public void clearIconPosition() {
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_X_KEY);
        configManager.unsetConfiguration(CONFIG_GROUP, ICON_Y_KEY);
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
            allowCompletionRegressionSave = false;
            return;
        }

        log.trace("Saved state snapshot: currentTaskId={}, manualDone={}, syncedDone={}",
                currentTaskId, manualCompletedTaskIds.size(), syncedCompletedTaskIds.size());

        PersistedState state = buildPersistedState();
        state.setAccountKey(accountKey);
        String json = gson.toJson(state);
        PersistedState parsed = parseAndValidateState(json, "new save");
        if (parsed == null) {
            log.warn("Refusing to save XtremeTasker state because the new snapshot failed validation.");
            return;
        }
        boolean allowCompletionRegression = allowCompletionRegressionSave;
        allowCompletionRegressionSave = false;

        String key = stateConfigKeyForAccount(accountKey);
        String previousJson = configManager.getConfiguration(CONFIG_GROUP, key);
        PersistedState previousState = parseAndValidateState(previousJson, "previous save");
        if (isSuspiciousDataDrop(previousState, parsed)) {
            rotateStateBackups(accountKey, previousJson);
            flushConfigToDisk("suspicious-drop backup");
            log.warn("Refusing to save suspiciously empty XtremeTasker state over existing progress for account {}", accountKey);
            chat("[Xtreme Tasker] Progress save was skipped because it looked like it would wipe existing data. Your previous progress was backed up.");
            return;
        }
        if (!allowCompletionRegression && isSuspiciousCompletionRegression(previousState, parsed)) {
            rotateStateBackups(accountKey, previousJson);
            flushConfigToDisk("suspicious-regression backup");
            log.warn("Refusing to save suspicious XtremeTasker completion regression for account {} (previous={}, next={})",
                    accountKey, completedCount(previousState), completedCount(parsed));
            chat("[Xtreme Tasker] Progress save was skipped because it looked like it would roll back completed tasks. Your previous progress was backed up.");
            return;
        }

        boolean shouldFlushToDisk = previousState == null || isRecoveryRelevantStateChange(previousState, parsed);
        if (shouldFlushToDisk) {
            rotateStateBackups(accountKey, previousJson);
        }
        configManager.setConfiguration(CONFIG_GROUP, key, json);
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

        String json = configManager.getConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey));
        if (json == null || json.trim().isEmpty())
        {
            PersistedState backup = loadNewestValidBackup(accountKey);
            if (backup != null) {
                configManager.setConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey), gson.toJson(backup));
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
                configManager.setConfiguration(CONFIG_GROUP, stateConfigKeyForAccount(accountKey), gson.toJson(state));
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
        state.setSyncMismatchTaskIds(new ArrayList<>(syncMismatchTaskIds));
        state.setSyncMismatchTitle(syncMismatchTitle);
        state.setKnownTaskIds(new HashSet<>(knownTaskIds));
        state.setManualCompletionTimestamps(new HashMap<>(manualCompletionTimestamps));
        state.setSyncedCompletionTimestamps(new HashMap<>(syncedCompletionTimestamps));
        state.setTaskTimeTicksById(new HashMap<>(taskTimeTicksById));
        state.setCollectionLogItemIds(new HashSet<>(collectionLogService.getCachedItemIds()));
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
                && isValidIdCollection(state.getSyncMismatchTaskIds())
                && isValidLongMap(state.getManualCompletionTimestamps())
                && isValidLongMap(state.getSyncedCompletionTimestamps())
                && isValidLongMap(state.getTaskTimeTicksById())
                && isValidPositiveIntegerSet(state.getCollectionLogItemIds())
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

    private boolean isSuspiciousDataDrop(PersistedState previous, PersistedState next) {
        if (previous == null || next == null) {
            return false;
        }

        int previousCompleted = completedCount(previous);
        int nextCompleted = completedCount(next);
        boolean nextHasAnyProgress = nextCompleted > 0
                || hasAnyIds(next.getRetiredTaskIds())
                || (next.getCurrentTaskId() != null && !next.getCurrentTaskId().trim().isEmpty());

        return previousCompleted >= 3 && !nextHasAnyProgress;
    }

    private boolean isSuspiciousCompletionRegression(PersistedState previous, PersistedState next) {
        if (previous == null || next == null) {
            return false;
        }

        Set<String> previousCompleted = completedIdSet(previous);
        Set<String> nextCompleted = completedIdSet(next);
        if (previousCompleted.size() < 10) {
            return false;
        }

        Set<String> missing = new HashSet<>(previousCompleted);
        missing.removeAll(nextCompleted);
        if (missing.size() <= MAX_COMPLETION_REGRESSION_WITHOUT_CURRENT_TASK_CHANGE) {
            return false;
        }

        return Objects.equals(safeTrim(previous.getCurrentTaskId()), safeTrim(next.getCurrentTaskId()));
    }

    private boolean hasAnyIds(Set<String> ids) {
        return ids != null && !ids.isEmpty();
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
                || !Objects.equals(safeTrim(previous.getAccountKey()), safeTrim(next.getAccountKey()))
                || !Objects.equals(safeTrim(previous.getAccountDisplayName()), safeTrim(next.getAccountDisplayName()))
                || previous.getLastSeenPackVersion() != next.getLastSeenPackVersion()
                || previous.getLastKnownTaskCount() != next.getLastKnownTaskCount()
                || !normalizedSet(previous.getManualCompletedTaskIds()).equals(normalizedSet(next.getManualCompletedTaskIds()))
                || !normalizedSet(previous.getSyncedCompletedTaskIds()).equals(normalizedSet(next.getSyncedCompletedTaskIds()))
                || !normalizedSet(previous.getRetiredTaskIds()).equals(normalizedSet(next.getRetiredTaskIds()))
                || !normalizedSet(previous.getKnownTaskIds()).equals(normalizedSet(next.getKnownTaskIds()))
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
    }

    private PersistedState loadNewestValidBackup(String accountKey) {
        for (int i = 1; i <= STATE_BACKUP_COUNT; i++) {
            String backupJson = configManager.getConfiguration(CONFIG_GROUP, stateBackupConfigKeyForAccount(accountKey, i));
            PersistedState backup = parseAndValidateState(backupJson, "backup " + i);
            if (backup != null) {
                log.warn("Recovered XtremeTasker state for account {} from backup {}", accountKey, i);
                return backup;
            }
        }
        return null;
    }

    private void preserveCorruptState(String accountKey, String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, stateCorruptConfigKeyForAccount(accountKey), json);
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
        collectionLogService.restoreCachedItemIds(state.getCollectionLogItemIds());

        currentTaskId = safeTrim(state.getCurrentTaskId());
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
        migrateLegacyLastSyncResultIfNeeded();
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
        currentTask = null;
        currentTaskId = null;
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
        knownTaskIds.clear();
        pendingCollectionLogSyncTicks = -1;
        collectionLogService.resetCachedItemIds();
        clearSyncMismatchReviewState();
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
        currentTask = tasks.stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);

        // If we can't resolve it (pack changed), don't keep saving a dead ID forever
        if (currentTask == null)
        {
            currentTaskId = null;
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

    public Long getTaskTimeTicks(XtremeTask task)
    {
        if (task == null) return null;
        return taskTimeTicksById.get(task.getId());
    }

    // ---------- core actions ----------

    public XtremeTask rollRandomTask() {
        rollSkipNotice = null;
        TaskTier currentTier = getCurrentTier();
        if (currentTier == null) return null;

        XtremeTaskerConfig.RollSourceFilter sourceFilter = config.rollSourceFilter();

        List<XtremeTask> available = tasks.stream()
                .filter(t -> t.getTier() == currentTier)
                .filter(t -> !isTaskCompleted(t))
                .filter(t -> {
                    if (sourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY) {
                        return t.getSource() == TaskSource.COMBAT_ACHIEVEMENT;
                    } else if (sourceFilter == XtremeTaskerConfig.RollSourceFilter.CLOG_ONLY) {
                        return t.getSource() == TaskSource.COLLECTION_LOG;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (!available.isEmpty()) {
            return available.get(random.nextInt(available.size()));
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
                    return nextAvailable.get(random.nextInt(nextAvailable.size()));
                }
            }
        }

        return null;
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
            return task.getSource() == TaskSource.COLLECTION_LOG;
        }
        return true;
    }

    private String buildSourceFilteredRollNotice(
            XtremeTaskerConfig.RollSourceFilter sourceFilter,
            TaskTier currentTier,
            TaskTier rollTier
    ) {
        String sourceLabel = sourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY ? "CAs" : "CLogs";
        String otherLabel = sourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY ? "CLogs" : "CAs";
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

        XtremeTask newTask = rollRandomTask();
        setCurrentTask(newTask);
        rollAnimEndMs = System.currentTimeMillis() + com.amtrollin.xtremetasker.ui.style.UiConstants.ROLL_ANIM_MS;

        currentTaskId = (newTask != null) ? newTask.getId() : null;
        dirty = true;
        persistIfPossible(); // writes immediately if activeAccountKey != null
    }

    public void completeCurrentTaskAndPersist()
    {
        XtremeTask cur = getCurrentTask();
        if (cur == null) return;

        String id = cur.getId();
        manualCompletedTaskIds.add(id);
        manualCompletionTimestamps.put(id, System.currentTimeMillis());
        // If previously synced, remove synced timestamp so manual takes precedence
        syncedCompletionTimestamps.remove(id);
        clientThread.invokeLater(() -> client.playSoundEffect(3925)); // collection log new-entry ding

        // Clear current when done so it won't pin on restart
        currentTask = null;
        currentTaskId = null;

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
            allowCompletionRegressionSave = true;
        }
        else
        {
            manualCompletedTaskIds.add(id);
            manualCompletionTimestamps.put(id, System.currentTimeMillis());
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
        if (desired < existingCompleted)
        {
            allowCompletionRegressionSave = true;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < group.size(); i++)
        {
            XtremeTask groupedTask = group.get(i);
            String id = groupedTask.getId();
            if (id == null || id.trim().isEmpty())
            {
                continue;
            }

            if (i < desired)
            {
                manualCompletedTaskIds.add(id);
                manualCompletionTimestamps.putIfAbsent(id, now);
                syncedCompletionTimestamps.remove(id);
            }
            else
            {
                markTaskIncomplete(id);
            }
        }

        if (currentTask != null && isTaskCompleted(currentTask))
        {
            currentTask = null;
            currentTaskId = null;
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

        // Accumulate in-game ticks for the current task (only while logged in).
        if (currentTaskId != null && client.getGameState() == GameState.LOGGED_IN)
        {
            taskTimeTicksById.merge(currentTaskId, 1L, Long::sum);
            dirty = true;
        }

        if (pendingCollectionLogSyncTicks >= 0)
        {
            pendingCollectionLogSyncTicks--;
            if (pendingCollectionLogSyncTicks <= 0)
            {
                pendingCollectionLogSyncTicks = -1;
                runCollectionLogSyncFromCache();
            }
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

        clientThread.invokeLater(() -> {
            // Re-load mappings if empty (e.g. struct cache was cold at login).
            if (caTaskIdsByName.isEmpty())
            {
                loadCombatAchievementMappings();
            }

            int newlySynced = 0;

            for (XtremeTask task : tasks)
            {
                if (task.getSource() != TaskSource.COMBAT_ACHIEVEMENT)
                {
                    continue;
                }

                if (manualCompletedTaskIds.contains(task.getId()))
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
                    if (syncedCompletedTaskIds.add(task.getId()))
                    {
                        syncedCompletionTimestamps.putIfAbsent(task.getId(), System.currentTimeMillis());
                        newlySynced++;
                    }
                }
            }

            setSyncMismatchTasksForSource(TaskSource.COMBAT_ACHIEVEMENT,
                    findCombatAchievementSyncMismatches());

            if (newlySynced > 0) {
                setSyncResultAndChat(TaskSource.COMBAT_ACHIEVEMENT, "Combat Achievement sync done! " + newlySynced + " new task(s) marked complete based on your CA progress."
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

    private int syncCollectionLogCompletionsFromCache()
    {
        int newlySynced = 0;
        Set<String> processedCountedGroups = new HashSet<>();

        for (XtremeTask task : tasks)
        {
            if (task.getSource() != TaskSource.COLLECTION_LOG)
            {
                continue;
            }

            if (manualCompletedTaskIds.contains(task.getId()))
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
                String groupKey = TaskGroupUtils.key(task);
                if (!processedCountedGroups.add(groupKey))
                {
                    continue;
                }

                List<XtremeTask> group = TaskGroupUtils.groupFor(tasks, task);
                int observedCount = observedCollectionLogSyncCount(task, verification);
                if (observedCount < 0)
                {
                    continue;
                }

                newlySynced += syncCountedCollectionLogGroup(group, observedCount);
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

            if (complete && syncedCompletedTaskIds.add(task.getId()))
            {
                syncedCompletionTimestamps.putIfAbsent(task.getId(), System.currentTimeMillis());
                newlySynced++;
            }
        }

        return newlySynced;
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

    private int syncCountedCollectionLogGroup(
            List<XtremeTask> group,
            int observedCount)
    {
        if (group == null || group.isEmpty())
        {
            return 0;
        }

        int desiredCompleted = desiredCompletedForCountedGroup(group, observedCount);

        int newlySynced = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < desiredCompleted; i++)
        {
            XtremeTask groupedTask = group.get(i);
            String id = groupedTask.getId();
            if (id == null || manualCompletedTaskIds.contains(id) || syncedCompletedTaskIds.contains(id))
            {
                continue;
            }

            syncedCompletedTaskIds.add(id);
            syncedCompletionTimestamps.putIfAbsent(id, now);
            newlySynced++;
        }

        return newlySynced;
    }

    private int desiredCompletedForCountedGroup(List<XtremeTask> group, int observedCount)
    {
        if (group == null || group.isEmpty() || observedCount <= 0)
        {
            return 0;
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

        clientThread.invokeLater(() -> {
            boolean refreshRequested = collectionLogService.requestCollectionLogOpenOrRefresh();
            if (refreshRequested && client.getGameState() == GameState.LOGGED_IN)
            {
                pendingCollectionLogSyncTicks = 4;
                return;
            }

            runCollectionLogSyncFromCache();
        });
    }

    private void runCollectionLogSyncFromCache()
    {
        int newlySynced = syncCollectionLogCompletionsFromCache();
        int capturedItems = collectionLogService.getCapturedItemCount();
        setSyncMismatchTasksForSource(TaskSource.COLLECTION_LOG,
                capturedItems > 0
                        ? findCollectionLogSyncMismatches(true)
                        : Collections.emptyList());

        if (capturedItems == 0)
        {
            setSyncResultAndChat(TaskSource.COLLECTION_LOG, "Collection Log sync done! No items are cached yet this session - open your Collection Log, then sync again."
                    + syncMismatchResultSuffix(TaskSource.COLLECTION_LOG));
        }
        else if (newlySynced > 0)
        {
            setSyncResultAndChat(TaskSource.COLLECTION_LOG, "Collection Log sync done! " + newlySynced + " new task(s) marked complete based on your collection log."
                    + syncMismatchResultSuffix(TaskSource.COLLECTION_LOG));
        }
        else
        {
            setSyncResultAndChat(TaskSource.COLLECTION_LOG, "Collection Log sync done! No new completions found."
                    + syncMismatchResultSuffix(TaskSource.COLLECTION_LOG));
        }

        rebuildTierCounts();
        dirty = true;
        if (activeAccountKey != null)
        {
            saveStateForAccount(activeAccountKey);
            dirty = false;
        }
    }

    private List<XtremeTask> findCollectionLogSyncMismatches(boolean collectionLogCacheAvailable)
    {
        List<XtremeTask> mismatches = new ArrayList<>();
        Set<String> processedCountedGroups = new HashSet<>();

        for (XtremeTask task : tasks)
        {
            if (task.getSource() != TaskSource.COLLECTION_LOG || !isTaskCompleted(task))
            {
                continue;
            }

            TaskVerification verification = task.getVerification();
            if (verification == null)
            {
                mismatches.add(task);
                continue;
            }

            if (isCountedCollectionLogSync(verification))
            {
                if (verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG && !collectionLogCacheAvailable)
                {
                    continue;
                }

                String groupKey = TaskGroupUtils.key(task);
                if (!processedCountedGroups.add(groupKey))
                {
                    continue;
                }

                int observedCount = observedCollectionLogSyncCount(task, verification);
                if (observedCount < 0)
                {
                    addCompletedTasksFromGroup(mismatches, TaskGroupUtils.groupFor(tasks, task));
                    continue;
                }

                List<XtremeTask> group = TaskGroupUtils.groupFor(tasks, task);
                int desiredCompleted = desiredCompletedForCountedGroup(group, observedCount);
                for (int i = desiredCompleted; i < group.size(); i++)
                {
                    XtremeTask groupedTask = group.get(i);
                    if (isTaskCompleted(groupedTask))
                    {
                        mismatches.add(groupedTask);
                    }
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

        for (XtremeTask groupedTask : group)
        {
            if (groupedTask != null && isTaskCompleted(groupedTask))
            {
                mismatches.add(groupedTask);
            }
        }
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
        return pendingCollectionLogSyncTicks >= 0;
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
            return task == null || task.getSource() == source;
        });

        Set<String> seen = new HashSet<>();
        if (mismatches != null)
        {
            for (XtremeTask task : mismatches)
            {
                if (task != null
                        && task.getSource() == source
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
        String label = source == TaskSource.COMBAT_ACHIEVEMENT ? "CA" : "CLOG";
        return " Review " + count + " " + label + " plugin completion(s) not found in game data.";
    }

    @Override
    public List<XtremeTask> getSyncMismatchTasks()
    {
        if (syncMismatchTaskIds.isEmpty())
        {
            return Collections.emptyList();
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
                out.add(task);
            }
        }
        return out;
    }

    @Override
    public List<XtremeTask> getSyncMismatchTasks(TaskSource source)
    {
        if (source == null)
        {
            return getSyncMismatchTasks();
        }

        return getSyncMismatchTasks().stream()
                .filter(task -> task != null && task.getSource() == source)
                .collect(Collectors.toList());
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

        List<XtremeTask> group = TaskGroupUtils.groupFor(tasks, task);
        if (group.size() <= 1)
        {
            return null;
        }

        TaskVerification verification = task.getVerification();
        if (!isCountedCollectionLogSync(verification))
        {
            return null;
        }

        int observedCount = observedCollectionLogSyncCount(task, verification);
        if (observedCount < 0)
        {
            return null;
        }

        int desiredCompleted = desiredCompletedForCountedGroup(group, observedCount);
        return "In game data shows (" + desiredCompleted + "/" + group.size() + ") complete";
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
            return task == null || task.getSource() == source;
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

        allowCompletionRegressionSave = true;
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
                    .filter(t -> t.getSource() == TaskSource.COLLECTION_LOG)
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

            if (capturedItems == 0)
            {
                chat("CLOG debug: 0 items cached. Open the Collection Log in-game to populate."
                        + " Tasks with requirements loaded: clog=" + tasksWithClogReq
                        + " diary=" + tasksWithDiaryReq
                        + " skill=" + tasksWithSkillReq + "/" + totalClogTasks + " total.");
            }
            else
            {
                chat("CLOG debug: " + capturedItems + " item(s) cached: " + cachedIds
                        + ". Tasks with requirements loaded: clog=" + tasksWithClogReq
                        + " diary=" + tasksWithDiaryReq
                        + " skill=" + tasksWithSkillReq + "/" + totalClogTasks + " total.");
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
        return false; // temporarily disabled
    }
}
