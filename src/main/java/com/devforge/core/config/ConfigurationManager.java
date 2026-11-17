package com.devforge.core.config;

import com.devforge.core.DevForgeException;
import com.devforge.core.paths.AppPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code config.json} from the configuration directory.
 *
 * <p>Precedence: environment variable {@code DEVFORGE_LOG_LEVEL} (log level only) over the file
 * over built-in defaults. A missing file means defaults; an invalid file is an error.
 *
 * <pre>
 * { "logLevel": "INFO",
 *   "search":   { "maxFileSizeBytes": 1048576, "ignorePaths": ["*.log", "tmp/"] },
 *   "security": { "advisoryCacheHours": 24, "httpTimeoutSeconds": 10 } }
 * </pre>
 */
public final class ConfigurationManager {

    public static final String FILE_NAME = "config.json";
    public static final String LOG_LEVEL_ENV = "DEVFORGE_LOG_LEVEL";
    private static final Set<String> LEVELS = Set.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    private ConfigurationManager() {}

    public static Configuration load(AppPaths paths, Map<String, String> env) {
        Configuration d = Configuration.defaults();
        String level = d.logLevel();
        long maxSize = d.searchMaxFileSizeBytes();
        List<String> ignores = d.searchIgnorePaths();
        int hours = d.advisoryCacheHours();
        int timeout = d.httpTimeoutSeconds();

        Path file = paths.configDir().resolve(FILE_NAME);
        if (Files.isRegularFile(file)) {
            JsonNode root;
            try {
                root = new ObjectMapper().readTree(Files.readString(file));
            } catch (IOException e) {
                throw new DevForgeException("Invalid configuration file " + file + ": not valid JSON.", e);
            }
            if (root == null || !root.isObject()) {
                throw new DevForgeException("Invalid configuration file " + file + ": expected a JSON object.");
            }
            level = text(root.get("logLevel"), "logLevel", level);
            JsonNode search = root.get("search");
            if (search != null) {
                maxSize = number(search.get("maxFileSizeBytes"), "search.maxFileSizeBytes", maxSize, 1024, 100L * 1024 * 1024);
                ignores = strings(search.get("ignorePaths"), "search.ignorePaths");
            }
            JsonNode security = root.get("security");
            if (security != null) {
                hours = (int) number(security.get("advisoryCacheHours"), "security.advisoryCacheHours", hours, 0, 8760);
                timeout = (int) number(security.get("httpTimeoutSeconds"), "security.httpTimeoutSeconds", timeout, 1, 120);
            }
        }
        String envLevel = env.get(LOG_LEVEL_ENV);
        if (envLevel != null && !envLevel.isBlank()) {
            level = envLevel;
        }
        level = level.strip().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw new DevForgeException("Invalid log level '" + level + "'. Use one of " + new ArrayList<>(LEVELS) + ".");
        }
        return new Configuration(level, maxSize, ignores, hours, timeout);
    }

    private static String text(JsonNode node, String key, String fallback) {
        if (node == null) {
            return fallback;
        }
        if (!node.isTextual()) {
            throw new DevForgeException("Invalid configuration: '" + key + "' must be a string.");
        }
        return node.asText();
    }

    private static long number(JsonNode node, String key, long fallback, long min, long max) {
        if (node == null) {
            return fallback;
        }
        long value = node.asLong(-1);
        if (value < min || value > max) {
            throw new DevForgeException("Invalid configuration: '" + key + "' must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static List<String> strings(JsonNode node, String key) {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new DevForgeException("Invalid configuration: '" + key + "' must be an array of strings.");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new DevForgeException("Invalid configuration: '" + key + "' must be an array of strings.");
            }
            out.add(item.asText());
        }
        return List.copyOf(out);
    }
}
