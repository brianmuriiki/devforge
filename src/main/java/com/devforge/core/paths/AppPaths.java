package com.devforge.core.paths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolved, absolute directories used by DevForge.
 *
 * <p>Use {@link #system()} in production and {@link #resolve} in tests.
 */
public record AppPaths(Path dataDir, Path configDir, Path logDir, Path cacheDir) {

    /** Environment variable that relocates all DevForge directories. */
    public static final String HOME_OVERRIDE_VAR = "DEVFORGE_HOME";

    public static final String DATABASE_FILE_NAME = "devforge.db";

    public AppPaths {
        dataDir = normalize(dataDir, "dataDir");
        configDir = normalize(configDir, "configDir");
        logDir = normalize(logDir, "logDir");
        cacheDir = normalize(cacheDir, "cacheDir");
    }

    /** Location of the SQLite database file. */
    public Path databasePath() {
        return dataDir.resolve(DATABASE_FILE_NAME);
    }

    /** Resolves paths for the machine DevForge is running on. */
    public static AppPaths system() {
        return resolve(
                OperatingSystem.current(),
                System.getenv(),
                Path.of(System.getProperty("user.home")));
    }

    /**
     * Resolves paths from explicit inputs, without touching the real environment.
     *
     * @param os       target operating system
     * @param env      environment variables
     * @param userHome the user's home directory
     */
    public static AppPaths resolve(OperatingSystem os, Map<String, String> env, Path userHome) {
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(userHome, "userHome");

        Optional<Path> override = pathFromEnv(env, HOME_OVERRIDE_VAR);
        if (override.isPresent()) {
            Path base = override.get();
            return new AppPaths(
                    base.resolve("data"),
                    base.resolve("config"),
                    base.resolve("logs"),
                    base.resolve("cache"));
        }
        return switch (os) {
            case LINUX -> linux(env, userHome);
            case MACOS -> macos(userHome);
            case WINDOWS -> windows(env, userHome);
        };
    }

    /** Creates all directories if they do not exist. Safe to call repeatedly. */
    public void ensureDirectories() throws IOException {
        for (Path dir : new Path[] {dataDir, configDir, logDir, cacheDir}) {
            Files.createDirectories(dir);
        }
    }

    private static AppPaths linux(Map<String, String> env, Path home) {
        Path data = xdg(env, "XDG_DATA_HOME", home.resolve(".local/share")).resolve("devforge");
        Path config = xdg(env, "XDG_CONFIG_HOME", home.resolve(".config")).resolve("devforge");
        Path cache = xdg(env, "XDG_CACHE_HOME", home.resolve(".cache")).resolve("devforge");
        Path logs = xdg(env, "XDG_STATE_HOME", home.resolve(".local/state"))
                .resolve("devforge")
                .resolve("logs");
        return new AppPaths(data, config, logs, cache);
    }

    private static AppPaths macos(Path home) {
        Path library = home.resolve("Library");
        Path support = library.resolve("Application Support").resolve("DevForge");
        return new AppPaths(
                support,
                support,
                library.resolve("Logs").resolve("DevForge"),
                library.resolve("Caches").resolve("DevForge"));
    }

    private static AppPaths windows(Map<String, String> env, Path home) {
        Path local = pathFromEnv(env, "LOCALAPPDATA").orElse(home.resolve("AppData").resolve("Local"));
        Path roaming = pathFromEnv(env, "APPDATA").orElse(home.resolve("AppData").resolve("Roaming"));
        Path data = local.resolve("DevForge");
        return new AppPaths(
                data, roaming.resolve("DevForge"), data.resolve("logs"), data.resolve("cache"));
    }

    /** XDG rule: the variable is honored only if it holds an absolute path. */
    private static Path xdg(Map<String, String> env, String variable, Path fallback) {
        return pathFromEnv(env, variable).orElse(fallback);
    }

    /** Returns the variable as an absolute path, or empty if unset, blank, invalid or relative. */
    private static Optional<Path> pathFromEnv(Map<String, String> env, String variable) {
        String value = env.get(variable);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(value.trim());
            return path.isAbsolute() ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static Path normalize(Path path, String name) {
        Objects.requireNonNull(path, name);
        return path.toAbsolutePath().normalize();
    }
}
