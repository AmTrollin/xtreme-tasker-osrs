package com.amtrollin.xtremetasker.models.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistedState {
    private int schemaVersion = 1;
    private long savedAtEpochMillis = 0L;
    private String savedAtLocalTime;
    private String pluginVersion;
    private String accountKey;
    private String accountDisplayName;
    private Set<String> manualCompletedTaskIds = new HashSet<>();
    private Set<String> syncedCompletedTaskIds = new HashSet<>();
    /** Completed task ids no longer present in the bundled task pack. Preserved for history/recovery. */
    private Set<String> retiredTaskIds = new HashSet<>();
    private String currentTaskId;
    private String undoableCompletedTaskId;
    private int skippedTaskCount = 0;
    private String currentTaskCollectionLogBaselineSignature;
    private Integer currentTaskCollectionLogBaselineCount;
    private int lastSeenPackVersion = 0;
    private int lastKnownTaskCount = 0;
    private String lastSyncResult;
    private long lastSyncResultAtEpochMillis = 0L;
    private String lastSyncResultAtLocalTime;
    private String lastCombatAchievementSyncResult;
    private long lastCombatAchievementSyncResultAtEpochMillis = 0L;
    private String lastCombatAchievementSyncResultAtLocalTime;
    private String lastCollectionLogSyncResult;
    private long lastCollectionLogSyncResultAtEpochMillis = 0L;
    private String lastCollectionLogSyncResultAtLocalTime;
    private List<String> syncCompletionCandidateTaskIds = new ArrayList<>();
    private List<String> syncMismatchTaskIds = new ArrayList<>();
    private String syncMismatchTitle;
    /** Task ids present the last time this account loaded the bundled task pack. */
    private Set<String> knownTaskIds = new HashSet<>();
    /** epoch-millis timestamps for tasks marked complete manually. */
    private Map<String, Long> manualCompletionTimestamps = new HashMap<>();
    /** epoch-millis timestamps for tasks marked complete via sync. */
    private Map<String, Long> syncedCompletionTimestamps = new HashMap<>();
    /** accumulated in-game ticks spent on each task while logged in. */
    private Map<String, Long> taskTimeTicksById = new HashMap<>();
    /** accumulated in-game ticks captured at the moment each task was completed. */
    private Map<String, Long> completedTaskTimeTicksById = new HashMap<>();
    /** Collection log item IDs RuneLite has exposed for this account. */
    private Set<Integer> collectionLogItemIds = new HashSet<>();
    /** Per collection-log item acquisition order, used to show applied vs not-yet-applied items chronologically. */
    private Map<Integer, Long> collectionLogItemOrder = new HashMap<>();
}
