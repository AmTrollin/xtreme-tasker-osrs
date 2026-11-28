package com.amtrollin.xtremetasker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ClientShutdown;
import org.junit.Test;
import static org.junit.Assert.*;

public class StateSaveLifecycleTest
{
    @Test
    public void clientExitWaitsForClientSnapshotAndFinalWrite()
    {
        EventBus bus = new EventBus();
        StateSaveQueue writer = new StateSaveQueue();
        List<Runnable> clientActions = new ArrayList<>();
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger clears = new AtomicInteger();
        new StateSaveLifecycle(bus, clientActions::add, writer,
                () -> { captures.incrementAndGet(); writer.submit(writes::incrementAndGet); },
                clears::incrementAndGet, error -> fail(error.toString()));

        ClientShutdown exit = new ClientShutdown();
        bus.post(exit);
        assertEquals(1, exit.getTasks().size());
        assertFalse(exit.getTasks().peek().isDone());
        assertEquals(0, captures.get());
        clientActions.remove(0).run();
        ((CompletableFuture<?>) exit.getTasks().peek()).join();
        assertEquals(1, captures.get());
        assertEquals(1, writes.get());
        assertEquals(1, clears.get());
    }

    @Test
    public void disableThenImmediateClientExitWaitsForTheSamePendingSave()
    {
        EventBus bus = new EventBus();
        StateSaveQueue writer = new StateSaveQueue();
        List<Runnable> clientActions = new ArrayList<>();
        AtomicInteger captures = new AtomicInteger();
        StateSaveLifecycle lifecycle = new StateSaveLifecycle(bus, clientActions::add, writer,
                captures::incrementAndGet, () -> { }, error -> fail(error.toString()));
        CompletableFuture<Void> disabled = lifecycle.finish();
        assertSame(disabled, lifecycle.finish());
        ClientShutdown exit = new ClientShutdown();
        bus.post(exit);
        assertSame(disabled, exit.getTasks().peek());
        assertEquals(1, clientActions.size());
        clientActions.remove(0).run();
        disabled.join();
        assertEquals(1, captures.get());

        ClientShutdown afterDrain = new ClientShutdown();
        bus.post(afterDrain);
        assertTrue("The completed lifecycle must unregister its exit listener", afterDrain.getTasks().isEmpty());
    }

    @Test
    public void captureFailureStillClearsStateAndReportsFailure()
    {
        EventBus bus = new EventBus();
        StateSaveQueue writer = new StateSaveQueue();
        AtomicInteger clears = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();
        StateSaveLifecycle lifecycle = new StateSaveLifecycle(bus, Runnable::run, writer,
                () -> { throw new IllegalStateException("capture failed"); }, clears::incrementAndGet, errors::add);
        expectFailure(lifecycle.finish(), "capture failed");
        assertEquals(1, clears.get());
        assertEquals(1, errors.size());
    }

    @Test
    public void diskFailureCannotPreventCleanupOrHangClientExit()
    {
        EventBus bus = new EventBus();
        StateSaveQueue writer = new StateSaveQueue();
        AtomicInteger clears = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();
        StateSaveLifecycle lifecycle = new StateSaveLifecycle(bus, Runnable::run, writer,
                () -> writer.submit(() -> { throw new IllegalStateException("disk failed"); }),
                clears::incrementAndGet, errors::add);
        ClientShutdown exit = new ClientShutdown();
        bus.post(exit);
        expectFailure(lifecycle.finish(), "disk failed");
        assertEquals(1, clears.get());
        assertEquals(1, errors.size());
    }

    private static void expectFailure(CompletableFuture<?> future, String message)
    {
        try
        {
            future.join();
            fail("Expected failed save");
        }
        catch (CompletionException e)
        {
            assertEquals(message, e.getCause().getMessage());
        }
    }
}
