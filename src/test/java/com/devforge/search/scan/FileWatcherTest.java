package com.devforge.search.scan;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileWatcherTest {

    @TempDir Path tmp;

    private static Set<Path> waitForChanges(FileWatcher w, Path expected) throws InterruptedException {
        Set<Path> all = new HashSet<>();
        for (int i = 0; i < 8 && !all.contains(expected); i++) {
            all.addAll(w.poll(Duration.ofSeconds(2)).changed());
        }
        return all;
    }

    @Test
    void reportsNewFilesInRootAndInNewSubdirectories() throws IOException, InterruptedException {
        assumeTrue(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"),
                "WatchService polls on macOS and is too slow for a unit test");
        try (FileWatcher w = new FileWatcher(tmp)) {
            Path first = Files.writeString(tmp.resolve("a.txt"), "x");
            assertTrue(waitForChanges(w, first).contains(first));

            Path dir = Files.createDirectory(tmp.resolve("sub"));
            waitForChanges(w, dir);
            Path nested = Files.writeString(dir.resolve("b.txt"), "y");
            assertTrue(waitForChanges(w, nested).contains(nested));
        }
    }

    @Test
    void quietDirectoryProducesEmptyBatch() throws IOException, InterruptedException {
        try (FileWatcher w = new FileWatcher(tmp)) {
            assertTrue(w.poll(Duration.ofMillis(100)).isEmpty());
        }
    }
}
