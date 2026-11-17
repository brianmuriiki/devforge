package com.devforge.core.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteConnectionProviderTest {

    @TempDir Path tmp;

    private SqliteConnectionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SqliteConnectionProvider(tmp.resolve("test.db"));
    }

    private static String pragma(Connection c, String name) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA " + name)) {
            return rs.getString(1);
        }
    }

    @Test
    void everyConnectionHasForeignKeysOn() throws SQLException {
        for (int i = 0; i < 3; i++) {
            try (Connection c = provider.open()) {
                assertEquals("1", pragma(c, "foreign_keys"));
            }
        }
    }

    @Test
    void usesWalAndBusyTimeout() throws SQLException {
        try (Connection c = provider.open()) {
            assertEquals("wal", pragma(c, "journal_mode"));
            assertEquals(String.valueOf(SqliteConnectionProvider.BUSY_TIMEOUT_MS), pragma(c, "busy_timeout"));
        }
    }

    @Test
    void foreignKeyViolationsAreActuallyRejected() throws SQLException {
        try (Connection c = provider.open();
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
            st.execute("CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER NOT NULL REFERENCES parent(id))");
            assertThrows(SQLException.class, () -> st.executeUpdate("INSERT INTO child (parent_id) VALUES (999)"));
        }
    }

    @Test
    void readerIsNotBlockedByUncommittedWriterAndSeesCommit() throws SQLException {
        try (Connection writer = provider.open();
                Connection reader = provider.open()) {
            try (Statement st = writer.createStatement()) {
                st.execute("CREATE TABLE t (id INTEGER)");
            }
            writer.setAutoCommit(false);
            try (Statement st = writer.createStatement()) {
                st.executeUpdate("INSERT INTO t VALUES (1)");
            }
            assertEquals(0, count(reader)); // WAL: uncommitted data is invisible, reader not blocked
            writer.commit();
            assertEquals(1, count(reader));
        }
    }

    private static int count(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM t")) {
            return rs.getInt(1);
        }
    }
}
