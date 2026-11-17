package com.devforge.search.scan;

import java.util.Locale;
import java.util.Set;

/** Decides which directories and files FindX never reads. */
public final class FileClassifier {

    /** Directories that are never entered (VCS data, build output, dependency caches). */
    public static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".hg", ".svn", "node_modules", "target", "build", "dist", "out", ".gradle",
            ".idea", "__pycache__", ".venv", "venv", ".tox", ".next", ".cache", "bower_components",
            ".devforge-recovery");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "webp", "tiff", "pdf", "zip", "gz", "tgz", "bz2",
            "xz", "7z", "rar", "jar", "war", "ear", "class", "so", "dll", "dylib", "exe", "bin", "o", "a",
            "obj", "lib", "mp3", "mp4", "mov", "avi", "mkv", "wav", "flac", "ogg", "ttf", "otf", "woff",
            "woff2", "eot", "sqlite", "db", "pyc", "pyd", "iso", "dmg", "psd");

    private FileClassifier() {}

    public static boolean isExcludedDirectory(String name) {
        return EXCLUDED_DIRECTORIES.contains(name);
    }

    public static boolean hasBinaryExtension(String extension) {
        return BINARY_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    /** Lower-case extension without the dot; empty for names like {@code Makefile} or {@code .gitignore}. */
    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
