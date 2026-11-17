package com.devforge.core.project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Appends rows to {@code activity_logs}. Never pass secrets or file contents as details. */
public final class ActivityLogRepository {

    public void record(
            Connection c, Long projectId, String module, String action, String details, Instant now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO activity_logs (project_id, module, action, details, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            if (projectId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setLong(1, projectId);
            }
            ps.setString(2, module);
            ps.setString(3, action);
            ps.setString(4, details);
            ps.setString(5, now.truncatedTo(ChronoUnit.SECONDS).toString());
            ps.executeUpdate();
        }
    }
}
