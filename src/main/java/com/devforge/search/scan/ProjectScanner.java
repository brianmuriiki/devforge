package com.devforge.search.scan;

import com.devforge.core.files.SensitiveFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Walks a project and lists the files FindX may consider. Symbolic links are never followed;
 * VCS/build/dependency directories, secret files and ignored paths are skipped.
 */
public final class ProjectScanner {

    public record ScannedFile(String relativePath, Path absolutePath, long size, Instant modifiedAt, String extension) {}

    public record ScanResult(List<ScannedFile> files, int unreadable) {}

    private ProjectScanner() {}

    public static ScanResult scan(Path root, IgnoreRules rules) throws IOException {
        List<ScannedFile> files = new ArrayList<>();
        int[] unreadable = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString();
                if (FileClassifier.isExcludedDirectory(name)
                        || SensitiveFiles.isSecretDirectory(name)
                        || (rules.isIgnored(relative(root, dir), true)
                                && !rules.mayReincludeDescendant(relative(root, dir)))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                String name = file.getFileName().toString();
                String rel = relative(root, file);
                if (SensitiveFiles.isSecretFileName(name) || rules.isIgnored(rel, false)) {
                    return FileVisitResult.CONTINUE;
                }
                files.add(new ScannedFile(
                        rel,
                        file,
                        attrs.size(),
                        Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()),
                        FileClassifier.extensionOf(name)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                unreadable[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(ScannedFile::relativePath));
        return new ScanResult(files, unreadable[0]);
    }

    static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }
}
