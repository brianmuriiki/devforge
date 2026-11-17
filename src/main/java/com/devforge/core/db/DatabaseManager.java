package com.devforge.core.db;

import com.devforge.core.paths.AppPaths;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Objects;

/** Prepares the database location, migrates the schema and hands out connections. */
public final class DatabaseManager {

    private final AppPaths paths;
    private final ConnectionProvider provider;
    private final SchemaManager schemaManager;

    public DatabaseManager(AppPaths paths) {
        this(paths, Clock.systemUTC());
    }

    public DatabaseManager(AppPaths paths, Clock clock) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.provider = new SqliteConnectionProvider(paths.databasePath());
        this.schemaManager = new SchemaManager(clock);
    }

    public ConnectionProvider connectionProvider() {
        return provider;
    }

    /**
     * Creates directories and the database file if needed, then migrates to the latest schema.
     *
     * @throws DatabaseException with a user-presentable message on failure
     */
    public DatabaseInfo initialize() {
        try {
            paths.ensureDirectories();
        } catch (IOException e) {
            throw new DatabaseException(
                    "Could not create the DevForge data directory: " + paths.dataDir(), e);
        }
        try (Connection connection = provider.open()) {
            int schemaVersion = schemaManager.migrate(connection);
            return describe(connection, paths.databasePath(), schemaVersion);
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Database initialization failed: " + paths.databasePath(), e);
        }
    }

    private static DatabaseInfo describe(Connection connection, Path path, int schemaVersion)
            throws SQLException {
        try (Statement st = connection.createStatement()) {
            String version = singleString(st, "SELECT sqlite_version()");
            String journalMode = singleString(st, "PRAGMA journal_mode");
            boolean foreignKeys = "1".equals(singleString(st, "PRAGMA foreign_keys"));
            return new DatabaseInfo(path, version, journalMode, foreignKeys, schemaVersion);
        }
    }

    private static String singleString(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }
}
