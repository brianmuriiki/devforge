package com.devforge.core.paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

    @TempDir Path tmp;

    @Test
    void overrideRelocatesEverythingOnAnyOs() {
        Path base = tmp.resolve("custom");
        for (OperatingSystem os : OperatingSystem.values()) {
            AppPaths p = AppPaths.resolve(os, Map.of("DEVFORGE_HOME", base.toString()), tmp);
            assertEquals(base.resolve("data"), p.dataDir());
            assertEquals(base.resolve("config"), p.configDir());
            assertEquals(base.resolve("logs"), p.logDir());
            assertEquals(base.resolve("cache"), p.cacheDir());
        }
    }

    @Test
    void blankOrRelativeOverrideIsIgnored() {
        AppPaths blank = AppPaths.resolve(OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", "  "), tmp);
        AppPaths relative = AppPaths.resolve(OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", "rel/dir"), tmp);
        Path expected = tmp.resolve(".local/share/devforge");
        assertEquals(expected, blank.dataDir());
        assertEquals(expected, relative.dataDir());
    }

    @Test
    void linuxDefaultsFollowXdgFallbacks() {
        AppPaths p = AppPaths.resolve(OperatingSystem.LINUX, Map.of(), tmp);
        assertEquals(tmp.resolve(".local/share/devforge"), p.dataDir());
        assertEquals(tmp.resolve(".config/devforge"), p.configDir());
        assertEquals(tmp.resolve(".cache/devforge"), p.cacheDir());
        assertEquals(tmp.resolve(".local/state/devforge/logs"), p.logDir());
        assertEquals(tmp.resolve(".local/share/devforge/devforge.db"), p.databasePath());
    }

    @Test
    void linuxHonorsAbsoluteXdgVariables() {
        Path data = tmp.resolve("xdg-data");
        AppPaths p = AppPaths.resolve(OperatingSystem.LINUX, Map.of("XDG_DATA_HOME", data.toString()), tmp);
        assertEquals(data.resolve("devforge"), p.dataDir());
    }

    @Test
    void linuxIgnoresRelativeXdgVariables() {
        AppPaths p = AppPaths.resolve(OperatingSystem.LINUX, Map.of("XDG_DATA_HOME", "relative"), tmp);
        assertEquals(tmp.resolve(".local/share/devforge"), p.dataDir());
    }

    @Test
    void macosUsesLibraryDirectories() {
        AppPaths p = AppPaths.resolve(OperatingSystem.MACOS, Map.of(), tmp);
        Path support = tmp.resolve("Library/Application Support/DevForge");
        assertEquals(support, p.dataDir());
        assertEquals(support, p.configDir());
        assertEquals(tmp.resolve("Library/Logs/DevForge"), p.logDir());
        assertEquals(tmp.resolve("Library/Caches/DevForge"), p.cacheDir());
    }

    @Test
    void windowsUsesLocalAndRoamingAppData() {
        Path local = tmp.resolve("Local");
        Path roaming = tmp.resolve("Roaming");
        AppPaths p = AppPaths.resolve(
                OperatingSystem.WINDOWS,
                Map.of("LOCALAPPDATA", local.toString(), "APPDATA", roaming.toString()),
                tmp);
        assertEquals(local.resolve("DevForge"), p.dataDir());
        assertEquals(roaming.resolve("DevForge"), p.configDir());
        assertEquals(local.resolve("DevForge/logs"), p.logDir());
        assertEquals(local.resolve("DevForge/cache"), p.cacheDir());
    }

    @Test
    void windowsFallsBackToHomeWhenVariablesMissing() {
        AppPaths p = AppPaths.resolve(OperatingSystem.WINDOWS, Map.of(), tmp);
        assertEquals(tmp.resolve("AppData/Local/DevForge"), p.dataDir());
        assertEquals(tmp.resolve("AppData/Roaming/DevForge"), p.configDir());
    }

    @Test
    void ensureDirectoriesCreatesAllAndIsIdempotent() throws IOException {
        AppPaths p = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        p.ensureDirectories();
        p.ensureDirectories();
        assertTrue(Files.isDirectory(p.dataDir()));
        assertTrue(Files.isDirectory(p.configDir()));
        assertTrue(Files.isDirectory(p.logDir()));
        assertTrue(Files.isDirectory(p.cacheDir()));
    }
}
