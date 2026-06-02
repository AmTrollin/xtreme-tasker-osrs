package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.google.gson.Gson;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CollectionLogMismatchTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void collectionLogRequirementNotFoundBySyncIsMarkedAsMismatch() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        setField(plugin, "collectionLogService", collectionLogService);

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

        List<XtremeTask> tasks = (List<XtremeTask>) getField(plugin, "tasks");
        tasks.clear();
        tasks.add(task);

        Set<String> syncedCompletedTaskIds = (Set<String>) getField(plugin, "syncedCompletedTaskIds");
        syncedCompletedTaskIds.clear();
        syncedCompletedTaskIds.add(task.getId());

        Method findMismatches = XtremeTaskerPlugin.class
                .getDeclaredMethod("findCollectionLogSyncMismatches", boolean.class);
        findMismatches.setAccessible(true);

        List<XtremeTask> mismatchesWhenNotFound = (List<XtremeTask>) findMismatches.invoke(plugin, true);
        assertEquals("Completed CLOG requirement not found by sync should mismatch", 1, mismatchesWhenNotFound.size());
        assertEquals(task.getId(), mismatchesWhenNotFound.get(0).getId());

        collectionLogService.storeItem(10878);
        List<XtremeTask> mismatchesWhenObtained = (List<XtremeTask>) findMismatches.invoke(plugin, true);
        assertTrue("Obtained requirement should no longer mismatch", mismatchesWhenObtained.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
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
            setField(plugin, "collectionLogService", collectionLogService);

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

            List<XtremeTask> tasks = (List<XtremeTask>) getField(plugin, "tasks");
            tasks.clear();
            tasks.add(task);

            Set<String> manualCompletedTaskIds = (Set<String>) getField(plugin, "manualCompletedTaskIds");
            manualCompletedTaskIds.clear();
            manualCompletedTaskIds.add(task.getId());

            collectionLogService.storeSeenItem(interfaceItemId);
            collectionLogService.storeItem(interfaceItemId);

            Method findMismatches = XtremeTaskerPlugin.class
                    .getDeclaredMethod("findCollectionLogSyncMismatches", boolean.class);
            findMismatches.setAccessible(true);

            List<XtremeTask> mismatches = (List<XtremeTask>) findMismatches.invoke(plugin, true);
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
    @SuppressWarnings("unchecked")
    public void repeatedCountedCollectionLogCompletionNotFoundBySyncIsMarkedAsMismatch() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService collectionLogService = new CollectionLogService();
        setField(plugin, "collectionLogService", collectionLogService);

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

        List<XtremeTask> tasks = (List<XtremeTask>) getField(plugin, "tasks");
        tasks.clear();
        tasks.add(first);
        tasks.add(second);

        Set<String> manualCompletedTaskIds = (Set<String>) getField(plugin, "manualCompletedTaskIds");
        manualCompletedTaskIds.clear();
        manualCompletedTaskIds.add(first.getId());

        Method findMismatches = XtremeTaskerPlugin.class
                .getDeclaredMethod("findCollectionLogSyncMismatches", boolean.class);
        findMismatches.setAccessible(true);

        List<XtremeTask> mismatches = (List<XtremeTask>) findMismatches.invoke(plugin, true);
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

    private static void setField(Object target, String fieldName, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
