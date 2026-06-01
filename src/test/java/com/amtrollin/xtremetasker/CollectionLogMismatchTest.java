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

        List<XtremeTask> mismatchesWhenUnseen = (List<XtremeTask>) findMismatches.invoke(plugin, true);
        assertEquals("Plugin-completed CLOG requirement not found by sync should be reviewable",
                1, mismatchesWhenUnseen.size());
        assertEquals(task.getId(), mismatchesWhenUnseen.get(0).getId());

        collectionLogService.storeSeenItem(10878);
        List<XtremeTask> mismatchesWhenSeenButUnobtained = (List<XtremeTask>) findMismatches.invoke(plugin, true);
        assertEquals("Seen but unobtained CLOG requirement should mismatch", 1, mismatchesWhenSeenButUnobtained.size());
        assertEquals(task.getId(), mismatchesWhenSeenButUnobtained.get(0).getId());

        collectionLogService.storeItem(10878);
        List<XtremeTask> mismatchesWhenObtained = (List<XtremeTask>) findMismatches.invoke(plugin, true);
        assertTrue("Obtained requirement should no longer mismatch", mismatchesWhenObtained.isEmpty());
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
