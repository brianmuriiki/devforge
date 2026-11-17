package com.devforge.core.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Opens SQLite connections with DevForge's mandatory settings applied. */
public final class SqliteConnectionProvider implements ConnectionProvider {

    /** How long a connection waits for a lock held by another connection. */
    public static final int BUSY_TIMEOUT_MS = 5000;

    private final String url;

    public SqliteConnectionProvider(Path databaseFile) {
        Objects.requireNonNull(databaseFile, "databaseFile");
        this.url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
    }

    @Override
    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try {
            configure(connection);
            return connection;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Order matters: set the timeout first so the journal-mode change can wait for a lock.
            st.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");

            try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new SQLException("SQLite foreign key enforcement could not be enabled");
                }
            }
        }
    }
}
