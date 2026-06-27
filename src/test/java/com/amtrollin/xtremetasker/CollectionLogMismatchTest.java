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
