package com.devforge.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.DevForgeException;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationManagerTest {

    @TempDir Path tmp;

    private AppPaths paths;

    @BeforeEach
    void setUp() throws IOException {
        paths = AppPaths.resolve(OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        Files.createDirectories(paths.configDir());
    }

    private void writeConfig(String json) throws IOException {
        Files.writeString(paths.configDir().resolve("config.json"), json);
    }

    @Test
    void missingFileMeansDefaults() {
        assertEquals(Configuration.defaults(), ConfigurationManager.load(paths, Map.of()));
    }

    @Test
    void readsAllSettings() throws IOException {
        writeConfig("""
                { "logLevel": "debug",
                  "search": { "maxFileSizeBytes": 2048, "ignorePaths": ["*.tmp", "cache/"] },
                  "security": { "advisoryCacheHours": 6, "httpTimeoutSeconds": 30 } }""");
        Configuration c = ConfigurationManager.load(paths, Map.of());
        assertEquals("DEBUG", c.logLevel());
        assertEquals(2048, c.searchMaxFileSizeBytes());
        assertEquals(List.of("*.tmp", "cache/"), c.searchIgnorePaths());
        assertEquals(6, c.advisoryCacheHours());
        assertEquals(30, c.httpTimeoutSeconds());
    }

    @Test
    void environmentOverridesLogLevel() throws IOException {
        writeConfig("{ \"logLevel\": \"INFO\" }");
        assertEquals("TRACE", ConfigurationManager.load(paths, Map.of("DEVFORGE_LOG_LEVEL", "trace")).logLevel());
    }

    @Test
    void invalidValuesAreRejectedWithTheKeyName() throws IOException {
        writeConfig("{ \"search\": { \"maxFileSizeBytes\": 5 } }");
        DevForgeException e = assertThrows(DevForgeException.class, () -> ConfigurationManager.load(paths, Map.of()));
        assertTrue(e.getMessage().contains("search.maxFileSizeBytes"));

        writeConfig("{ \"logLevel\": \"LOUD\" }");
        assertTrue(assertThrows(DevForgeException.class, () -> ConfigurationManager.load(paths, Map.of()))
                .getMessage().contains("log level"));
    }

    @Test
    void malformedJsonAndWrongShapesAreRejected() throws IOException {
        writeConfig("{ not json");
        assertTrue(assertThrows(DevForgeException.class, () -> ConfigurationManager.load(paths, Map.of()))
                .getMessage().contains("not valid JSON"));
        writeConfig("[1, 2]");
        assertThrows(DevForgeException.class, () -> ConfigurationManager.load(paths, Map.of()));
        writeConfig("{ \"search\": { \"ignorePaths\": \"oops\" } }");
        assertThrows(DevForgeException.class, () -> ConfigurationManager.load(paths, Map.of()));
    }
}
