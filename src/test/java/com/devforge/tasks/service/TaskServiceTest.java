package com.devforge.tasks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.db.DatabaseException;
import com.devforge.core.db.DatabaseManager;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.core.project.ProjectStatus;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStats;
import com.devforge.tasks.model.TaskStatus;
import com.devforge.tasks.repo.TaskSort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir Path tmp;

    private DatabaseManager database;
    private ProjectRegistry registry;
    private TaskService service;
    private Project project;

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        database = new DatabaseManager(paths, CLOCK);
        database.initialize();
        registry = new ProjectRegistry(database.connectionProvider(), CLOCK);
        service = new TaskService(database.connectionProvider(), registry, CLOCK, ZoneOffset.UTC);
        project = registry.register(Files.createDirectories(tmp.resolve("work/app")), null, null);
    }

    private Task add(String title) {
        return service.add(project, title, "", TaskPriority.MEDIUM, null);
    }

    @Test
    void addTrimsTextAndStartsAsTodo() {
        Task t = service.add(project, "  Build authentication  ", "  notes ", TaskPriority.HIGH, null);
        assertEquals("Build authentication", t.title());
        assertEquals("notes", t.description());
        assertEquals(TaskStatus.TODO, t.status());
        assertEquals(TaskPriority.HIGH, t.priority());
        assertEquals(t, service.get(t.id()));
    }

    @Test
    void addRejectsBlankAndOverlongTitles() {
        assertThrows(TaskException.class, () -> add("   "));
        assertThrows(TaskException.class, () -> add(null));
        assertThrows(TaskException.class, () -> add("x".repeat(TaskService.MAX_TITLE_LENGTH + 1)));
        assertEquals(TaskService.MAX_TITLE_LENGTH,
                add("x".repeat(TaskService.MAX_TITLE_LENGTH)).title().length());
    }

    @Test
    void addToUnknownProjectFailsWithoutLeavingData() {
        Project ghost = new Project(999, "ghost", "/ghost", "", ProjectStatus.ACTIVE, NOW, NOW);
        assertThrows(DatabaseException.class,
                () -> service.add(ghost, "x", "", TaskPriority.LOW, null));
        assertTrue(service.list(null, null, null, TaskSort.ID).isEmpty());
    }

    @Test
    void parseDueDateAcceptsIsoDatesAndRejectsGarbage() {
        assertEquals(LocalDate.parse("2026-12-31"), TaskService.parseDueDate(" 2026-12-31 "));
        assertThrows(TaskException.class, () -> TaskService.parseDueDate("2026-02-30"));
        assertThrows(TaskException.class, () -> TaskService.parseDueDate("tomorrow"));
        assertThrows(TaskException.class, () -> TaskService.parseDueDate("31/12/2026"));
        assertThrows(TaskException.class, () -> TaskService.parseDueDate("+12345-01-01"));
    }

    @Test
    void updateChangesOnlyRequestedFields() {
        Task t = service.add(project, "old", "desc", TaskPriority.LOW, LocalDate.parse("2030-01-01"));

        Task u = service.update(t.id(), new TaskChanges("new", null, TaskPriority.CRITICAL, null, null, false));
        assertEquals("new", u.title());
        assertEquals("desc", u.description());
        assertEquals(TaskPriority.CRITICAL, u.priority());
        assertEquals(LocalDate.parse("2030-01-01"), u.dueDate());
        assertEquals(TaskStatus.TODO, u.status());
        assertEquals(u, service.get(t.id()));

        Task v = service.update(t.id(), new TaskChanges(null, null, null, TaskStatus.BLOCKED, null, false));
        assertEquals(TaskStatus.BLOCKED, v.status());
        assertEquals("new", v.title());
    }

    @Test
    void updateCanSetAndClearDueDate() {
        Task t = add("t");
        Task set = service.update(t.id(), new TaskChanges(null, null, null, null, LocalDate.parse("2030-05-01"), false));
        assertEquals(LocalDate.parse("2030-05-01"), set.dueDate());
        Task cleared = service.update(t.id(), new TaskChanges(null, null, null, null, null, true));
        assertNull(cleared.dueDate());
        assertNull(service.get(t.id()).dueDate());
    }

    @Test
    void updateWithNoChangesIsRejected() {
        Task t = add("t");
        TaskException e = assertThrows(TaskException.class,
                () -> service.update(t.id(), new TaskChanges(null, null, null, null, null, false)));
        assertTrue(e.getMessage().contains("Nothing to update"));
    }

    @Test
    void operationsOnMissingTaskReportNotFound() {
        TaskChanges change = new TaskChanges("x", null, null, null, null, false);
        for (Runnable op : List.<Runnable>of(
                () -> service.get(404),
                () -> service.update(404, change),
                () -> service.complete(404),
                () -> service.reopen(404))) {
            TaskException e = assertThrows(TaskException.class, op::run);
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    void completeAndReopenManageCompletionTime() {
        Task t = add("t");
        Task done = service.complete(t.id());
        assertEquals(TaskStatus.DONE, done.status());
        assertEquals(NOW, done.completedAt());
        assertEquals(done, service.get(t.id()));

        Task reopened = service.reopen(t.id());
        assertEquals(TaskStatus.TODO, reopened.status());
        assertNull(reopened.completedAt());
        assertEquals(reopened, service.get(t.id()));
    }

    @Test
    void completingTwiceAndReopeningUnfinishedTaskAreRejected() {
        Task t = add("t");
        TaskException notDone = assertThrows(TaskException.class, () -> service.reopen(t.id()));
        assertTrue(notDone.getMessage().contains("not completed"));

        service.complete(t.id());
        TaskException twice = assertThrows(TaskException.class, () -> service.complete(t.id()));
        assertTrue(twice.getMessage().contains("already completed"));
    }

    @Test
    void listFiltersAndSorts() throws IOException {
        Project other = registry.register(Files.createDirectories(tmp.resolve("work/other")), null, null);
        service.add(project, "a", "", TaskPriority.LOW, LocalDate.parse("2031-01-01"));
        Task b = service.add(project, "b", "", TaskPriority.HIGH, null);
        service.add(other, "c", "", TaskPriority.HIGH, LocalDate.parse("2030-01-01"));
        service.complete(b.id());

        assertEquals(List.of("a", "b"), titles(service.list(project.id(), null, null, TaskSort.ID)));
        assertEquals(List.of("b"), titles(service.list(null, TaskStatus.DONE, null, TaskSort.ID)));
        assertEquals(List.of("c", "b"), titles(service.list(null, null, TaskPriority.HIGH, TaskSort.DUE_DATE)));
        assertEquals(List.of("c", "a", "b"), titles(service.list(null, null, null, TaskSort.DUE_DATE)));
    }

    @Test
    void operationsWriteActivityLog() throws SQLException {
        Task t = add("t");
        service.complete(t.id());

        List<String> actions = new ArrayList<>();
        try (Connection c = database.connectionProvider().open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT action FROM activity_logs WHERE module = 'tasks' ORDER BY id")) {
            while (rs.next()) {
                actions.add(rs.getString(1));
            }
        }
        assertEquals(List.of("task.add", "task.complete"), actions);
    }

    @Test
    void todayFollowsConfiguredZone() {
        // 2026-11-13T00:00Z is still the previous evening in New York.
        TaskService newYork = new TaskService(
                database.connectionProvider(), registry, CLOCK, ZoneId.of("America/New_York"));
        assertEquals(LocalDate.parse("2026-11-12"), newYork.today());
        assertEquals(LocalDate.parse("2026-11-13"), service.today());
    }

    @Test
    void resolveProjectUsesReferenceOrWorkingDirectory() throws IOException {
        Path inside = Files.createDirectories(tmp.resolve("work/app/src"));
        Path outside = Files.createDirectories(tmp.resolve("elsewhere"));

        assertEquals(project, service.resolveProject(String.valueOf(project.id()), outside));
        assertEquals(project, service.resolveProject("APP", outside));
        assertEquals(project, service.resolveProject(null, inside));

        TaskException e = assertThrows(TaskException.class, () -> service.resolveProject(null, outside));
        assertTrue(e.getMessage().contains("--project"));
    }

    @Test
    void deleteRemovesTaskAndReportsWhatWasDeleted() {
        Task t = add("t");
        assertEquals(t, service.delete(t.id()));
        assertThrows(TaskException.class, () -> service.get(t.id()));
        assertThrows(TaskException.class, () -> service.delete(t.id()));
    }

    @Test
    void addingToArchivedProjectIsRejected() {
        Project archived = registry.archive(project.id());
        TaskException e = assertThrows(TaskException.class,
                () -> service.add(archived, "x", "", TaskPriority.LOW, null));
        assertTrue(e.getMessage().contains("archived"));
    }

    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::title).toList();
    }

    @Test
    void statsSummarizeProgress() {
        service.add(project, "a", "", TaskPriority.HIGH, LocalDate.parse("2020-01-01")); // overdue
        Task b = service.add(project, "b", "", TaskPriority.LOW, null);
        Task c = service.add(project, "c", "", TaskPriority.HIGH, null);
        service.complete(b.id());
        service.update(c.id(), new TaskChanges(null, null, null, TaskStatus.IN_PROGRESS, null, false));

        TaskStats s = service.stats(project.id());
        assertEquals(3, s.total());
        assertEquals(1, s.todo());
        assertEquals(1, s.inProgress());
        assertEquals(0, s.blocked());
        assertEquals(1, s.done());
        assertEquals(1, s.overdue());
        assertEquals(33.3, s.completionPercent(), 0.001);
        assertEquals(2, (int) s.byPriority().get(TaskPriority.HIGH));
        assertEquals(1, (int) s.byPriority().get(TaskPriority.LOW));
        assertEquals(0, (int) s.byPriority().get(TaskPriority.MEDIUM));
    }

    @Test
    void statsOfAnEmptyProjectAreZero() {
        TaskStats s = service.stats(project.id());
        assertEquals(0, s.total());
        assertEquals(0.0, s.completionPercent(), 0.0001);
    }
}
