package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CollectionLogMismatchTest
{
    @Test
    public void collectionLogCacheListenerFiresForNewObtainedItemsOnly()
    {
        CollectionLogService collectionLogService = new CollectionLogService();
        AtomicInteger changes = new AtomicInteger();
        collectionLogService.setCacheChangeListener(changes::incrementAndGet);

        collectionLogService.storeItem(10878);
        assertEquals(1, changes.get());
        assertTrue(collectionLogService.isItemObtained(10878));

        collectionLogService.storeItem(10878);
        assertEquals(1, changes.get());

        collectionLogService.restoreCachedItemIds(Collections.singleton(10879));
        assertEquals(1, changes.get());
        assertTrue(collectionLogService.isItemObtained(10879));
    }

    @Test
    public void collectionLogCacheBatchCoalescesNewObtainedItemNotifications()
    {
        CollectionLogService collectionLogService = new CollectionLogService();
        AtomicInteger changes = new AtomicInteger();
        collectionLogService.setCacheChangeListener(changes::incrementAndGet);

        collectionLogService.beginCacheChangeBatch();
        collectionLogService.storeItem(10878);
        collectionLogService.storeItem(10879);
        collectionLogService.storeItem(10879);

        assertEquals("batched collection-log scans should not notify per item", 0, changes.get());

        collectionLogService.endCacheChangeBatch();

        assertEquals("batched collection-log scans should notify once when changed", 1, changes.get());
        assertTrue(collectionLogService.isItemObtained(10878));
        assertTrue(collectionLogService.isItemObtained(10879));
    }

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

        Set<String> syncedCompletedTaskIds = plugin.syncedCompletedTaskIdsForTesting();
        syncedCompletedTaskIds.clear();
        syncedCompletedTaskIds.add(task.getId());

        List<XtremeTask> mismatchesWhenNotFound = plugin.findCollectionLogSyncMismatches(true);
        assertEquals("Completed CLOG requirement not found by sync should mismatch", 1, mismatchesWhenNotFound.size());
        assertEquals(task.getId(), mismatchesWhenNotFound.get(0).getId());

        collectionLogService.storeItem(10878);
        List<XtremeTask> mismatchesWhenObtained = plugin.findCollectionLogSyncMismatches(true);
        assertTrue("Obtained requirement should no longer mismatch", mismatchesWhenObtained.isEmpty());
    }

    @Test
    public void collectionLogSyncFindsCandidatesWithoutMarkingTasksComplete() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(task);

        collectionLogService.storeItem(10878);

        List<String> candidates = new java.util.ArrayList<>();
        int found = plugin.findCollectionLogCompletionCandidatesFromCache(candidates);

        assertEquals(1, found);
        assertEquals(task.getId(), candidates.get(0));
        assertFalse("Sync discovery must not auto-mark the task complete", plugin.isTaskCompleted(task));
        assertTrue(plugin.manualCompletedTaskIdsForTesting().isEmpty());
        assertTrue(plugin.syncedCompletedTaskIdsForTesting().isEmpty());
    }

    @Test
    public void collectionLogCacheChangeRefreshesCountedGroupCandidates() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(collectionLogService);

        int[] giantsFoundryItemIds = new int[]{27012, 27014, 27017, 27019, 27021, 27023, 27025, 27027, 27029};
        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        for (int i = 1; i <= 4; i++)
        {
            tasks.add(collectionLogTask(
                    "collection_log_easy_get-1-unique-from-giants-foundry_00" + i + "_test",
                    "Get 1 unique from Giants' Foundry",
                    TaskTier.EASY,
                    giantsFoundryItemIds,
                    i
            ));
        }

        Set<String> syncedCompletedTaskIds = plugin.syncedCompletedTaskIdsForTesting();
        syncedCompletedTaskIds.clear();
        syncedCompletedTaskIds.add(tasks.get(0).getId());
        syncedCompletedTaskIds.add(tasks.get(1).getId());

        collectionLogService.storeItem(27012);
        collectionLogService.storeItem(27014);
        collectionLogService.storeItem(27017);
        collectionLogService.storeItem(27019);

        List<XtremeTask> candidates = plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG);
        assertEquals("Four cached Giants' Foundry uniques should stage the third and fourth easy tasks",
                2, candidates.size());
        assertEquals(tasks.get(2).getId(), candidates.get(0).getId());
        assertEquals(tasks.get(3).getId(), candidates.get(1).getId());
        assertFalse("Cache refresh must not auto-mark the staged task complete", plugin.isTaskCompleted(tasks.get(2)));
        assertFalse("Cache refresh must not auto-mark the staged task complete", plugin.isTaskCompleted(tasks.get(3)));
    }

    @Test
    public void applyingSyncCandidatesMarksTasksCompleteAsSynced() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        XtremeTask task = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(task);

        plugin.markSyncCompletionCandidateTasksCompleteAndPersist(Collections.singletonList(task));

        assertTrue(plugin.isTaskCompleted(task));
        assertTrue(plugin.manualCompletedTaskIdsForTesting().isEmpty());
        assertTrue(plugin.syncedCompletedTaskIdsForTesting().contains(task.getId()));
    }

    @Test
    public void manualOnlyCollectionLogTaskIsNotMarkedAsSyncMismatch() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());

        XtremeTask task = new XtremeTask(
                "collection_log_medium_1-graceful-recolor_001_34396fc6b4",
                "1 Graceful Recolor",
                TaskSource.COLLECTION_LOG,
                TaskTier.MEDIUM,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(task);

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(task.getId());

        List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
        assertTrue("Manual-only graceful recolor should stay out of sync mismatch review", mismatches.isEmpty());
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

        collectionLogService.storeItem(28138);

        List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
        assertEquals("Only the second displayed Forestry completion should be reviewable", 1, mismatches.size());
        assertEquals(tasks.get(1).getId(), mismatches.get(0).getId());
    }

    @Test
    public void syncMismatchApplyBlocksIncompleteSelectionInsideDisplaySequence() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();

        int[] mtaWandItemIds = new int[]{6908, 6910, 6912, 6914};
        XtremeTask beginner = collectionLogTask(
                "collection_log_easy_upgrade-the-mta-wand-once_001_test",
                "Upgrade the MTA wand once",
                TaskTier.EASY,
                mtaWandItemIds,
                1
        );
        XtremeTask apprentice = collectionLogTask(
                "collection_log_easy_upgrade-the-mta-wand-once_002_test",
                "Upgrade the MTA wand once",
                TaskTier.EASY,
                mtaWandItemIds,
                2
        );
        XtremeTask teacher = collectionLogTask(
                "collection_log_easy_upgrade-the-mta-wand-once_003_test",
                "Upgrade the MTA wand once",
                TaskTier.EASY,
                mtaWandItemIds,
                3
        );
        XtremeTask unrelated = collectionLogTask(
                "collection_log_easy_get-a-green-satchel_001_guard_test",
                "Get a Green satchel",
                TaskTier.EASY,
                new int[]{10878},
                1
        );
        XtremeTask bootsFirst = countedCollectionLogTask(
                "collection_log_easy_get-the-next-tier-of-metal-boots_001_guard_test",
                "Get the next tier of metal boots",
                1
        );
        XtremeTask bootsSecond = countedCollectionLogTask(
                "collection_log_easy_get-the-next-tier-of-metal-boots_002_guard_test",
                "Get the next tier of metal boots",
                2
        );

        List<XtremeTask> tasks = plugin.tasksForTesting();
        tasks.clear();
        tasks.add(beginner);
        tasks.add(apprentice);
        tasks.add(teacher);
        tasks.add(unrelated);
        tasks.add(bootsFirst);
        tasks.add(bootsSecond);

        Set<String> manualCompletedTaskIds = plugin.manualCompletedTaskIdsForTesting();
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(beginner.getId());
        manualCompletedTaskIds.add(apprentice.getId());
        manualCompletedTaskIds.add(teacher.getId());
        manualCompletedTaskIds.add(unrelated.getId());
        manualCompletedTaskIds.add(bootsFirst.getId());
        manualCompletedTaskIds.add(bootsSecond.getId());

        List<String> syncMismatchTaskIds = plugin.syncMismatchTaskIdsForTesting();
        syncMismatchTaskIds.clear();
        syncMismatchTaskIds.add(beginner.getId());
        syncMismatchTaskIds.add(apprentice.getId());
        syncMismatchTaskIds.add(teacher.getId());
        syncMismatchTaskIds.add(unrelated.getId());
        syncMismatchTaskIds.add(bootsFirst.getId());
        syncMismatchTaskIds.add(bootsSecond.getId());
        plugin.setSyncMismatchTitleForTesting("Review completed tasks");

        String guardMessage = plugin.getSyncMismatchIncompleteGuardMessage(Collections.singletonList(beginner));
        assertTrue("Beginner wand should require higher wand steps to be selected too",
                guardMessage != null && guardMessage.contains("Apprentice MTA wand"));
        assertTrue("Single-sequence message should separate the headline from the fix",
                guardMessage.contains("out of order.\n\nTask: Upgrade the MTA wand once\nSelected \"Obtain Beginner MTA wand\""));

        List<XtremeTask> mixedInvalidSelection = List.of(beginner, apprentice, unrelated, bootsFirst);
        guardMessage = plugin.getSyncMismatchIncompleteGuardMessage(mixedInvalidSelection);
        assertTrue("Mixed saves should still be blocked by a bad wand sequence",
                guardMessage != null && guardMessage.contains("Beginner MTA wand"));
        assertTrue("Multiple bad selections in one wand series should be named",
                guardMessage.contains("Apprentice MTA wand"));
        assertTrue("Missing higher wand step should be named",
                guardMessage.contains("Teacher MTA wand"));
        assertTrue("Multiple bad series should be summarized together",
                guardMessage.contains("2 sequences"));
        assertTrue("A second bad sequence should be named too",
                guardMessage.contains("metal boots"));
        assertTrue("Multi-sequence message should put each sequence on its own paragraph",
                guardMessage.contains("out of order.\n\nTask: Upgrade the MTA wand once\nSelected \"Obtain Beginner MTA wand\"")
                        && guardMessage.contains("unselect \"Obtain Beginner MTA wand\" and \"Upgrade to Apprentice MTA wand\".\n\nTask: Get the next tier of metal boots"));

        plugin.markSyncMismatchTasksIncompleteAndPersist(mixedInvalidSelection);
        assertTrue("Guarded beginner wand should remain complete", plugin.isTaskCompleted(beginner));
        assertTrue("Guarded apprentice wand should remain complete", plugin.isTaskCompleted(apprentice));
        assertTrue("Guarded teacher wand should remain complete", plugin.isTaskCompleted(teacher));
        assertTrue("Unrelated selected task should not be saved when any sequence is invalid", plugin.isTaskCompleted(unrelated));
        assertTrue("Guarded boots task should remain complete", plugin.isTaskCompleted(bootsFirst));
        assertTrue("Guarded higher boots task should remain complete", plugin.isTaskCompleted(bootsSecond));

        assertEquals("Suffix sequence selections should be saveable from the grouped picker",
                null,
                plugin.getSyncMismatchIncompleteGuardMessage(List.of(apprentice, teacher)));

        plugin.markSyncMismatchTasksIncompleteAndPersist(List.of(beginner, apprentice, teacher));
        assertFalse(plugin.isTaskCompleted(beginner));
        assertFalse(plugin.isTaskCompleted(apprentice));
        assertFalse(plugin.isTaskCompleted(teacher));
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
    public void teaFlaskAlternateItemsCountForTeaFlaskRequirement()
    {
        int[] teaFlaskIds = new int[]{
                10859,
                10860,
                10861,
                25617
        };

        for (int teaFlaskId : teaFlaskIds)
        {
            CollectionLogService collectionLogService = new CollectionLogService();
            collectionLogService.storeSeenItem(teaFlaskId);
            collectionLogService.storeItem(teaFlaskId);

            assertTrue("Tea flask item " + teaFlaskId + " should satisfy Tea flask",
                    collectionLogService.hasSeenAll(new int[]{10859}));
            assertEquals("Tea flask item " + teaFlaskId + " should count as one Tea flask slot",
                    1, collectionLogService.countObtained(new int[]{10859}));
        }

        CollectionLogService collectionLogService = new CollectionLogService();
        for (int teaFlaskId : teaFlaskIds)
        {
            collectionLogService.storeItem(teaFlaskId);
        }

        assertEquals("Multiple Tea flask item IDs should still count as one Tea flask slot",
                1, collectionLogService.countObtained(new int[]{10859}));
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
    public void chargedRaidItemsDoNotSatisfyUnchargedCollectionLogSlots()
    {
        int[][] unchargedItemPairs = new int[][]{
                {22481, 22323}, // Sanguinesti staff
                {22486, 22325}, // Scythe of vitur
                {28549, 28547}  // Tumeken's shadow
        };

        for (int[] unchargedItemPair : unchargedItemPairs)
        {
            int unchargedItemId = unchargedItemPair[0];
            int chargedItemId = unchargedItemPair[1];

            CollectionLogService collectionLogService = new CollectionLogService();
            collectionLogService.storeSeenItem(unchargedItemId);
            collectionLogService.storeItem(unchargedItemId);

            assertTrue("Uncharged item " + unchargedItemId + " should satisfy its collection log slot",
                    collectionLogService.hasSeenAll(new int[]{unchargedItemId}));
            assertEquals("Uncharged item " + unchargedItemId + " should count as one collection log slot",
                    1, collectionLogService.countObtained(new int[]{unchargedItemId}));

            CollectionLogService chargedOnlyCollectionLogService = new CollectionLogService();
            chargedOnlyCollectionLogService.storeSeenItem(chargedItemId);
            chargedOnlyCollectionLogService.storeItem(chargedItemId);

            assertFalse("Charged item " + chargedItemId + " should not satisfy uncharged item " + unchargedItemId,
                    chargedOnlyCollectionLogService.hasSeenAll(new int[]{unchargedItemId}));
            assertEquals("Charged item " + chargedItemId + " should not count for uncharged item " + unchargedItemId,
                    0, chargedOnlyCollectionLogService.countObtained(new int[]{unchargedItemId}));
        }
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

        List<XtremeTask> mismatches = plugin.findCollectionLogSyncMismatches(true);
        assertEquals("Repeated counted CLOG completion not found by sync should be reviewable", 1, mismatches.size());
        assertEquals(first.getId(), mismatches.get(0).getId());
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
                TaskSource.DIARY_ACHIEVEMENT,
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

}
