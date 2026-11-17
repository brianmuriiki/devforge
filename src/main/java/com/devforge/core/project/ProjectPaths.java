package com.devforge.core.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Turns user-supplied directories into canonical project roots. */
public final class ProjectPaths {

    private ProjectPaths() {}

    /**
     * Resolves {@code input} to an existing directory's real path: relative segments, trailing
     * separators and symbolic links are resolved, so every spelling of a directory maps to the
     * same string.
     *
     * @throws ProjectException if the path is missing, not a directory, or unreadable
     */
    public static Path canonicalDirectory(Path input) {
        Path absolute = input.toAbsolutePath();
        if (!Files.exists(absolute)) {
            throw new ProjectException("Project directory does not exist: " + absolute);
        }
        if (!Files.isDirectory(absolute)) {
            throw new ProjectException("Not a directory: " + absolute);
        }
        try {
            return absolute.toRealPath();
        } catch (IOException e) {
            throw new ProjectException("Cannot access project directory: " + absolute, e);
        }
    }
}
