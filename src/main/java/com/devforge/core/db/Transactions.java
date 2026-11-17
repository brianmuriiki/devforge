package com.devforge.core.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Runs units of work on short-lived connections, as one transaction when writing. */
public final class Transactions {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final ConnectionProvider provider;

    public Transactions(ConnectionProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /** Runs read-only work on an auto-commit connection. */
    public <T> T read(String failureMessage, Work<T> work) {
        try (Connection c = provider.open()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new DatabaseException(failureMessage, e);
        }
    }

    /**
     * Runs {@code work} in one transaction: commit on success, roll back on any failure.
     * SQL failures become a {@link DatabaseException}; other runtime exceptions pass through.
     */
    public <T> T inTransaction(String failureMessage, Work<T> work) {
        try (Connection c = provider.open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly(c, e);
                throw new DatabaseException(failureMessage, e);
            } catch (RuntimeException e) {
                rollbackQuietly(c, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException(failureMessage, e);
        }
    }

    private static void rollbackQuietly(Connection c, Exception original) {
        try {
            c.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
