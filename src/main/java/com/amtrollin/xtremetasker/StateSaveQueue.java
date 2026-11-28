package com.amtrollin.xtremetasker;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs detached save snapshots in order; barriers report the latest save's result. */
final class StateSaveQueue implements AutoCloseable
{
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
    {
        Thread thread = new Thread(runnable, "xtreme-tasker-state-writer");
        thread.setDaemon(true);
        return thread;
    });
    private long submitted;
    private long failedSequence;
    private Throwable failure;
    private Runnable failedSave;
    private CompletableFuture<Void> finishing;

    synchronized CompletableFuture<Void> submit(Runnable save)
    {
        if (finishing != null) throw new IllegalStateException("State writer is closing");
        long sequence = ++submitted;
        CompletableFuture<Void> result = new CompletableFuture<>();
        executor.execute(() ->
        {
            try
            {
                save.run();
                synchronized (this)
                {
                    failure = null;
                    failedSave = null;
                }
                result.complete(null);
            }
            catch (Throwable error)
            {
                synchronized (this)
                {
                    failure = error;
                    failedSequence = sequence;
                    failedSave = save;
                }
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    synchronized boolean hasFailure()
    {
        return failure != null;
    }

    synchronized void retryFailed()
    {
        // Never replay an older snapshot after a newer one was submitted.
        if (finishing == null && failedSave != null && failedSequence == submitted) submit(failedSave);
    }

    synchronized CompletableFuture<Void> barrier()
    {
        if (finishing != null) return finishing;
        CompletableFuture<Void> result = new CompletableFuture<>();
        executor.execute(() -> completeWithSaveResult(result));
        return result;
    }

    synchronized CompletableFuture<Void> finish()
    {
        if (finishing == null)
        {
            finishing = new CompletableFuture<>();
            executor.execute(() -> completeWithSaveResult(finishing));
            executor.shutdown();
        }
        return finishing;
    }

    private synchronized void completeWithSaveResult(CompletableFuture<Void> result)
    {
        if (failure == null) result.complete(null);
        else result.completeExceptionally(failure);
    }

    void flush()
    {
        await(barrier());
    }

    private static void await(CompletableFuture<Void> result)
    {
        try
        {
            result.join();
        }
        catch (CompletionException e)
        {
            throw new IllegalStateException("Failed to save XtremeTasker state", e.getCause());
        }
    }

    @Override
    public void close()
    {
        await(finish());
    }
}
