package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.tasklist.TaskGroupUtils;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.google.gson.Gson;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class PerformanceRegressionTest
{
    @Test
    public void renderingDoesNotRecountItemsOrInvalidateTimers()
    {
        CountingCollectionLog service = new CountingCollectionLog();
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(service);
        XtremeTask task = task("first", 1);
        plugin.tasksForTesting().add(task);
        service.storeItem(10878);
        plugin.setCurrentTaskForTesting(task);

        int reads = service.counts;
        int timerVersion = plugin.getTaskListTimerStateVersion();
        for (int i = 0; i < 1000; i++) assertTrue(plugin.isCurrentTaskCompletionCriteriaMet());
        assertEquals(reads, service.counts);
        assertEquals(timerVersion, plugin.getTaskListTimerStateVersion());
    }

    @Test
    public void cachedCompletionChangesOnDropAndUndo() throws Exception
    {
        CollectionLogService service = new CollectionLogService();
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(service);
        XtremeTask task = task("first", 1);
        plugin.tasksForTesting().add(task);
        plugin.setCurrentTaskForTesting(task);
        assertFalse(plugin.isCurrentTaskCompletionCriteriaMet());
        service.storeItem(10878);
        assertTrue(plugin.isCurrentTaskCompletionCriteriaMet());
        java.lang.reflect.Field clientThread = XtremeTaskerPlugin.class.getDeclaredField("clientThread");
        clientThread.setAccessible(true);
        clientThread.set(plugin, new net.runelite.client.callback.ClientThread()
        {
            @Override
            public void invokeLater(Runnable runnable) { }
        });
        plugin.completeCurrentTaskAndPersist();
        assertFalse(plugin.isCurrentTaskCompletionCriteriaMet());
        plugin.undoCurrentTaskCompletionAndPersist();
        assertTrue(plugin.isCurrentTaskCompletionCriteriaMet());
    }

    @Test
    public void groupIndexMatchesGroupingRulesAndReloadsAtSamePackVersion()
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        List<XtremeTask> tasks = plugin.tasksForTesting();
        XtremeTask first = task("first", 1);
        XtremeTask second = task("second", 2);
        tasks.add(first);
        tasks.add(second);
        assertEquals(TaskGroupUtils.groupFor(tasks, first), plugin.getTaskGroupInstances(first));
        assertSame(plugin.getTaskGroupInstances(first), plugin.getTaskGroupInstances(first));

        tasks = plugin.tasksForTesting();
        tasks.clear();
        XtremeTask replacement = task("replacement", 1);
        tasks.add(replacement);
        assertEquals(List.of(replacement), plugin.getTaskGroupInstances(replacement));
    }

    @Test
    public void stateSnapshotsDoNotShareMutableAccountData() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService service = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(service);
        plugin.manualCompletedTaskIdsForTesting().add("account-a-task");
        service.storeItem(10878);
        Method build = XtremeTaskerPlugin.class.getDeclaredMethod("buildPersistedState");
        build.setAccessible(true);
        PersistedState snapshot = (PersistedState) build.invoke(plugin);

        plugin.manualCompletedTaskIdsForTesting().clear();
        service.resetCachedItemIds();
        assertEquals(java.util.Set.of("account-a-task"), snapshot.getManualCompletedTaskIds());
        assertTrue(snapshot.getCollectionLogItemIds().contains(10878));
    }

    @Test
    public void queuedWritesReturnBeforeIoAndDrainInOrder() throws Exception
    {
        StateSaveQueue queue = new StateSaveQueue();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> written = new ArrayList<>();
        try
        {
            queue.submit(() ->
            {
                started.countDown();
                try
                {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Writer was not released");
                }
                catch (InterruptedException e)
                {
                    throw new AssertionError(e);
                }
                written.add("account-a-before-switch");
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            queue.submit(() -> written.add("account-b-after-switch"));
            release.countDown();
            queue.flush();
            assertEquals(List.of("account-a-before-switch", "account-b-after-switch"), written);
            queue.submit(() -> written.add("shutdown"));
        }
        finally
        {
            release.countDown();
            queue.close();
        }
        assertEquals("shutdown", written.get(written.size() - 1));
    }

    @Test
    public void stateConfigurationEventsDoNotRebuildOverlays()
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        ConfigChanged event = new ConfigChanged();
        event.setGroup("xtremetasker");
        event.setKey("state_account_backup_1");
        // No UI dependencies are installed; touching the overlays would fail.
        plugin.onConfigChanged(event);
    }

    @Test
    public void incrementalSyncMatchesFullRefreshAcrossDropsCompletionsAndDismissals() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        CollectionLogService service = new CollectionLogService();
        plugin.setCollectionLogServiceForTesting(service);
        List<XtremeTask> tasks = plugin.tasksForTesting();
        for (int group = 0; group < 12; group++)
        {
            for (int count = 1; count <= 3; count++)
            {
                TaskVerification verification = new Gson().fromJson(
                        "{\"method\":\"collection-log\",\"itemIds\":[" + (100 + group * 3) + ","
                                + (101 + group * 3) + "," + (102 + group * 3) + "],\"count\":" + count + "}",
                        TaskVerification.class);
                XtremeTask task = new XtremeTask("group-" + group + "-" + count, "Get a unique " + group,
                        TaskSource.COLLECTION_LOG, TaskTier.EASY, 100 + group * 3,
                        null, null, null, null, verification, null);
                tasks.add(task);
                if (count == 1) plugin.manualCompletedTaskIdsForTesting().add(task.getId());
            }
        }
        service.markSyncSeen();
        Method fullRefresh = XtremeTaskerPlugin.class.getDeclaredMethod("refreshCollectionLogCompletionCandidatesFromCache");
        fullRefresh.setAccessible(true);
        java.lang.reflect.Field candidateIds = XtremeTaskerPlugin.class.getDeclaredField("syncCompletionCandidateTaskIds");
        candidateIds.setAccessible(true);

        java.util.Random random = new java.util.Random(71);
        for (int step = 0; step < 100; step++)
        {
            if (step % 7 == 0) plugin.dismissSyncCompletionCandidateReview(TaskSource.COLLECTION_LOG);
            if (step % 9 == 0) plugin.dismissSyncMismatchReview();
            if (step % 11 == 0) plugin.toggleTaskCompletedAndPersist(tasks.get(random.nextInt(tasks.size())));
            if (step % 13 == 0) service.markFullSyncSeen();
            int item = 100 + random.nextInt(36);
            if (step % 2 == 0) service.storeItem(item);
            else service.storeSeenItem(item);
            // Always deliver a cache-change event, including when the random
            // item was already cached. Unrelated slots must preserve results.
            service.storeSeenItem(10000 + step);

            List<?> incrementalCandidates = new ArrayList<>((List<?>) candidateIds.get(plugin));
            List<String> incrementalMismatches = new ArrayList<>(plugin.syncMismatchTaskIdsForTesting());
            fullRefresh.invoke(plugin);
            assertEquals("Candidate membership at step " + step, incrementalCandidates,
                    new ArrayList<>((List<?>) candidateIds.get(plugin)));
            assertEquals("Mismatch membership at step " + step, incrementalMismatches,
                    new ArrayList<>(plugin.syncMismatchTaskIdsForTesting()));
        }
    }

    private static XtremeTask task(String id, int count)
    {
        TaskVerification verification = new Gson().fromJson(
                "{\"method\":\"collection-log\",\"itemIds\":[10878,10879],\"count\":" + count + "}",
                TaskVerification.class);
        return new XtremeTask(id, "Get a satchel", TaskSource.COLLECTION_LOG, TaskTier.EASY,
                10878, null, null, null, null, verification, null);
    }

    private static final class CountingCollectionLog extends CollectionLogService
    {
        private int counts;

        @Override
        public long countObtained(int[] ids)
        {
            counts++;
            return super.countObtained(ids);
        }
    }
}
