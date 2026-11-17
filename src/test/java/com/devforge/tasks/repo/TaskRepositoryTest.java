package com.devforge.tasks.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.db.DatabaseManager;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRepository;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStatus;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");

    @TempDir Path tmp;

    private final TaskRepository tasks = new TaskRepository();
    private Connection c;
    private Project project;

    @BeforeEach
    void setUp() throws SQLException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        DatabaseManager database = new DatabaseManager(paths);
        database.initialize();
        c = database.connectionProvider().open();
        project = new ProjectRepository().insert(c, "p1", "/p1", "", NOW);
    }

    @AfterEach
    void tearDown() throws SQLException {
        c.close();
    }

    private Task add(String title, TaskPriority priority, LocalDate due) throws SQLException {
        return tasks.insert(c, project.id(), title, "", priority, due, NOW);
    }

    private int count(String table) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getInt(1);
        }
    }

    @Test
    void insertCreatesTodoTaskWithDefaults() throws SQLException {
        Task t = add("Build authentication", TaskPriority.HIGH, null);

        assertTrue(t.id() > 0);
        assertEquals(TaskStatus.TODO, t.status());
        assertNull(t.completedAt());
        assertEquals(t, tasks.findById(c, t.id()).orElseThrow());
        assertTrue(tasks.findById(c, 9999).isEmpty());
    }

    @Test
    void dueDateRoundTrips() throws SQLException {
        Task t = add("Ship", TaskPriority.LOW, LocalDate.parse("2026-12-24"));
        assertEquals(LocalDate.parse("2026-12-24"), tasks.findById(c, t.id()).orElseThrow().dueDate());
    }

    @Test
    void insertForUnknownProjectFails() {
        assertThrows(SQLException.class,
                () -> tasks.insert(c, 12345, "orphan", "", TaskPriority.LOW, null, NOW));
    }

    @Test
    void blankTitleIsRejectedByDatabase() {
        assertThrows(SQLException.class, () -> add("   ", TaskPriority.LOW, null));
    }

    @Test
    void findAllFiltersByProjectStatusAndPriority() throws SQLException {
        Project other = new ProjectRepository().insert(c, "p2", "/p2", "", NOW);
        Task a = add("a", TaskPriority.HIGH, null);
        Task b = add("b", TaskPriority.LOW, null);
        tasks.update(c, b.withStatus(TaskStatus.DONE, NOW));
        tasks.insert(c, other.id(), "c", "", TaskPriority.HIGH, null, NOW);

        assertEquals(3, tasks.findAll(c, TaskFilter.none(), TaskSort.ID).size());
        assertEquals(2, tasks.findAll(c, new TaskFilter(project.id(), null, null), TaskSort.ID).size());
        assertEquals(List.of(b.id()), ids(tasks.findAll(c, new TaskFilter(null, TaskStatus.DONE, null), TaskSort.ID)));
        assertEquals(2, tasks.findAll(c, new TaskFilter(null, null, TaskPriority.HIGH), TaskSort.ID).size());
        assertEquals(List.of(a.id()),
                ids(tasks.findAll(c, new TaskFilter(project.id(), TaskStatus.TODO, TaskPriority.HIGH), TaskSort.ID)));
    }

    @Test
    void dueDateSortPutsTasksWithoutDueDateLast() throws SQLException {
        add("later", TaskPriority.LOW, LocalDate.parse("2026-12-01"));
        add("none", TaskPriority.LOW, null);
        add("sooner", TaskPriority.LOW, LocalDate.parse("2026-11-20"));

        List<String> titles = tasks.findAll(c, TaskFilter.none(), TaskSort.DUE_DATE)
                .stream().map(Task::title).toList();
        assertEquals(List.of("sooner", "later", "none"), titles);
    }

    @Test
    void updatePersistsChanges() throws SQLException {
        Task t = add("old", TaskPriority.LOW, null);
        Task changed = t.withDetails("new", "details", TaskPriority.CRITICAL, LocalDate.parse("2026-12-31"), NOW.plusSeconds(60))
                .withStatus(TaskStatus.DONE, NOW.plusSeconds(120));

        assertTrue(tasks.update(c, changed));
        assertEquals(changed, tasks.findById(c, t.id()).orElseThrow());

        Task reopened = changed.withStatus(TaskStatus.IN_PROGRESS, NOW.plusSeconds(180));
        tasks.update(c, reopened);
        assertNull(tasks.findById(c, t.id()).orElseThrow().completedAt());
    }

    @Test
    void updateOfMissingTaskReturnsFalse() throws SQLException {
        Task ghost = new Task(777, project.id(), "x", "", TaskStatus.TODO, TaskPriority.LOW, null, NOW, NOW, null);
        assertFalse(tasks.update(c, ghost));
    }

    @Test
    void databaseRejectsDoneWithoutCompletionTimestamp() throws SQLException {
        Task t = add("x", TaskPriority.LOW, null);
        try (PreparedStatement ps = c.prepareStatement("UPDATE tasks SET status = 'DONE' WHERE id = ?")) {
            ps.setLong(1, t.id());
            assertThrows(SQLException.class, ps::executeUpdate);
        }
    }

    @Test
    void deleteRemovesTask() throws SQLException {
        Task t = add("x", TaskPriority.LOW, null);
        assertTrue(tasks.delete(c, t.id()));
        assertFalse(tasks.delete(c, t.id()));
        assertTrue(tasks.findById(c, t.id()).isEmpty());
    }

    @Test
    void deletingProjectCascadesToTasksAndBugs() throws SQLException {
        add("x", TaskPriority.LOW, null);
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO bugs (project_id, title, created_at, updated_at) VALUES ("
                    + project.id() + ", 'b', 'x', 'x')");
            assertEquals(1, count("tasks"));
            assertEquals(1, count("bugs"));

            st.executeUpdate("DELETE FROM projects WHERE id = " + project.id());
        }
        assertEquals(0, count("tasks"));
        assertEquals(0, count("bugs"));
    }

    @Test
    void bugConstraintsAreEnforced() throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO bugs (project_id, title, severity, created_at, updated_at) VALUES ("
                    + project.id() + ", 'ok', 'HIGH', 'x', 'x')");
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO bugs (project_id, title, severity, created_at, updated_at) VALUES ("
                            + project.id() + ", 'bad', 'WEIRD', 'x', 'x')"));
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "INSERT INTO bugs (project_id, title, status, created_at, updated_at) VALUES ("
                            + project.id() + ", 'bad', 'DONE', 'x', 'x')"));
            try (ResultSet rs = st.executeQuery("SELECT status FROM bugs")) {
                assertTrue(rs.next());
                assertEquals("OPEN", rs.getString(1)); // default
            }
        }
    }

    private static List<Long> ids(List<Task> list) {
        return list.stream().map(Task::id).toList();
    }
}
