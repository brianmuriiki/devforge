package com.devforge.environment.engine;

import com.devforge.core.files.SensitiveFiles;
import com.devforge.environment.VaultException;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * Decides which files a backup profile covers. Only files inside the user's home directory are
 * eligible, symbolic links are never followed or archived, and secret-looking files are excluded
 * unless sensitive inclusion was explicitly requested.
 */
public final class FileSelector {

    public static final int MAX_FILES = 200_000;
    public static final long MAX_BYTES = 4L * 1024 * 1024 * 1024;

    public record SelectedFile(Path absolute, String relative, long size) {}

    /**
     * @param sensitiveExcluded relative paths left out because they look like secrets
     * @param sensitiveIncluded relative paths that look like secrets but were included on request
     * @param missing           configured include paths that do not exist
     */
    public record Selection(
            List<SelectedFile> files, List<String> sensitiveExcluded, List<String> sensitiveIncluded,
            List<String> missing, int symlinksSkipped, int unreadable) {

        public long totalBytes() {
            long sum = 0;
            for (SelectedFile f : files) {
                sum += f.size();
            }
            return sum;
        }
    }

    private FileSelector() {}

    /**
     * Resolves a configured include path: {@code ~} and {@code ~/x} mean the home directory, relative
     * paths are taken relative to home.
     *
     * @throws VaultException if the path is outside the home directory or is the home directory itself
     */
    public static Path resolveInclude(Path home, String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw new VaultException("A path must not be empty.");
        }
        Path normalizedHome = home.toAbsolutePath().normalize();
        Path resolved;
        try {
            if (text.equals("~")) {
                resolved = normalizedHome;
            } else if (text.startsWith("~/") || text.startsWith("~\\")) {
                resolved = normalizedHome.resolve(text.substring(2));
            } else {
                Path p = Path.of(text);
                resolved = p.isAbsolute() ? p : normalizedHome.resolve(p);
            }
        } catch (InvalidPathException e) {
            throw new VaultException("Invalid path: " + text);
        }
        resolved = resolved.toAbsolutePath().normalize();
        if (!resolved.startsWith(normalizedHome)) {
            throw new VaultException("Only paths inside your home directory (" + normalizedHome
                    + ") can be backed up: " + text);
        }
        if (resolved.equals(normalizedHome)) {
            throw new VaultException("Choose specific files or directories instead of the whole home directory.");
        }
        return resolved;
    }

    /** @throws VaultException if {@code pattern} is not a valid glob */
    public static void validateExcludePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new VaultException("An exclude pattern must not be empty.");
        }
        try {
            FileSystems.getDefault().getPathMatcher("glob:" + pattern.strip());
        } catch (PatternSyntaxException | UnsupportedOperationException e) {
            throw new VaultException("Invalid exclude pattern: " + pattern);
        }
    }

    public static Selection select(Path home, List<String> includes, List<String> excludes, boolean includeSensitive) {
        Path normalizedHome = home.toAbsolutePath().normalize();
        List<PathMatcher> excluded = new ArrayList<>();
        for (String pattern : excludes) {
            try {
                excluded.add(FileSystems.getDefault().getPathMatcher("glob:" + pattern));
            } catch (PatternSyntaxException | UnsupportedOperationException e) {
                throw new VaultException("Invalid exclude pattern: " + pattern);
            }
        }
        Map<String, SelectedFile> selected = new LinkedHashMap<>();
        List<String> sensitiveExcluded = new ArrayList<>();
        List<String> sensitiveIncluded = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int[] counters = {0, 0}; // symlinks, unreadable

        for (String raw : includes) {
            Path start = resolveInclude(normalizedHome, raw);
            if (Files.isSymbolicLink(start)) {
                counters[0]++;
                continue;
            }
            if (!Files.exists(start)) {
                missing.add(raw);
                continue;
            }
            try {
                Files.walkFileTree(start, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String rel = relative(normalizedHome, dir);
                        if (matches(excluded, rel, dir.getFileName().toString())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        if (SensitiveFiles.isSecretDirectory(dir.getFileName().toString())) {
                            if (!includeSensitive) {
                                sensitiveExcluded.add(rel + "/");
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            sensitiveIncluded.add(rel + "/");
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isSymbolicLink()) {
                            counters[0]++;
                            return FileVisitResult.CONTINUE;
                        }
                        if (!attrs.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }
                        String name = file.getFileName().toString();
                        String rel = relative(normalizedHome, file);
                        if (matches(excluded, rel, name)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (SensitiveFiles.looksLikeCredentialName(name)) {
                            if (!includeSensitive) {
                                sensitiveExcluded.add(rel);
                                return FileVisitResult.CONTINUE;
                            }
                            sensitiveIncluded.add(rel);
                        }
                        selected.putIfAbsent(rel, new SelectedFile(file, rel, attrs.size()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        counters[1]++;
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new VaultException("Could not read " + start + ": " + e.getMessage(), e);
            }
        }
        long total = 0;
        for (SelectedFile f : selected.values()) {
            total += f.size();
        }
        if (selected.size() > MAX_FILES || total > MAX_BYTES) {
            throw new VaultException("The selection is too large for one backup (limit " + MAX_FILES
                    + " files or 4 GiB). Narrow the profile's paths or add exclude patterns.");
        }
        return new Selection(List.copyOf(selected.values()), sensitiveExcluded, sensitiveIncluded, missing,
                counters[0], counters[1]);
    }

    private static boolean matches(List<PathMatcher> matchers, String rel, String name) {
        if (matchers.isEmpty()) {
            return false;
        }
        Path relPath = Path.of(rel);
        Path namePath = Path.of(name);
        for (PathMatcher m : matchers) {
            if (m.matches(relPath) || m.matches(namePath)) {
                return true;
            }
        }
        return false;
    }

    private static String relative(Path home, Path path) {
        return home.relativize(path).toString().replace(File.separatorChar, '/');
    }
}
