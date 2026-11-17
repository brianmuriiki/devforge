package com.devforge.core.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaManagerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
    private static final Migration A = new Migration(1, "a", "/db/test/V1__a.sql");
    private static final Migration B = new Migration(2, "b", "/db/test/V2__b.sql");
    private static final Migration BROKEN = new Migration(3, "broken", "/db/test/V3__broken.sql");

    @TempDir Path tmp;

    private SqliteConnectionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SqliteConnectionProvider(tmp.resolve("schema.db"));
    }

    private static boolean tableExists(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int versionRows(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            return rs.getInt(1);
        }
    }

    @Test
    void freshDatabaseGetsDefaultSchema() throws SQLException {
        SchemaManager manager = new SchemaManager(CLOCK);
        try (Connection c = provider.open()) {
            assertEquals(manager.latestVersion(), manager.migrate(c));
            assertTrue(tableExists(c, "projects"));
            assertTrue(tableExists(c, "activity_logs"));
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery(
                            "SELECT description, applied_at FROM schema_version WHERE version = 1")) {
                assertTrue(rs.next());
                assertEquals("core", rs.getString(1));
                assertEquals("2026-11-13T00:00:00Z", rs.getString(2));
            }
        }
    }

    @Test
    void migrateIsIdempotent() throws SQLException {
        SchemaManager manager = new SchemaManager(CLOCK);
        try (Connection c = provider.open()) {
            manager.migrate(c);
            assertEquals(manager.latestVersion(), manager.migrate(c));
            assertEquals(manager.latestVersion(), versionRows(c));
        }
    }

    @Test
    void refusesDatabaseFromNewerVersion() throws SQLException {
        try (Connection c = provider.open(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE schema_version (version INTEGER PRIMARY KEY, description TEXT NOT NULL, applied_at TEXT NOT NULL)");
            st.execute("INSERT INTO schema_version VALUES (99, 'future', '2030-01-01T00:00:00Z')");

            DatabaseException e = assertThrows(DatabaseException.class, () -> new SchemaManager(CLOCK).migrate(c));
            assertTrue(e.getMessage().contains("newer version"));
            assertFalse(tableExists(c, "projects"));
        }
    }

    @Test
    void failedMigrationRollsBackEverythingOnFreshDatabase() throws SQLException {
        SchemaManager manager = new SchemaManager(List.of(A, B, BROKEN), CLOCK);
        try (Connection c = provider.open()) {
            DatabaseException e = assertThrows(DatabaseException.class, () -> manager.migrate(c));
            assertTrue(e.getMessage().contains("V3"));
            assertFalse(tableExists(c, "a"));
            assertFalse(tableExists(c, "b"));
            assertFalse(tableExists(c, "c"));
            assertFalse(tableExists(c, "schema_version"));
        }
    }

    @Test
    void upgradeAppliesOnlyPendingMigrations() throws SQLException {
        try (Connection c = provider.open()) {
            assertEquals(1, new SchemaManager(List.of(A), CLOCK).migrate(c));
            assertFalse(tableExists(c, "b"));

            assertEquals(2, new SchemaManager(List.of(A, B), CLOCK).migrate(c));
            assertTrue(tableExists(c, "b"));
            assertEquals(2, versionRows(c));
        }
    }

    @Test
    void failedUpgradeKeepsPreviousVersionIntact() throws SQLException {
        try (Connection c = provider.open()) {
            new SchemaManager(List.of(A), CLOCK).migrate(c);

            assertThrows(DatabaseException.class,
                    () -> new SchemaManager(List.of(A, B, BROKEN), CLOCK).migrate(c));

            assertTrue(tableExists(c, "a"));
            assertFalse(tableExists(c, "b"));
            assertFalse(tableExists(c, "c"));
            assertEquals(1, versionRows(c));
        }
    }

    @Test
    void rejectsMigrationListWithGaps() {
        assertThrows(IllegalArgumentException.class, () -> new SchemaManager(List.of(A, BROKEN), CLOCK));
    }

    @Test
    void splitStatementsHandlesCommentsAndMultilineStatements() {
        String script = """
                -- a comment
                CREATE TABLE t (
                    id INTEGER -- trailing note
                );
                CREATE INDEX i ON t (id);
                """;
        List<String> statements = SchemaManager.splitStatements(script);
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE t"));
        assertTrue(statements.get(1).startsWith("CREATE INDEX i"));
    }

    @Test
    void v1ConstraintsAreEnforced() throws SQLException {
        try (Connection c = provider.open(); Statement st = c.createStatement()) {
            new SchemaManager(CLOCK).migrate(c);
            st.executeUpdate("INSERT INTO projects (name, root_path, created_at, updated_at) "
                    + "VALUES ('My-App', '/p/one', '2026-11-13T00:00:00Z', '2026-11-13T00:00:00Z')");

            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO projects (name, root_path, created_at, updated_at) "
                            + "VALUES ('other', '/p/one', 'x', 'x')")); // duplicate path
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO projects (name, root_path, created_at, updated_at) "
                            + "VALUES ('my-app', '/p/two', 'x', 'x')")); // name differs only by case
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO projects (name, root_path, status, created_at, updated_at) "
                            + "VALUES ('x', '/p/three', 'WEIRD', 'x', 'x')")); // bad status
        }
    }

    @Test
    void deletingProjectKeepsActivityHistory() throws SQLException {
        try (Connection c = provider.open(); Statement st = c.createStatement()) {
            new SchemaManager(CLOCK).migrate(c);
            st.executeUpdate("INSERT INTO projects (id, name, root_path, created_at, updated_at) "
                    + "VALUES (1, 'p', '/p', 'x', 'x')");
            st.executeUpdate("INSERT INTO activity_logs (project_id, module, action, created_at) "
                    + "VALUES (1, 'core', 'init', 'x')");

            st.executeUpdate("DELETE FROM projects WHERE id = 1");

            try (ResultSet rs = st.executeQuery("SELECT project_id FROM activity_logs")) {
                assertTrue(rs.next());
                rs.getInt(1);
                assertTrue(rs.wasNull());
            }
        }
    }
}
