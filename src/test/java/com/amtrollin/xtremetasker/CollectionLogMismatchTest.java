package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.amtrollin.xtremetasker.verification.CombatAchievementService;
import com.google.gson.Gson;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CollectionLogMismatchTest
{
    @Test
    public void collectionLogRequirementNotFoundBySyncIsMarkedAsMismatch() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        TaskVerification verification = new Gson().fromJson(
                "{\"method\":\"collection-log\",\"itemIds\":[10878],\"count\":1}",
                TaskVerification.class
        );

        XtremeTask task = new XtremeTask(
                "collection_log_easy_get-a-green-satchel_001_test",
                "Get a Green satchel",
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY,
                null,
                null,
                null,
                null,
                null,
                verification,
                null
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(task);

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(task.getId());

        collectionLogService.storeSeenItem(10878);
        collectionLogService.markSyncSeen();
        List<XtremeTask> mismatchesWhenNotFound = plugin.findCollectionLogSyncMismatches(true);
        assertEquals("Manually completed CLOG requirement not found by sync should mismatch", 1, mismatchesWhenNotFound.size());
        assertEquals(task.getId(), mismatchesWhenNotFound.get(0).getId());

        collectionLogService.storeItem(10878);
        List<XtremeTask> mismatchesWhenObtained = plugin.findCollectionLogSyncMismatches(true);
        assertTrue("Obtained requirement should no longer mismatch", mismatchesWhenObtained.isEmpty());
    }

    @Test
    public void syncedOnlyCollectionLogCompletionIsAutoRepairedWhenLaterSyncDisprovesIt() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_synced_repair_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        plugin.tasksForTesting().add(task);
        plugin.syncedCompletedTaskIdsForTesting().add(task.getId());

        collectionLogService.storeSeenItem(10878);
        collectionLogService.markSyncSeen();

        assertTrue("Synced-only false positive should be removed when later sync disproves it",
                !plugin.isTaskCompleted(task));
        assertTrue("Auto-repaired synced-only false positive should not require mismatch review",
                plugin.getSyncMismatchTasks(TaskSource.COLLECTION_LOG).isEmpty());
    }

    @Test
    public void manuallyCompletedCollectionLogTaskIsNotMarkedMismatchBeforeSyncEvidence() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_manual_no_sync_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(task);

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(task.getId());

        List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
        assertTrue("Manual CLOG completion should not mismatch until a sync has seen the relevant log slot",
                mismatches.isEmpty());
    }

    @Test
    public void quietCollectionLogOpenStoresMismatchForCompletedMissingClog() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_quiet_sync_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        plugin.manualCompletedTaskIdsForTesting().clear();
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());

        assertTrue("Completing manually alone should not show the CLOG mismatch helper",
                !plugin.isCollectionLogTaskSyncMismatch(task));

        collectionLogService.storeSeenItem(10878);
        collectionLogService.markSyncSeen();

        assertTrue("Opening CLOG and seeing the missing slot should show the CLOG mismatch helper",
                plugin.isCollectionLogTaskSyncMismatch(task));
        assertEquals(List.of(task.getId()), plugin.syncMismatchTaskIdsForTesting());
    }

    @Test
    public void fullCollectionLogSyncStoresMismatchWithoutOpeningSpecificPage() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_full_sync_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        plugin.manualCompletedTaskIdsForTesting().clear();
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());

        assertTrue("Completing manually alone should not show the CLOG mismatch helper",
                !plugin.isCollectionLogTaskSyncMismatch(task));

        collectionLogService.markFullSyncSeen();

        assertTrue("A full CLOG sync should prove a manually completed missing CLOG is mismatched",
                plugin.isCollectionLogTaskSyncMismatch(task));
        assertEquals(List.of(task.getId()), plugin.syncMismatchTaskIdsForTesting());
    }

    @Test
    public void oldCollectionLogSyncDoesNotMismatchTaskCompletedAfterSync() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_old_sync_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);

        collectionLogService.markFullSyncSeen();
        Thread.sleep(5L);
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());
        plugin.manualCompletionTimestampsForTesting().put(task.getId(), System.currentTimeMillis());

        assertTrue("A sync that happened before manual completion should not show the CLOG mismatch helper",
                plugin.findCollectionLogSyncMismatches(true).isEmpty());
        assertTrue("Task details should not inherit stale pre-completion CLOG sync evidence",
                !plugin.isCollectionLogTaskSyncMismatch(task));

        Thread.sleep(5L);
        collectionLogService.markFullSyncSeen();

        assertTrue("A new CLOG sync after manual completion should show the CLOG mismatch helper",
                plugin.isCollectionLogTaskSyncMismatch(task));
        assertEquals(List.of(task.getId()), plugin.syncMismatchTaskIdsForTesting());
    }

    @Test
    public void collectionLogCompletionCandidateSurvivesCaAdSyncRefresh() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_candidate_preserve_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);

        collectionLogService.storeItem(10878);
        assertEquals("Opening CLOG should populate CLOG completion candidates",
                List.of(task.getId()),
                taskIds(plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG)));

        invokeRefreshCollectionLogNonItemSyncState(plugin);

        assertEquals("CA/AD sync refresh should not clear CLOG candidates captured from opening CLOG",
                List.of(task.getId()),
                taskIds(plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG)));
    }

    @Test
    public void collectionLogMismatchSyncEvidencePersistsAcrossSessions() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_persisted_sync_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());
        plugin.manualCompletionTimestampsForTesting().put(task.getId(), System.currentTimeMillis() - 1_000L);
        collectionLogService.markFullSyncSeen();
        assertTrue(plugin.isCollectionLogTaskSyncMismatch(task));

        Method buildState = XtremeTaskerPlugin.class.getDeclaredMethod("buildPersistedState");
        buildState.setAccessible(true);
        PersistedState state = (PersistedState) buildState.invoke(plugin);

        XtremeTaskerPlugin restoredPlugin = new XtremeTaskerPlugin();
        CollectionLogService restoredCollectionLogService = new CollectionLogService();
        restoredPlugin.setCollectionLogServiceForTesting(restoredCollectionLogService);
        restoredPlugin.tasksForTesting().clear();
        restoredPlugin.tasksForTesting().add(task);

        Method applyState = XtremeTaskerPlugin.class.getDeclaredMethod("applyPersistedState", PersistedState.class);
        applyState.setAccessible(true);
        applyState.invoke(restoredPlugin, state);

        assertTrue("Persisted CLOG sync evidence should keep the mismatch helper visible after login",
                restoredPlugin.isCollectionLogTaskSyncMismatch(task));
        assertEquals(List.of(task.getId()), restoredPlugin.syncMismatchTaskIdsForTesting());
    }

    @Test
    public void pendingAmbiguousCollectionLogDropCountsPersistUntilFullSync() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);
        collectionLogService.restorePendingDropCounts(2, 1);

        Method buildState = XtremeTaskerPlugin.class.getDeclaredMethod("buildPersistedState");
        buildState.setAccessible(true);
        PersistedState state = (PersistedState) buildState.invoke(plugin);

        XtremeTaskerPlugin restoredPlugin = new XtremeTaskerPlugin();
        CollectionLogService restoredCollectionLogService = new CollectionLogService();
        restoredPlugin.setCollectionLogServiceForTesting(restoredCollectionLogService);

        Method applyState = XtremeTaskerPlugin.class.getDeclaredMethod("applyPersistedState", PersistedState.class);
        applyState.setAccessible(true);
        applyState.invoke(restoredPlugin, state);

        assertEquals("Pending Ancient page drops should survive restore until CLOG sync",
                2,
                restoredPlugin.getPendingAncientPageDropCountSinceLastSync());
        assertEquals("Pending Medallion fragment drops should survive restore until CLOG sync",
                1,
                restoredPlugin.getPendingMedallionFragmentDropCountSinceLastSync());

        restoredCollectionLogService.markFullSyncSeen();

        assertEquals("Full CLOG sync should clear restored pending Ancient page drops",
                0,
                restoredPlugin.getPendingAncientPageDropCountSinceLastSync());
        assertEquals("Full CLOG sync should clear restored pending Medallion fragment drops",
                0,
                restoredPlugin.getPendingMedallionFragmentDropCountSinceLastSync());
    }

    @Test
    public void combatAchievementMismatchRequiresSyncAfterCompletion() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());
        plugin.setCombatAchievementServiceForTesting(new StubCombatAchievementService(false));

        XtremeTask task = combatAchievementTask(
                "combat_achievement_easy_missing_after_sync_test",
                "Missing CA after sync",
                7
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);

        invokeRefreshCombatAchievementSyncState(plugin);
        Thread.sleep(5L);
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());
        plugin.manualCompletionTimestampsForTesting().put(task.getId(), System.currentTimeMillis());

        assertTrue("A CA sync before completion should not prove the completed task is mismatched",
                plugin.syncMismatchTaskIdsForTesting().isEmpty());
        assertTrue("Old CA sync evidence should be ignored on task details",
                !plugin.isTaskSyncMismatch(task));

        Thread.sleep(5L);
        invokeRefreshCombatAchievementSyncState(plugin);

        assertEquals("A CA sync after completion should show the mismatch helper when the CA is not complete in-game",
                List.of(task.getId()), plugin.syncMismatchTaskIdsForTesting());
        assertTrue(plugin.isTaskSyncMismatch(task));
    }

    @Test
    public void combatAchievementMismatchSyncEvidencePersistsAcrossSessions() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());
        plugin.setCombatAchievementServiceForTesting(new StubCombatAchievementService(false));

        XtremeTask task = combatAchievementTask(
                "combat_achievement_easy_persisted_mismatch_test",
                "Persisted CA mismatch",
                8
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());
        plugin.manualCompletionTimestampsForTesting().put(task.getId(), System.currentTimeMillis() - 1_000L);

        invokeRefreshCombatAchievementSyncState(plugin);
        assertEquals(List.of(task.getId()), plugin.syncMismatchTaskIdsForTesting());

        Method buildState = XtremeTaskerPlugin.class.getDeclaredMethod("buildPersistedState");
        buildState.setAccessible(true);
        PersistedState state = (PersistedState) buildState.invoke(plugin);

        XtremeTaskerPlugin restoredPlugin = new XtremeTaskerPlugin();
        restoredPlugin.setCollectionLogServiceForTesting(new CollectionLogService());
        restoredPlugin.setCombatAchievementServiceForTesting(new StubCombatAchievementService(false));
        restoredPlugin.tasksForTesting().clear();
        restoredPlugin.tasksForTesting().add(task);

        Method applyState = XtremeTaskerPlugin.class.getDeclaredMethod("applyPersistedState", PersistedState.class);
        applyState.setAccessible(true);
        applyState.invoke(restoredPlugin, state);

        assertEquals("Persisted CA sync evidence should keep the mismatch helper visible after login",
                List.of(task.getId()), restoredPlugin.syncMismatchTaskIdsForTesting());
        assertTrue(restoredPlugin.isTaskSyncMismatch(task));
    }

    @Test
    public void staleCombatAchievementMismatchWithoutSyncEvidenceIsPruned() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());
        plugin.setCombatAchievementServiceForTesting(new StubCombatAchievementService(false));

        XtremeTask task = combatAchievementTask(
                "combat_achievement_easy_stale_mismatch_test",
                "Stale CA mismatch",
                9
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());
        plugin.manualCompletionTimestampsForTesting().put(task.getId(), System.currentTimeMillis());
        plugin.syncMismatchTaskIdsForTesting().add(task.getId());

        assertTrue("Old bogus CA mismatch rows should clear when there is no post-completion CA sync evidence",
                plugin.getSyncMismatchTasks(TaskSource.COMBAT_ACHIEVEMENT).isEmpty());
        assertTrue(plugin.syncMismatchTaskIdsForTesting().isEmpty());
    }

    @Test
    public void obtainedCollectionLogItemClearsMismatchAndStaysObtained() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-right-skull-half_001_obtained_test",
                "Get a Right skull half",
                TaskTier.EASY,
                new int[]{9007},
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        plugin.manualCompletedTaskIdsForTesting().clear();
        plugin.manualCompletedTaskIdsForTesting().add(task.getId());

        collectionLogService.storeSeenItem(9007);
        collectionLogService.markSyncSeen();
        assertTrue("Missing right skull half should mismatch after sync sees the slot",
                plugin.isCollectionLogTaskSyncMismatch(task));

        collectionLogService.storeItem(9007);

        assertTrue("Obtained right skull half should be cached permanently",
                collectionLogService.isItemObtained(9007));
        assertTrue("Obtained right skull half should clear the CLOG mismatch helper",
                !plugin.isCollectionLogTaskSyncMismatch(task));
    }

    @Test
    public void oneCompletedRepeatedForestryUniqueIsNotMarkedAsSyncMismatch() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        int[] forestryItemIds = new int[]{28138, 28140, 28146, 28166, 28169, 28171};
        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        for (int i = 1; i <= 6; i++)
        {
            tasks.add(collectionLogTask(
                    "collection_log_easy_get-1-unique-from-forestry_00" + i + "_test",
                    "Get 1 unique from Forestry",
                    TaskTier.EASY,
                    forestryItemIds,
                    i
            ));
        }

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(tasks.get(0).getId());

        collectionLogService.storeItem(28138);

        List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
        assertTrue("One completed Forestry task should be satisfied by one obtained Forestry unique",
                mismatches.isEmpty());
    }

    @Test
    public void staleRepeatedForestryMismatchIsPrunedAfterCacheCatchesUp() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        int[] forestryItemIds = new int[]{28138, 28140, 28146, 28166, 28169, 28171};
        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        for (int i = 1; i <= 6; i++)
        {
            tasks.add(collectionLogTask(
                    "collection_log_easy_get-1-unique-from-forestry_00" + i + "_test",
                    "Get 1 unique from Forestry",
                    TaskTier.EASY,
                    forestryItemIds,
                    i
            ));
        }

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(tasks.get(0).getId());

        List<String> syncMismatchTaskIds = plugin.syncMismatchTaskIdsForTesting();
        syncMismatchTaskIds.clear();
        syncMismatchTaskIds.add(tasks.get(0).getId());
        plugin.setSyncMismatchTitleForTesting("Review completed tasks");

        collectionLogService.storeItem(28138);

        assertTrue("Stale Forestry review row should disappear once live cache satisfies 1/6",
                plugin.getSyncMismatchTasks(TaskSource.COLLECTION_LOG).isEmpty());
        assertEquals("", plugin.syncMismatchTitleForTesting());
    }

    @Test
    public void cacheCleanupDoesNotRecheckDiaryBackedCollectionLogReviewRows() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask diaryTask = achievementDiaryTask(
                "collection_log_easy_complete-the-ardougne-easy-diary_001_test",
                "Complete the Ardougne easy diary"
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(diaryTask);

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(diaryTask.getId());

        List<String> syncMismatchTaskIds = plugin.syncMismatchTaskIdsForTesting();
        syncMismatchTaskIds.clear();
        syncMismatchTaskIds.add(diaryTask.getId());
        plugin.setSyncMismatchTitleForTesting("Review completed tasks");

        collectionLogService.storeItem(28138);

        List<XtremeTask> mismatches = plugin.getSyncMismatchTasks(TaskSource.COLLECTION_LOG);
        assertEquals("Diary-backed review row should be left for client-thread sync, not cache cleanup",
                1, mismatches.size());
        assertEquals(diaryTask.getId(), mismatches.get(0).getId());
    }

    @Test
    public void repeatedCollectionLogMismatchKeepsDisplayedGroupOrder() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        int[] forestryItemIds = new int[]{28138, 28140, 28146, 28166, 28169, 28171};
        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        for (int i = 1; i <= 6; i++)
        {
            tasks.add(collectionLogTask(
                    "collection_log_easy_get-1-unique-from-forestry_00" + i + "_test",
                    "Get 1 unique from Forestry",
                    TaskTier.EASY,
                    forestryItemIds,
                    i
            ));
        }

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(tasks.get(0).getId());
        manualCompletedTaskIds.add(tasks.get(1).getId());

        Map<String, Long> manualCompletionTimestamps = plugin.manualCompletionTimestampsForTesting();
        manualCompletionTimestamps.clear();
        manualCompletionTimestamps.put(tasks.get(0).getId(), 200L);
        manualCompletionTimestamps.put(tasks.get(1).getId(), 100L);

        for (int itemId : forestryItemIds)
        {
            collectionLogService.storeSeenItem(itemId);
        }
        collectionLogService.markSyncSeen();
        collectionLogService.storeItem(28138);

        List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
        assertEquals("Only the second displayed Forestry completion should be reviewable", 1, mismatches.size());
        assertEquals(tasks.get(1).getId(), mismatches.get(0).getId());
    }

    @Test
    public void satchelInterfaceItemsCountForSatchelRequirements() throws Exception
    {
        int[][] satchelIds = new int[][]{
                {10877, 25618},
                {10878, 25619},
                {10879, 25620},
                {10880, 25621},
                {10881, 25622},
                {10882, 25623}
        };

        for (int[] ids : satchelIds)
        {
            XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
            CollectionLogService collectionLogService = new CollectionLogService();
            plugin.setCollectionLogServiceForTesting(collectionLogService);

            int realItemId = ids[0];
            int interfaceItemId = ids[1];
            TaskVerification verification = new Gson().fromJson(
                    "{\"method\":\"collection-log\",\"itemIds\":[" + realItemId + "],\"count\":1}",
                    TaskVerification.class
            );

            XtremeTask task = new XtremeTask(
                    "collection_log_easy_get-a-satchel_" + realItemId + "_test",
                    "Get a satchel",
                    TaskSource.COLLECTION_LOG,
                    TaskTier.EASY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    verification,
                    null
            );

            List<XtremeTask> tasks = plugin.tasksForTesting();
            tasks.clear();
            tasks.add(task);

            Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
            manualCompletedTaskIds.clear();
            manualCompletedTaskIds.add(task.getId());

            collectionLogService.storeSeenItem(interfaceItemId);
            collectionLogService.storeItem(interfaceItemId);

            List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
            assertTrue("Satchel interface item " + interfaceItemId + " should satisfy satchel requirement " + realItemId,
                    mismatches.isEmpty());
        }
    }

    @Test
    public void olmletVariantsCountAsOneOlmletLogSlot()
    {
        int[] olmletVariantIds = new int[]{
                24656,
                24658,
                20851,
                22376,
                22378,
                22380,
                22382,
                22384
        };

        for (int olmletVariantId : olmletVariantIds)
        {
            CollectionLogService collectionLogService = new CollectionLogService();
            collectionLogService.storeSeenItem(olmletVariantId);
            collectionLogService.storeItem(olmletVariantId);

            assertTrue("Olmlet variant " + olmletVariantId + " should satisfy Olmlet",
                    collectionLogService.hasSeenAll(new int[]{20851}));
            assertEquals("Olmlet variant " + olmletVariantId + " should count as one Olmlet slot",
                    1, collectionLogService.countObtained(new int[]{20851}));
        }

        CollectionLogService collectionLogService = new CollectionLogService();
        for (int olmletVariantId : olmletVariantIds)
        {
            collectionLogService.storeItem(olmletVariantId);
        }

        assertEquals("Multiple Olmlet variants should still count as one Olmlet slot",
                1, collectionLogService.countObtained(new int[]{20851}));
    }

    @Test
    public void lilZikVariantsCountAsOneLilZikLogSlot()
    {
        int[] lilZikVariantIds = new int[]{
                25749,
                25748,
                25750,
                25751,
                25752,
                22473
        };

        for (int lilZikVariantId : lilZikVariantIds)
        {
            CollectionLogService collectionLogService = new CollectionLogService();
            collectionLogService.storeSeenItem(lilZikVariantId);
            collectionLogService.storeItem(lilZikVariantId);

            assertTrue("Lil' zik variant " + lilZikVariantId + " should satisfy Lil' zik",
                    collectionLogService.hasSeenAll(new int[]{22473}));
            assertEquals("Lil' zik variant " + lilZikVariantId + " should count as one Lil' zik slot",
                    1, collectionLogService.countObtained(new int[]{22473}));
        }

        CollectionLogService collectionLogService = new CollectionLogService();
        for (int lilZikVariantId : lilZikVariantIds)
        {
            collectionLogService.storeItem(lilZikVariantId);
        }

        assertEquals("Multiple Lil' zik variants should still count as one Lil' zik slot",
                1, collectionLogService.countObtained(new int[]{22473}));
    }

    @Test
    public void chargedAndUnchargedRaidItemsCountAsOneLogSlot()
    {
        int[][] chargedItemPairs = new int[][]{
                {22323, 22481}, // Sanguinesti staff
                {22325, 22486}, // Scythe of vitur
                {28547, 28549}  // Tumeken's shadow
        };

        for (int[] chargedItemPair : chargedItemPairs)
        {
            int canonicalItemId = chargedItemPair[0];
            int alternateItemId = chargedItemPair[1];

            CollectionLogService collectionLogService = new CollectionLogService();
            collectionLogService.storeSeenItem(alternateItemId);
            collectionLogService.storeItem(alternateItemId);

            assertTrue("Alternate item " + alternateItemId + " should satisfy canonical item " + canonicalItemId,
                    collectionLogService.hasSeenAll(new int[]{canonicalItemId}));
            assertEquals("Alternate item " + alternateItemId + " should count as one canonical slot",
                    1, collectionLogService.countObtained(new int[]{canonicalItemId}));
            assertEquals("Charged and uncharged aliases in one requirement should not double-count",
                    1, collectionLogService.countObtained(new int[]{canonicalItemId, alternateItemId}));
            assertTrue("Charged and uncharged aliases in one requirement should be satisfied by either form",
                    collectionLogService.hasSeenAll(new int[]{canonicalItemId, alternateItemId}));
        }
    }

    @Test
    public void chargedTomeOfWaterDoesNotCountAsEmptyTomeLogSlot()
    {
        CollectionLogService collectionLogService = new CollectionLogService();
        collectionLogService.storeSeenItem(25574);
        collectionLogService.storeItem(25574);

        assertTrue("Charged Tome of water should not satisfy the empty Tome of water collection-log slot",
                !collectionLogService.hasSeenAll(new int[]{25576}));
        assertEquals("Charged Tome of water should not count as the empty Tome of water slot",
                0, collectionLogService.countObtained(new int[]{25576}));
    }

    @Test
    public void collectionLogZeroQuantityClearsStaleTomeOfWaterObtainedState()
    {
        CollectionLogService collectionLogService = new CollectionLogService();
        collectionLogService.storeItem(25576);

        assertEquals("Precondition: Tome of water starts cached as obtained",
                1, collectionLogService.countObtained(new int[]{25576}));

        collectionLogService.storeUnobtainedItem(25576);

        assertTrue("Explicit zero-quantity sync should keep Tome of water seen",
                collectionLogService.hasSeenAll(new int[]{25576}));
        assertEquals("Explicit zero-quantity sync should clear stale Tome of water obtained state",
                0, collectionLogService.countObtained(new int[]{25576}));
    }

    @Test
    public void tumekensGuardianVariantsCountAsOneGuardianLogSlot()
    {
        int[] guardianVariantIds = new int[]{
                27382,
                27383,
                27387,
                27354,
                27384,
                27386,
                27352,
                27385
        };

        for (int guardianVariantId : guardianVariantIds)
        {
            CollectionLogService collectionLogService = new CollectionLogService();
            collectionLogService.storeSeenItem(guardianVariantId);
            collectionLogService.storeItem(guardianVariantId);

            assertTrue("Tumeken's guardian variant " + guardianVariantId + " should satisfy Tumeken's guardian",
                    collectionLogService.hasSeenAll(new int[]{27352}));
            assertEquals("Tumeken's guardian variant " + guardianVariantId + " should count as one guardian slot",
                    1, collectionLogService.countObtained(new int[]{27352}));
        }

        CollectionLogService collectionLogService = new CollectionLogService();
        for (int guardianVariantId : guardianVariantIds)
        {
            collectionLogService.storeItem(guardianVariantId);
        }

        assertEquals("Multiple Tumeken's guardian variants should still count as one guardian slot",
                1, collectionLogService.countObtained(new int[]{27352}));
    }

    @Test
    public void restoredCollectionLogCacheMarksItemsAsSeenAgain()
    {
        CollectionLogService collectionLogService = new CollectionLogService();

        assertTrue("Fresh cache should not report unseen items as seen",
                !collectionLogService.hasSeenAll(new int[]{10878}));

        collectionLogService.restoreCachedItemIds(Collections.singleton(10878));

        assertTrue("Restored cache should mark persisted item ids as seen",
                collectionLogService.hasSeenAll(new int[]{10878}));
    }

    @Test
    public void collectionLogItemAcquisitionOrderIsPreservedAcrossRestore()
    {
        CollectionLogService collectionLogService = new CollectionLogService();
        collectionLogService.storeItem(23285);
        collectionLogService.storeItem(23291);

        Map<Integer, Long> order = new HashMap<>(collectionLogService.getCachedItemOrder());
        Set<Integer> itemIds = new HashSet<>(collectionLogService.getCachedItemIds());

        CollectionLogService restored = new CollectionLogService();
        restored.restoreCachedItemState(itemIds, order);
        restored.storeItem(23288);

        assertTrue("First restored item should keep the earliest order",
                restored.getObtainedItemOrder(23285) < restored.getObtainedItemOrder(23291));
        assertTrue("Newly captured items should sort after restored items",
                restored.getObtainedItemOrder(23291) < restored.getObtainedItemOrder(23288));
    }

    @Test
    public void repeatedCountedCollectionLogCompletionNotFoundBySyncIsMarkedAsMismatch() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask first = countedCollectionLogTask(
                "collection_log_easy_get-the-next-tier-of-metal-boots_001_test",
                "Get the next tier of metal boots",
                1
        );
        XtremeTask second = countedCollectionLogTask(
                "collection_log_easy_get-the-next-tier-of-metal-boots_002_test",
                "Get the next tier of metal boots",
                2
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(first);
        tasks.add(second);

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(first.getId());

        for (int itemId : new int[]{4119, 4121, 4123, 4125, 4127, 4129, 4131})
        {
            collectionLogService.storeSeenItem(itemId);
        }
        collectionLogService.markSyncSeen();
        List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
        assertEquals("Repeated counted CLOG completion not found by sync should be reviewable", 1, mismatches.size());
        assertEquals(first.getId(), mismatches.get(0).getId());
    }

    @Test
    public void currentRepeatedCollectionLogTaskCanCompleteFromExistingCache() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        int[] forestryItemIds = new int[]{28138, 28140, 28146, 28166, 28169, 28171};
        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        for (int i = 1; i <= 6; i++)
        {
            tasks.add(collectionLogTask(
                    "collection_log_easy_get-1-unique-from-forestry_00" + i + "_test",
                    "Get 1 unique from Forestry",
                    TaskTier.EASY,
                    forestryItemIds,
                    i
            ));
        }

        collectionLogService.storeItem(28138);
        plugin.setCurrentTaskForTesting(tasks.get(0));

        assertTrue("Current CLOG task should be markable complete when its required item is already cached",
                plugin.isCurrentTaskCompletionCriteriaMet());
    }

    @Test
    public void currentRepeatedCollectionLogTaskHighlightsAfterNewCachedDrop() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        int[] forestryItemIds = new int[]{28138, 28140, 28146, 28166, 28169, 28171};
        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        for (int i = 1; i <= 6; i++)
        {
            tasks.add(collectionLogTask(
                    "collection_log_easy_get-1-unique-from-forestry_00" + i + "_test",
                    "Get 1 unique from Forestry",
                    TaskTier.EASY,
                    forestryItemIds,
                    i
            ));
        }

        plugin.setCurrentTaskForTesting(tasks.get(0));
        assertTrue("Current CLOG task should not be markable complete before the item is cached",
                !plugin.isCurrentTaskCompletionCriteriaMet());

        collectionLogService.storeItem(28138);

        assertTrue("Current CLOG task should highlight once its required item is cached",
                plugin.isCurrentTaskCompletionCriteriaMet());
    }

    @Test
    public void currentCollectionLogCompletionDoesNotRequirePrerequisites() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = new XtremeTask(
                "collection_log_easy_get-a-green-satchel_001_prereq_test",
                "Get a Green satchel",
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY,
                null,
                null,
                null,
                "99 Agility",
                null,
                new Gson().fromJson(
                        "{\"method\":\"collection-log\",\"itemIds\":[10878],\"count\":1}",
                        TaskVerification.class),
                null
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        collectionLogService.storeItem(10878);
        plugin.setCurrentTaskForTesting(task);

        assertTrue("Current CLOG completion highlight should match cache evidence even when prereqs are unmet",
                plugin.isCurrentTaskCompletionCriteriaMet());
    }

    @Test
    public void currentCollectionLogTaskAlreadyCompleteAtRollUsesCompletedBeforeRolledTime() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-1-unique-from-forestry_001_cbr_test",
                "Get 1 unique from Forestry",
                TaskTier.EASY,
                new int[]{28138, 28140, 28146, 28166, 28169, 28171},
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(task);
        collectionLogService.storeItem(28138);

        plugin.setCurrentTaskForTesting(task);

        assertTrue("Current CLOG task should still be markable complete",
                plugin.isCurrentTaskCompletionCriteriaMet());
        assertEquals("Completed-before-rolled task should use CBR time sentinel",
                Long.valueOf(-1L),
                plugin.getTaskTimeTicks(task));

        plugin.toggleTaskCompletedAndPersist(task);

        assertTrue("CBR task should mark complete normally", plugin.isTaskCompleted(task));
        assertEquals("CBR time marker should survive manual completion",
                Long.valueOf(-1L),
                plugin.getTaskTimeTicks(task));
    }

    @Test
    public void currentDisplaySequenceTaskRequiresItsExactSequenceItem() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask bronzeBootsTask = countedCollectionLogTask(
                "collection_log_easy_get-the-next-tier-of-metal-boots_001_exact_item_test",
                "Get the next tier of metal boots",
                1
        );

        plugin.tasksForTesting().clear();
        plugin.tasksForTesting().add(bronzeBootsTask);

        collectionLogService.storeItem(4121);
        plugin.setCurrentTaskForTesting(bronzeBootsTask);

        assertTrue("Obtaining a later sequence item should not complete the current earlier step",
                !plugin.isCurrentTaskCompletionCriteriaMet());
        assertEquals("Task should keep normal time tracking when its exact sequence item is missing",
                null,
                plugin.getTaskTimeTicks(bronzeBootsTask));

        collectionLogService.storeItem(4119);

        assertTrue("Exact sequence item should complete the current step",
                plugin.isCurrentTaskCompletionCriteriaMet());
    }

    @Test
    public void currentSailingSequenceTasksUseExactStepIconAndLabel() throws Exception
    {
        assertSequenceDecoration(
                "collection_log_easy_get-the-next-reward-from-the-tempor-tant_002_test",
                "Get the next reward from The Tempor Tantrum",
                TaskTier.EASY,
                new int[]{31732, 31733, 31734},
                2,
                31733,
                "Barrel stand"
        );
        assertSequenceDecoration(
                "collection_log_medium_get-the-next-reward-from-the-jubbly-jive_003_test",
                "Get the next reward from The Jubbly Jive",
                TaskTier.MEDIUM,
                new int[]{31744, 31745, 31746},
                3,
                31746,
                "Gurtob's fabric roll"
        );
        assertSequenceDecoration(
                "collection_log_hard_get-the-next-reward-from-the-gwyneth-gli_001_test",
                "Get the next reward from The Gwenith Glide",
                TaskTier.HARD,
                new int[]{31756, 31757, 31758},
                1,
                31756,
                "Serrated key"
        );
    }

    @Test
    public void currentSingleCollectionLogTaskAlreadyCompleteAtRollUsesExistingCacheWithoutSync() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-right-skull-half_001_cbr_test",
                "Get a Right skull half",
                TaskTier.EASY,
                new int[]{9007},
                1
        );

        collectionLogService.storeItem(9007);
        plugin.setCurrentTaskForTesting(task);

        assertTrue("Current single-item CLOG task should use existing cache without waiting for sync",
                plugin.isCurrentTaskCompletionCriteriaMet());
        assertEquals("Already obtained CLOG task should be CBR immediately on roll",
                Long.valueOf(-1L),
                plugin.getTaskTimeTicks(task));
    }

    @Test
    public void currentCollectionLogGetterRecoversReadyStateFromExistingCache() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-right-skull-half_001_getter_test",
                "Get a Right skull half",
                TaskTier.EASY,
                new int[]{9007},
                1
        );

        plugin.setCurrentTaskForTesting(task);
        assertTrue("Task should start unready before CLOG cache has the item",
                !plugin.isCurrentTaskCompletionCriteriaMet());

        collectionLogService.storeItem(9007);

        assertTrue("Current task readiness getter should reconcile from obtained CLOG cache",
                plugin.isCurrentTaskCompletionCriteriaMet());
    }

    private static XtremeTask countedCollectionLogTask(String id, String name, int count)
    {
        TaskVerification verification = new Gson().fromJson(
                "{\"method\":\"collection-log\",\"itemIds\":[4119,4121,4123,4125,4127,4129,4131],\"count\":" + count + "}",
                TaskVerification.class
        );

        return new XtremeTask(
                id,
                name,
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY,
                null,
                null,
                null,
                null,
                null,
                verification,
                null
        );
    }

    private static XtremeTask collectionLogTask(String id, String name, TaskTier tier, int[] itemIds, int count)
    {
        String itemIdsJson = java.util.Arrays.stream(itemIds)
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        TaskVerification verification = new Gson().fromJson(
                "{\"method\":\"collection-log\",\"itemIds\":[" + itemIdsJson + "],\"count\":" + count + "}",
                TaskVerification.class
        );

        return new XtremeTask(
                id,
                name,
                TaskSource.COLLECTION_LOG,
                tier,
                null,
                null,
                null,
                null,
                null,
                verification,
                null
        );
    }

    private static void assertSequenceDecoration(
            String id,
            String name,
            TaskTier tier,
            int[] itemIds,
            int count,
            int expectedIconItemId,
            String expectedLabel
    ) throws Exception
    {
        XtremeTask task = collectionLogTask(id, name, tier, itemIds, count);
        Method decorate = XtremeTaskerPlugin.class.getDeclaredMethod("decorateCurrentSequenceTask", XtremeTask.class);
        decorate.setAccessible(true);

        XtremeTask decorated = (XtremeTask) decorate.invoke(new XtremeTaskerPlugin(), task);

        assertEquals("Decorated sequence task should use the exact current step icon",
                Integer.valueOf(expectedIconItemId),
                decorated.getIconItemId());
        assertTrue("Decorated sequence task should include the exact current step label",
                decorated.getName().contains("(" + expectedLabel + ")"));
    }

    private static XtremeTask achievementDiaryTask(String id, String name)
    {
        TaskVerification verification = new Gson().fromJson(
                "{\"method\":\"achievement-diary\",\"region\":\"ardougne\",\"difficulty\":\"easy\"}",
                TaskVerification.class
        );

        return new XtremeTask(
                id,
                name,
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY,
                null,
                null,
                null,
                null,
                null,
                verification,
                null
        );
    }

    private static XtremeTask combatAchievementTask(String id, String name, int taskId)
    {
        TaskVerification verification = new Gson().fromJson(
                "{\"method\":\"COMBAT_ACHIEVEMENT\",\"taskId\":" + taskId + "}",
                TaskVerification.class
        );

        return new XtremeTask(
                id,
                name,
                TaskSource.COMBAT_ACHIEVEMENT,
                TaskTier.EASY,
                null,
                null,
                null,
                null,
                null,
                verification,
                null
        );
    }

    private static void invokeRefreshCombatAchievementSyncState(XtremeTaskerPlugin plugin) throws Exception
    {
        Method refresh = XtremeTaskerPlugin.class.getDeclaredMethod("refreshCombatAchievementSyncState");
        refresh.setAccessible(true);
        refresh.invoke(plugin);
    }

    private static void invokeRefreshCollectionLogNonItemSyncState(XtremeTaskerPlugin plugin) throws Exception
    {
        Method refresh = XtremeTaskerPlugin.class.getDeclaredMethod("refreshCollectionLogNonItemSyncState");
        refresh.setAccessible(true);
        refresh.invoke(plugin);
    }

    private static List<String> taskIds(List<XtremeTask> tasks)
    {
        return tasks.stream().map(XtremeTask::getId).collect(java.util.stream.Collectors.toList());
    }

    private static final class StubCombatAchievementService extends CombatAchievementService
    {
        private final boolean complete;

        private StubCombatAchievementService(boolean complete)
        {
            this.complete = complete;
        }

        @Override
        public boolean isTaskComplete(int sortId)
        {
            return complete;
        }
    }

}
