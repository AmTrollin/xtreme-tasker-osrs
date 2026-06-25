package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.TaskGroupProgress;
import com.amtrollin.xtremetasker.models.XtremeTask;

import java.awt.image.BufferedImage;
import java.util.List;

public interface TaskerService
{
    boolean isOverlayEnabled();

    boolean hasTaskPackLoaded();

    XtremeTask getCurrentTask();

    TaskTier getCurrentTier();

    int getTierPercent(TaskTier tier);

    String getTierProgressLabel(TaskTier tier);

    List<XtremeTask> getTasks();

    boolean isTaskCompleted(XtremeTask task);

    TaskGroupProgress getTaskGroupProgress(XtremeTask task);

    List<XtremeTask> getTaskGroupInstances(XtremeTask task);

    void toggleTaskGroupProgressAndPersist(XtremeTask task);

    void setTaskGroupCompletedCountAndPersist(XtremeTask task, int completedCount);

    CompletionInfo getCompletionInfo(XtremeTask task);

    XtremeTask getMostRecentCompletedTask();

    Long getTaskTimeTicks(XtremeTask task);

    void toggleTaskCompletedAndPersist(XtremeTask task);

    void completeCurrentTaskAndPersist();

    boolean canUndoRecentTaskCompletion();

    void undoCurrentTaskCompletionAndPersist();

    void rollRandomTaskAndPersist();

    void reloadTaskPack();

    void syncAccountProgressAndPersist();

    void syncCollectionLogTaskAndPersist(XtremeTask task);

    String getLastSyncResult();

    String getLastSyncResultAtLocalTime();

    String getLastCombatAchievementSyncResult();

    String getLastCombatAchievementSyncResultAtLocalTime();

    String getLastCollectionLogSyncResult();

    String getLastCollectionLogSyncResultAtLocalTime();

    boolean isCollectionLogSyncPending();

    List<XtremeTask> getSyncCompletionCandidateTasks(TaskSource source);

    void dismissSyncCompletionCandidateReview(TaskSource source);

    void markSyncCompletionCandidateTasksCompleteAndPersist(List<XtremeTask> tasks);

    List<XtremeTask> getSyncMismatchTasks();

    List<XtremeTask> getSyncMismatchTasks(TaskSource source);

    String getSyncMismatchTitle();

    String getSyncMismatchGameProgressLabel(XtremeTask task);

    void dismissSyncMismatchReview();

    void dismissSyncMismatchReview(TaskSource source);

    void markSyncMismatchTaskIncompleteAndPersist(XtremeTask task);

    void markSyncMismatchTasksIncompleteAndPersist(List<XtremeTask> tasks);

    void markAllSyncMismatchTasksIncompleteAndPersist();

    List<PrerequisiteStatus> getPrerequisiteStatuses(XtremeTask task);

    boolean isNewTask(XtremeTask task);

    boolean isTaskGroupNew(XtremeTask task);

    boolean hasNewTasks();

    void pushGameMessage(String msg);

    boolean condenseRepeatedTasks();

    void toggleCondenseRepeatedTasks();

    boolean skipSingleIncompleteConfirmation();

    void setSkipSingleIncompleteConfirmation(boolean skip);

    XtremeTaskerConfig.RollSourceFilter getRollSourceFilter();

    String getRollSkipNotice();

    boolean showTips();

    String getItemName(int itemId);

    String getCollectionLogSequenceStepLabel(XtremeTask task);

    boolean isCollectionLogItemObtained(int itemId);

    long getCollectionLogItemObtainedOrder(int itemId);

    void refreshTaskSyncMismatchForTask(XtremeTask task);

    boolean isTaskSyncMismatch(XtremeTask task);

    boolean isCollectionLogTaskSyncMismatch(XtremeTask task);

    void dismissTaskSyncMismatchAndPersist(XtremeTask task);

    void dismissCollectionLogTaskSyncMismatchAndPersist(XtremeTask task);

    BufferedImage getItemImage(int itemId);
}
