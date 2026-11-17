package com.devforge.core.config;

import java.util.List;

/**
 * Validated application settings. Contains no secrets.
 *
 * @param logLevel              ERROR, WARN, INFO, DEBUG or TRACE
 * @param searchMaxFileSizeBytes largest file whose content FindX indexes
 * @param searchIgnorePaths     extra ignore patterns (gitignore syntax) applied by FindX
 * @param advisoryCacheHours    how long cached advisory/registry data is considered fresh
 * @param httpTimeoutSeconds    timeout for each online request made by DepGuard
 */
public record Configuration(
        String logLevel,
        long searchMaxFileSizeBytes,
        List<String> searchIgnorePaths,
        int advisoryCacheHours,
        int httpTimeoutSeconds) {

    public static Configuration defaults() {
        return new Configuration("WARN", 1024 * 1024, List.of(), 24, 10);
    }
}
