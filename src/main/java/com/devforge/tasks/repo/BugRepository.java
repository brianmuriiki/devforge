package com.devforge.tasks.repo;

import com.devforge.tasks.model.Bug;
import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.model.BugStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL access for the {@code bugs} table. The caller owns the connection and transaction. */
public final class BugRepository {

    private static final String SELECT =
            "SELECT id, project_id, title, description, severity, status, reproduction_steps, "
                    + "created_at, updated_at FROM bugs";

    /** Inserts an OPEN bug. Fails with {@link SQLException} for an unknown project or bad data. */
    public Bug insert(
            Connection c,
            long projectId,
            String title,
            String description,
            BugSeverity severity,
            String reproductionSteps,
            Instant now)
            throws SQLException {
        Instant stamp = now.truncatedTo(ChronoUnit.SECONDS);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bugs (project_id, title, description, severity, status, "
                        + "reproduction_steps, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, projectId);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, severity.name());
            ps.setString(5, BugStatus.OPEN.name());
            ps.setString(6, reproductionSteps);
            ps.setString(7, stamp.toString());
            ps.setString(8, stamp.toString());
            ps.executeUpdate();
        }
        long id;
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            id = rs.getLong(1);
        }
        return new Bug(id, projectId, title, description, severity, BugStatus.OPEN,
                reproductionSteps, stamp, stamp);
    }

    public Optional<Bug> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Lists bugs matching all non-null criteria, oldest first. */
    public List<Bug> findAll(Connection c, BugFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (filter.projectId() != null) {
            sql.append(" AND project_id = ?");
            params.add(filter.projectId());
        }
        if (filter.status() != null) {
            sql.append(" AND status = ?");
            params.add(filter.status().name());
        }
        if (filter.severity() != null) {
            sql.append(" AND severity = ?");
            params.add(filter.severity().name());
        }
        sql.append(" ORDER BY id");

        List<Bug> bugs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bugs.add(map(rs));
                }
            }
        }
        return bugs;
    }

    /** Writes every editable field. Returns false if no bug has that id. */
    public boolean update(Connection c, Bug bug) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE bugs SET title = ?, description = ?, severity = ?, status = ?, "
                        + "reproduction_steps = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, bug.title());
            ps.setString(2, bug.description());
            ps.setString(3, bug.severity().name());
            ps.setString(4, bug.status().name());
            ps.setString(5, bug.reproductionSteps());
            ps.setString(6, bug.updatedAt().toString());
            ps.setLong(7, bug.id());
            return ps.executeUpdate() == 1;
        }
    }

    private static Bug map(ResultSet rs) throws SQLException {
        return new Bug(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getString("title"),
                rs.getString("description"),
                BugSeverity.valueOf(rs.getString("severity")),
                BugStatus.valueOf(rs.getString("status")),
                rs.getString("reproduction_steps"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }
}
