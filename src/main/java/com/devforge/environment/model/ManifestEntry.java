package com.devforge.environment.model;

/**
 * One archived file.
 *
 * @param path   path relative to the home directory, using {@code /} separators
 * @param mode   POSIX permissions such as {@code rw-r--r--}, or null where unsupported
 */
public record ManifestEntry(String path, long size, String sha256, String mode) {}
