package com.amtrollin.xtremetasker;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Serializes detached state snapshots without blocking rendering on disk I/O. */
final class StateSaveQueue implements AutoCloseable
{
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
    {
        Thread thread = new Thread(runnable, "xtreme-tasker-state-writer");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> lastSave;

    synchronized void submit(Runnable save)
    {
        lastSave = executor.submit(save);
    }

    void flush()
    {
        Future<?> pending;
        synchronized (this)
        {
            pending = lastSave;
        }
        if (pending == null) return;
        // Finish outstanding writes before loading an account or clearing state.
        // The writer never calls into or waits for the client thread.
        boolean interrupted = false;
        try
        {
            while (true)
            {
                try
                {
                    pending.get();
                    return;
                }
                catch (InterruptedException e)
                {
                    interrupted = true;
                }
                catch (ExecutionException e)
                {
                    throw new IllegalStateException("Failed to save XtremeTasker state", e.getCause());
                }
            }
        }
        finally
        {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close()
    {
        synchronized (this)
        {
            executor.shutdown();
        }
        flush();
    }
}
