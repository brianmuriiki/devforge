package com.devforge.tasks.repo;

import com.devforge.tasks.model.Task;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQL access for the {@code tasks} table. The caller owns the connection and transaction. */
public final class TaskRepository {

    private static final String SELECT =
            "SELECT id, project_id, title, description, status, priority, due_date, "
                    + "created_at, updated_at, completed_at FROM tasks";

    /** Inserts a TODO task. Fails with {@link SQLException} for an unknown project or bad data. */
    public Task insert(
            Connection c,
            long projectId,
            String title,
            String description,
            TaskPriority priority,
            LocalDate dueDate,
            Instant now)
            throws SQLException {
        Instant stamp = now.truncatedTo(ChronoUnit.SECONDS);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tasks (project_id, title, description, status, priority, due_date, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, projectId);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, TaskStatus.TODO.name());
            ps.setString(5, priority.name());
            setNullableText(ps, 6, dueDate == null ? null : dueDate.toString());
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
        return new Task(id, projectId, title, description, TaskStatus.TODO, priority, dueDate,
                stamp, stamp, null);
    }

    public Optional<Task> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Lists tasks matching all non-null criteria. Only fixed SQL fragments are concatenated. */
    public List<Task> findAll(Connection c, TaskFilter filter, TaskSort sort) throws SQLException {
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
        if (filter.priority() != null) {
            sql.append(" AND priority = ?");
            params.add(filter.priority().name());
        }
        sql.append(sort == TaskSort.DUE_DATE
                ? " ORDER BY due_date IS NULL, due_date, id"
                : " ORDER BY id");

        List<Task> tasks = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(map(rs));
                }
            }
        }
        return tasks;
    }

    /** Writes every editable field of the task. Returns false if no task has that id. */
    public boolean update(Connection c, Task task) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE tasks SET title = ?, description = ?, status = ?, priority = ?, "
                        + "due_date = ?, updated_at = ?, completed_at = ? WHERE id = ?")) {
            ps.setString(1, task.title());
            ps.setString(2, task.description());
            ps.setString(3, task.status().name());
            ps.setString(4, task.priority().name());
            setNullableText(ps, 5, task.dueDate() == null ? null : task.dueDate().toString());
            ps.setString(6, task.updatedAt().toString());
            setNullableText(ps, 7, task.completedAt() == null ? null : task.completedAt().toString());
            ps.setLong(8, task.id());
            return ps.executeUpdate() == 1;
        }
    }

    /** Returns false if no task has that id. */
    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private static void setNullableText(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static Task map(ResultSet rs) throws SQLException {
        String due = rs.getString("due_date");
        String completed = rs.getString("completed_at");
        return new Task(
                rs.getLong("id"),
                rs.getLong("project_id"),
                rs.getString("title"),
                rs.getString("description"),
                TaskStatus.valueOf(rs.getString("status")),
                TaskPriority.valueOf(rs.getString("priority")),
                due == null ? null : LocalDate.parse(due),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")),
                completed == null ? null : Instant.parse(completed));
    }
}
