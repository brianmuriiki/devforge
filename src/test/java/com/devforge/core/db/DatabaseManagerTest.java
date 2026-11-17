package com.devforge.core.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseManagerTest {

    @TempDir Path tmp;

    private AppPaths pathsUnder(Path home) {
        return AppPaths.resolve(OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", home.toString()), tmp);
    }

    @Test
    void initializeCreatesDirectoriesAndDatabase() {
        AppPaths paths = pathsUnder(tmp.resolve("home"));
        DatabaseInfo info = new DatabaseManager(paths).initialize();

        assertTrue(Files.isDirectory(paths.dataDir()));
        assertTrue(Files.exists(paths.databasePath()));
        assertEquals(paths.databasePath(), info.path());
        assertEquals("wal", info.journalMode());
        assertTrue(info.foreignKeysEnabled());
        assertNotNull(info.sqliteVersion());
        assertFalse(info.sqliteVersion().isBlank());
    }

    @Test
    void initializeIsIdempotent() {
        DatabaseManager manager = new DatabaseManager(pathsUnder(tmp.resolve("home")));
        manager.initialize();
        manager.initialize();
    }

    @Test
    void failsWithFriendlyExceptionWhenDirectoryCannotBeCreated() throws IOException {
        Path blocker = Files.writeString(tmp.resolve("blocker"), "I am a file");
        DatabaseManager manager = new DatabaseManager(pathsUnder(blocker.resolve("home")));

        DatabaseException e = assertThrows(DatabaseException.class, manager::initialize);
        assertTrue(e.getMessage().contains("data directory"));
    }

    @Test
    void initializeMigratesSchemaToLatest() throws SQLException {
        DatabaseManager manager = new DatabaseManager(pathsUnder(tmp.resolve("home")));
        DatabaseInfo info = manager.initialize();

        assertEquals(6, info.schemaVersion()); // update when you add a migration
        try (Connection c = manager.connectionProvider().open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM projects")) {
            assertEquals(0, rs.getInt(1));
        }
    }
}
