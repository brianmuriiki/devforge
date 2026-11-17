package com.devforge.core.paths;

import java.util.Locale;

/** Operating systems DevForge supports. */
public enum OperatingSystem {
    LINUX,
    WINDOWS,
    MACOS;

    /**
     * Maps a value of the {@code os.name} system property to a supported OS.
     * Anything that is not Windows or macOS is treated as Linux-like (XDG conventions).
     */
    public static OperatingSystem fromOsName(String osName) {
        String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        // macOS must be checked first: "darwin" contains "win".
        if (name.contains("mac") || name.contains("darwin")) {
            return MACOS;
        }
        if (name.contains("win")) {
            return WINDOWS;
        }
        return LINUX;
    }

    public static OperatingSystem current() {
        return fromOsName(System.getProperty("os.name"));
    }
}
