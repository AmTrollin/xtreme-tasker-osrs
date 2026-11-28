package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.google.gson.Gson;
import java.awt.EventQueue;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClientThreadMutationTest
{
    @Test
    public void completeAndUndoDoNotMutateStateOnTheAwtThread() throws Exception
    {
        Fixture f = new Fixture();
        EventQueue.invokeAndWait(f.plugin::completeCurrentTaskAndPersist);
        assertFalse(f.plugin.isTaskCompleted(f.task));
        assertEquals(f.task, f.plugin.getCurrentTask());
        f.runNextClientAction();
        assertTrue(f.plugin.isTaskCompleted(f.task));
        assertNull(f.plugin.getCurrentTask());
        f.actions.clear(); // queued sound, irrelevant to state mutation

        EventQueue.invokeAndWait(f.plugin::undoCurrentTaskCompletionAndPersist);
        assertTrue(f.plugin.isTaskCompleted(f.task));
        f.runNextClientAction();
        assertFalse(f.plugin.isTaskCompleted(f.task));
        assertEquals(f.task.getId(), f.plugin.getCurrentTask().getId());
    }

    @Test
    public void clickImmediatelyBeforeDisableIsIncludedInFinalSnapshot() throws Exception
    {
        Fixture f = new Fixture();
        StateSaveQueue writer = new StateSaveQueue();
        AtomicBoolean finalSnapshotCompleted = new AtomicBoolean();
        StateSaveLifecycle lifecycle = new StateSaveLifecycle(new EventBus(), f.actions::add, writer,
                () -> finalSnapshotCompleted.set(f.plugin.isTaskCompleted(f.task)), () -> { }, error -> fail(error.toString()));
        setField(f.plugin, "saveLifecycle", lifecycle);
        setField(f.plugin, "initializedLifecycle", lifecycle);
        EventQueue.invokeAndWait(f.plugin::completeCurrentTaskAndPersist);
        java.util.concurrent.CompletableFuture<Void> finished = lifecycle.finish();
        f.runNextClientAction(); // accepted completion must not be canceled by disable
        f.runNextClientAction(); // final snapshot follows the completion
        finished.join();
        assertTrue(finalSnapshotCompleted.get());
    }

    @Test
    public void actionsSubmittedAfterDisableAreIgnored() throws Exception
    {
        Fixture f = new Fixture();
        StateSaveQueue writer = new StateSaveQueue();
        StateSaveLifecycle lifecycle = new StateSaveLifecycle(new EventBus(), f.actions::add, writer,
                () -> { }, () -> { }, error -> fail(error.toString()));
        setField(f.plugin, "saveLifecycle", lifecycle);
        setField(f.plugin, "initializedLifecycle", lifecycle);
        java.util.concurrent.CompletableFuture<Void> finished = lifecycle.finish();
        EventQueue.invokeAndWait(f.plugin::completeCurrentTaskAndPersist);
        assertEquals(1, f.actions.size());
        f.runNextClientAction();
        finished.join();
        assertFalse(f.plugin.isTaskCompleted(f.task));
    }

    @Test
    public void reenableDoesNotInitializeUntilPreviousWriterDrains() throws Exception
    {
        Fixture f = new Fixture();
        java.util.concurrent.BlockingQueue<Runnable> clientActions = new java.util.concurrent.LinkedBlockingQueue<>();
        setField(f.plugin, "clientThread", new ClientThread()
        {
            @Override public void invokeLater(Runnable action) { clientActions.add(action); }
        });
        EventBus bus = new EventBus();
        setField(f.plugin, "eventBus", bus);
        f.plugin.setCollectionLogServiceForTesting(new com.amtrollin.xtremetasker.verification.CollectionLogService()
        {
            @Override public void shutDown() { }
        });
        StateSaveQueue previousWriter = new StateSaveQueue();
        java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        StateSaveLifecycle previous = new StateSaveLifecycle(bus, clientActions::add, previousWriter,
                () -> previousWriter.submit(() ->
                {
                    writing.countDown();
                    try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Not released"); }
                    catch (InterruptedException e) { throw new AssertionError(e); }
                }), () -> { }, error -> fail(error.toString()));
        setField(f.plugin, "saveLifecycle", previous);
        setField(f.plugin, "initializedLifecycle", previous);
        java.util.concurrent.CompletableFuture<Void> stopped = previous.finish();
        f.plugin.startUp();
        try
        {
            assertEquals("Only the previous session's final capture is scheduled", 1, clientActions.size());
            clientActions.remove().run();
            assertTrue(writing.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue("Initialization must wait for disk completion", clientActions.isEmpty());
        }
        finally { release.countDown(); }
        stopped.join();
        Runnable initialize = clientActions.poll(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(initialize);

        Field field = XtremeTaskerPlugin.class.getDeclaredField("saveLifecycle");
        field.setAccessible(true);
        StateSaveLifecycle current = (StateSaveLifecycle) field.get(f.plugin);
        java.util.concurrent.CompletableFuture<Void> finished = current.finish();
        initialize.run(); // disabled before initialization: must not register the UI
        clientActions.remove().run();
        finished.join();
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture
    {
        private final XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        private final List<Runnable> actions = new ArrayList<>();
        private final AtomicBoolean clientThread = new AtomicBoolean(false);
        private final XtremeTask task;

        private Fixture() throws Exception
        {
            TaskVerification verification = new Gson().fromJson("{\"method\":\"COMBAT_ACHIEVEMENT\",\"taskId\":12}", TaskVerification.class);
            task = new XtremeTask("task", "Task", TaskSource.COMBAT_ACHIEVEMENT, TaskTier.EASY,
                    null, null, null, null, null, verification, null);
            plugin.tasksForTesting().add(task);
            setField(plugin, "currentTask", task);
            setField(plugin, "currentTaskId", task.getId());
            plugin.setClientForTesting((Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("isClientThread")) return clientThread.get();
                        throw new AssertionError("Unexpected client call: " + method.getName());
                    }));
            setField(plugin, "clientThread", new ClientThread()
            {
                @Override public void invokeLater(Runnable action) { actions.add(action); }
            });
        }

        private void runNextClientAction()
        {
            clientThread.set(true);
            try { actions.remove(0).run(); }
            finally { clientThread.set(false); }
        }
    }
}
