package com.devforge.security.parse;

import com.devforge.security.model.Dependency;
import com.devforge.security.model.Ecosystem;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Finds supported dependency manifests inside a project and parses them. */
public final class ManifestFinder {

    /** @param relativePath path from the project root using {@code /} separators */
    public record Manifest(Path path, String relativePath, Ecosystem ecosystem) {}

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", "node_modules", "target", "build", "dist", "out", ".gradle", ".idea", "__pycache__",
            ".venv", "venv", ".tox", ".next", ".cache", "bower_components", ".devforge-recovery");
    private static final int MAX_DEPTH = 8;

    private ManifestFinder() {}

    public static List<Manifest> find(Path root) throws IOException {
        List<Manifest> found = new ArrayList<>();
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && SKIPPED_DIRECTORIES.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
                    return FileVisitResult.CONTINUE;
                }
                Ecosystem eco = switch (file.getFileName().toString()) {
                    case "pom.xml" -> Ecosystem.MAVEN;
                    case "package.json" -> Ecosystem.NPM;
                    case "requirements.txt", "pyproject.toml" -> Ecosystem.PYPI;
                    default -> null;
                };
                if (eco != null) {
                    found.add(new Manifest(file, root.relativize(file).toString().replace(File.separatorChar, '/'), eco));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        found.sort(Comparator.comparing(Manifest::relativePath));
        return found;
    }

    public static List<Dependency> parse(Manifest manifest) throws ManifestException {
        return switch (manifest.ecosystem()) {
            case MAVEN -> PomParser.parse(manifest.path());
            case NPM -> NpmParser.parse(manifest.path());
            case PYPI -> PythonParser.parse(manifest.path());
        };
    }
}
