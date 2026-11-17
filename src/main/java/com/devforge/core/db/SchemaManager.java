package com.devforge.core.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies pending schema migrations and records them in {@code schema_version}.
 *
 * <p>All pending migrations run in one transaction: either the database reaches the latest
 * version or it is left exactly as it was.
 */
public final class SchemaManager {

    /** Migrations shipped with this release. Append only; never edit an applied entry. */
    public static final List<Migration> DEFAULT_MIGRATIONS = List.of(
            new Migration(1, "core", "/db/migration/V1__core.sql"),
            new Migration(2, "taskforge", "/db/migration/V2__taskforge.sql"),
            new Migration(3, "findx", "/db/migration/V3__findx.sql"),
            new Migration(4, "depguard", "/db/migration/V4__depguard.sql"),
            new Migration(5, "devvault", "/db/migration/V5__devvault.sql"),
            new Migration(6, "encrypted_backups", "/db/migration/V6__encrypted_backups.sql"));

    private static final String CREATE_VERSION_TABLE =
            """
            CREATE TABLE IF NOT EXISTS schema_version (
                version     INTEGER PRIMARY KEY,
                description TEXT NOT NULL,
                applied_at  TEXT NOT NULL
            )""";

    private final List<Migration> migrations;
    private final Clock clock;

    public SchemaManager(Clock clock) {
        this(DEFAULT_MIGRATIONS, clock);
    }

    public SchemaManager(List<Migration> migrations, Clock clock) {
        this.migrations = List.copyOf(Objects.requireNonNull(migrations, "migrations"));
        this.clock = Objects.requireNonNull(clock, "clock");
        for (int i = 0; i < this.migrations.size(); i++) {
            if (this.migrations.get(i).version() != i + 1) {
                throw new IllegalArgumentException(
                        "Migrations must be numbered 1..n without gaps; found version "
                                + this.migrations.get(i).version() + " at position " + (i + 1));
            }
        }
    }

    /** Highest schema version this build knows about. */
    public int latestVersion() {
        return migrations.size();
    }

    /**
     * Brings the database up to {@link #latestVersion()}.
     *
     * @param connection an auto-commit connection (as returned by {@link ConnectionProvider})
     * @return the schema version after migrating
     * @throws DatabaseException if the database is newer than this build or a migration fails
     */
    public int migrate(Connection connection) throws SQLException {
        int current = readVersion(connection);
        rejectNewer(current);
        if (current == latestVersion()) {
            return current; // fast path: no write lock needed
        }

        try (Statement st = connection.createStatement()) {
            st.execute("BEGIN IMMEDIATE");
            try {
                st.execute(CREATE_VERSION_TABLE);
                // Re-read under the lock: another process may have migrated while we waited.
                current = readVersion(connection);
                rejectNewer(current);
                for (Migration migration : migrations) {
                    if (migration.version() > current) {
                        apply(connection, migration);
                    }
                }
                st.execute("COMMIT");
            } catch (SQLException | RuntimeException e) {
                try {
                    st.execute("ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        }
        return latestVersion();
    }

    private void rejectNewer(int databaseVersion) {
        if (databaseVersion > latestVersion()) {
            throw new DatabaseException(
                    "This database (schema version " + databaseVersion
                            + ") was created by a newer version of DevForge; this version supports up to "
                            + latestVersion() + ". Please upgrade DevForge.");
        }
    }

    private void apply(Connection connection, Migration migration) {
        try (Statement st = connection.createStatement()) {
            for (String sql : splitStatements(loadScript(migration))) {
                st.execute(sql);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)")) {
                ps.setInt(1, migration.version());
                ps.setString(2, migration.description());
                ps.setString(3, clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Database migration V" + migration.version() + " (" + migration.description()
                            + ") failed; no schema changes were applied.", e);
        }
    }

    private static int readVersion(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'schema_version'");
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return 0;
            }
        }
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String loadScript(Migration migration) {
        try (InputStream in = SchemaManager.class.getResourceAsStream(migration.resource())) {
            if (in == null) {
                throw new DatabaseException("Migration script is missing: " + migration.resource());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DatabaseException("Could not read migration script: " + migration.resource(), e);
        }
    }

    /**
     * Splits a script into statements. Lines starting with {@code --} are dropped and a statement
     * ends at a line ending in {@code ;}. Triggers (multi-statement bodies) are not supported.
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                addIfNotBlank(statements, current);
            }
        }
        addIfNotBlank(statements, current);
        return statements;
    }

    private static void addIfNotBlank(List<String> statements, StringBuilder current) {
        String sql = current.toString().strip();
        current.setLength(0);
        if (!sql.isEmpty() && !sql.equals(";")) {
            statements.add(sql);
        }
    }
}
