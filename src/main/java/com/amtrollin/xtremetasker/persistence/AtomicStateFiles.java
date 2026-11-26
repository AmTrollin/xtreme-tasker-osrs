package com.amtrollin.xtremetasker.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Replaces a save only after its complete contents have been written to a sibling file. */
public final class AtomicStateFiles
{
    private AtomicStateFiles() { }

    public static void write(Path destination, String json) throws IOException
    {
        write(destination, json, (temporary, target) -> Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    @FunctionalInterface
    interface Replacement
    {
        void replace(Path temporary, Path destination) throws IOException;
    }

    static void write(Path destination, String json, Replacement replacement) throws IOException
    {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName() + ".", ".tmp");
        try
        {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE))
            {
                channel.force(true);
            }
            replacement.replace(temporary, destination);
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }
}
