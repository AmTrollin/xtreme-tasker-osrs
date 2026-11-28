package com.amtrollin.xtremetasker.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AtomicStateFilesTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void writesCompleteUtf8StateAndAtomicallyReplacesExistingFile() throws Exception
    {
        Path file = temporary.getRoot().toPath().resolve("states/account.json");
        AtomicStateFiles.write(file, "{\"task\":\"old\"}");
        AtomicStateFiles.write(file, "{\"task\":\"new ü\"}");
        assertEquals("{\"task\":\"new ü\"}", Files.readString(file));
        try (java.util.stream.Stream<Path> files = Files.list(file.getParent()))
        {
            assertEquals(1, files.count());
        }
    }

    @Test
    public void failedReplacementPreservesPreviousSaveAndRemovesTemporaryFile() throws Exception
    {
        Path file = temporary.getRoot().toPath().resolve("account.json");
        AtomicStateFiles.write(file, "previous save");
        try
        {
            AtomicStateFiles.write(file, "new save", (source, destination) ->
            {
                assertEquals("new save", Files.readString(source));
                assertEquals("previous save", Files.readString(destination));
                throw new IOException("simulated replace failure");
            });
            fail("Write errors must propagate");
        }
        catch (IOException e)
        {
            assertEquals("simulated replace failure", e.getMessage());
        }
        assertEquals("previous save", Files.readString(file));
        try (java.util.stream.Stream<Path> files = Files.list(file.getParent()))
        {
            assertEquals(1, files.count());
        }
    }

    @Test
    public void invalidDestinationReportsFailureWithoutAlteringBlocker() throws Exception
    {
        Path blocker = temporary.newFile("not-a-directory").toPath();
        Files.writeString(blocker, "keep me");
        try
        {
            AtomicStateFiles.write(blocker.resolve("account.json"), "new state");
            fail("Directory failure must propagate");
        }
        catch (IOException expected)
        {
            assertEquals("keep me", Files.readString(blocker));
        }
    }
}
