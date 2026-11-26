package com.amtrollin.xtremetasker;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;

/** Keeps exit protection registered until the final client-thread snapshot is written. */
final class StateSaveLifecycle
{
    private final EventBus eventBus;
    private final Consumer<Runnable> clientScheduler;
    private final StateSaveQueue writer;
    private final Runnable captureFinalState;
    private final Runnable clearState;
    private final Consumer<Throwable> reportFailure;
    private CompletableFuture<Void> completion;
    private volatile boolean capturedFinalState;

    StateSaveLifecycle(EventBus eventBus, Consumer<Runnable> clientScheduler, StateSaveQueue writer,
            Runnable captureFinalState, Runnable clearState, Consumer<Throwable> reportFailure)
    {
        this.eventBus = eventBus;
        this.clientScheduler = clientScheduler;
        this.writer = writer;
        this.captureFinalState = captureFinalState;
        this.clearState = clearState;
        this.reportFailure = reportFailure;
        eventBus.register(this);
    }

    synchronized boolean isFinishing()
    {
        return completion != null;
    }

    boolean hasCapturedFinalState()
    {
        return capturedFinalState;
    }

    @Subscribe
    public void onClientShutdown(ClientShutdown event)
    {
        event.waitFor(finish());
    }

    synchronized CompletableFuture<Void> finish()
    {
        if (completion != null) return completion;
        completion = new CompletableFuture<>();
        try
        {
            clientScheduler.accept(this::captureAndFinish);
        }
        catch (Throwable error)
        {
            capturedFinalState = true;
            drain(error);
        }
        return completion;
    }

    private void captureAndFinish()
    {
        Throwable failure = null;
        try
        {
            captureFinalState.run();
        }
        catch (Throwable error)
        {
            failure = error;
        }
        finally
        {
            try
            {
                clearState.run();
            }
            catch (Throwable error)
            {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            capturedFinalState = true;
            drain(failure);
        }
    }

    private void drain(Throwable captureFailure)
    {
        writer.finish().whenComplete((ignored, writeFailure) ->
        {
            Throwable failure = captureFailure == null ? writeFailure : captureFailure;
            eventBus.unregister(this);
            if (failure == null) completion.complete(null);
            else
            {
                if (captureFailure != null && writeFailure != null) failure.addSuppressed(writeFailure);
                try
                {
                    reportFailure.accept(failure);
                }
                finally
                {
                    completion.completeExceptionally(failure);
                }
            }
        });
    }
}
