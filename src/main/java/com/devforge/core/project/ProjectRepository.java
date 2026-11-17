package com.devforge.core.project;

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

/** SQL access for the {@code projects} table. The caller owns the connection and transaction. */
public final class ProjectRepository {

    private static final String SELECT =
            "SELECT id, name, root_path, description, status, created_at, updated_at FROM projects";

    /** Inserts an ACTIVE project. Constraint violations surface as {@link SQLException}. */
    public Project insert(Connection c, String name, String rootPath, String description, Instant now)
            throws SQLException {
        Instant stamp = now.truncatedTo(ChronoUnit.SECONDS);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO projects (name, root_path, description, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, rootPath);
            ps.setString(3, description);
            ps.setString(4, ProjectStatus.ACTIVE.name());
            ps.setString(5, stamp.toString());
            ps.setString(6, stamp.toString());
            ps.executeUpdate();
        }
        long id;
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            id = rs.getLong(1);
        }
        return new Project(id, name, rootPath, description, ProjectStatus.ACTIVE, stamp, stamp);
    }

    public Optional<Project> findByRootPath(Connection c, String rootPath) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE root_path = ?")) {
            ps.setString(1, rootPath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Project> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Case-insensitive, because the {@code name} column is declared COLLATE NOCASE. */
    public Optional<Project> findByName(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Project> findAll(Connection c) throws SQLException {
        List<Project> projects = new ArrayList<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(SELECT + " ORDER BY id")) {
            while (rs.next()) {
                projects.add(map(rs));
            }
        }
        return projects;
    }

    public int count(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM projects")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Writes name, description, status and updated_at. Returns false if no project has that id. */
    public boolean update(Connection c, Project p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE projects SET name = ?, description = ?, status = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, p.name());
            ps.setString(2, p.description());
            ps.setString(3, p.status().name());
            ps.setString(4, p.updatedAt().toString());
            ps.setLong(5, p.id());
            return ps.executeUpdate() == 1;
        }
    }

    /** Deletes the registry row (tasks and bugs cascade). Never touches the filesystem. */
    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM projects WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static Project map(ResultSet rs) throws SQLException {
        return new Project(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("root_path"),
                rs.getString("description"),
                ProjectStatus.valueOf(rs.getString("status")),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }
}
