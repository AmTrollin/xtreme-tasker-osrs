package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.persistence.AtomicStateFiles;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StateSaveQueueTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void actualDiskFailureReachesCallerAndTheSnapshotCanBeRetried() throws Exception
    {
        Path parent = temporary.newFile("blocker").toPath();
        Path save = parent.resolve("account.json");
        StateSaveQueue writer = new StateSaveQueue();
        try
        {
            writer.submit(() -> write(save, "detached snapshot"));
            try
            {
                writer.flush();
                fail("A failed disk write must fail the barrier");
            }
            catch (IllegalStateException expected)
            {
                assertTrue(expected.getCause() instanceof UncheckedIOException);
            }
            assertTrue(writer.hasFailure());
            Files.delete(parent);
            writer.retryFailed();
            writer.flush();
            assertFalse(writer.hasFailure());
            assertEquals("detached snapshot", Files.readString(save));
        }
        finally
        {
            writer.close();
        }
    }

    @Test
    public void retryNeverReplaysAnOlderSnapshotAfterANewerSave() throws Exception
    {
        Path file = temporary.getRoot().toPath().resolve("account.json");
        StateSaveQueue writer = new StateSaveQueue();
        try
        {
            try
            {
                writer.submit(() -> { throw new IllegalStateException("old failure"); }).join();
                fail("Expected failure");
            }
            catch (CompletionException expected) { }
            writer.submit(() -> write(file, "new snapshot"));
            writer.retryFailed();
            writer.flush();
            assertEquals("new snapshot", Files.readString(file));
        }
        finally
        {
            writer.close();
        }
    }

    private static void write(Path file, String text)
    {
        try
        {
            AtomicStateFiles.write(file, text);
        }
        catch (java.io.IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
