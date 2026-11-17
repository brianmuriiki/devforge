package com.devforge.search.scan;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Recursive wrapper over {@link WatchService}. Events are hints, not guarantees: platforms drop
 * events (reported as {@code overflow}) and watch limits can prevent registering some directories
 * ({@link #incomplete()}). Callers must reconcile with a full incremental index.
 */
public final class FileWatcher implements AutoCloseable {

    public record Batch(Set<Path> changed, boolean overflow) {
        public boolean isEmpty() {
            return changed.isEmpty() && !overflow;
        }
    }

    private final WatchService service;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private boolean incomplete;

    public FileWatcher(Path root) throws IOException {
        this.service = FileSystems.getDefault().newWatchService();
        registerTree(root);
    }

    /** True if some directories could not be watched (for example, the inotify limit was reached). */
    public boolean incomplete() {
        return incomplete;
    }

    /** Waits up to {@code timeout} for activity, then collects everything already pending. */
    public Batch poll(Duration timeout) throws InterruptedException {
        Set<Path> changed = new HashSet<>();
        boolean overflow = false;
        try {
            WatchKey key = service.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            while (key != null) {
                overflow |= handle(key, changed);
                key = service.poll();
            }
        } catch (ClosedWatchServiceException e) {
            // Closed while waiting: report whatever was gathered.
        }
        return new Batch(changed, overflow);
    }

    private boolean handle(WatchKey key, Set<Path> changed) {
        boolean overflow = false;
        Path dir = directories.get(key);
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                overflow = true;
                continue;
            }
            if (dir == null) {
                continue;
            }
            Path child = dir.resolve((Path) event.context());
            changed.add(child);
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    registerTree(child);
                } catch (IOException e) {
                    incomplete = true;
                }
            }
        }
        if (!key.reset()) {
            directories.remove(key);
        }
        return overflow;
    }

    private void registerTree(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(start) && FileClassifier.isExcludedDirectory(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                try {
                    WatchKey key = dir.register(
                            service,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    directories.put(key, dir);
                } catch (IOException e) {
                    incomplete = true;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                incomplete = true;
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void close() throws IOException {
        service.close();
    }
}
