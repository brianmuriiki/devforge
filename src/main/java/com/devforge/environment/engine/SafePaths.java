package com.devforge.environment.engine;

import com.devforge.environment.VaultException;
import java.nio.file.Path;

/** Validation of archive-supplied paths. Archive contents are untrusted input. */
public final class SafePaths {

    private static final int MAX_LENGTH = 1024;

    private SafePaths() {}

    /** Requires a clean relative path: no absolute paths, drive letters, backslashes, colons, {@code .} or {@code ..}. */
    public static void validateRelative(String rel) {
        if (rel == null || rel.isEmpty() || rel.length() > MAX_LENGTH) {
            throw unsafe(rel);
        }
        for (int i = 0; i < rel.length(); i++) {
            char ch = rel.charAt(i);
            if (ch < 0x20 || ch == 0x7F || ch == '\\' || ch == ':') {
                throw unsafe(rel);
            }
        }
        if (rel.startsWith("/")) {
            throw unsafe(rel);
        }
        for (String segment : rel.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw unsafe(rel);
            }
        }
    }

    /** Resolves {@code rel} below {@code base}; the result is guaranteed to stay inside {@code base}. */
    public static Path resolveUnder(Path base, String rel) {
        validateRelative(rel);
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path target = normalizedBase.resolve(rel).normalize();
        if (!target.startsWith(normalizedBase) || target.equals(normalizedBase)) {
            throw unsafe(rel);
        }
        return target;
    }

    private static VaultException unsafe(String rel) {
        String shown = rel == null ? "null" : rel.replaceAll("[\\p{Cntrl}]", "?");
        if (shown.length() > 80) {
            shown = shown.substring(0, 80) + "...";
        }
        return new VaultException("Unsafe path in archive: '" + shown + "'");
    }
}
