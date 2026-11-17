package com.devforge.tasks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.db.DatabaseManager;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.tasks.model.Bug;
import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.model.BugStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BugServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir Path tmp;

    private DatabaseManager database;
    private ProjectRegistry registry;
    private BugService service;
    private Project project;

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        database = new DatabaseManager(paths, CLOCK);
        database.initialize();
        registry = new ProjectRegistry(database.connectionProvider(), CLOCK);
        service = new BugService(database.connectionProvider(), registry, CLOCK);
        project = registry.register(Files.createDirectories(tmp.resolve("work/app")), null, null);
    }

    private Bug add(String title, BugSeverity severity) {
        return service.add(project, title, "", severity, "");
    }

    @Test
    void addTrimsTextAndStartsOpen() {
        Bug b = service.add(project, "  Login fails  ", " after expiry ", BugSeverity.HIGH, " 1. log in ");
        assertEquals("Login fails", b.title());
        assertEquals("after expiry", b.description());
        assertEquals("1. log in", b.reproductionSteps());
        assertEquals(BugStatus.OPEN, b.status());
        assertEquals(BugSeverity.HIGH, b.severity());
        assertEquals(b, service.get(b.id()));
    }

    @Test
    void addRejectsBlankAndOverlongInput() {
        assertThrows(BugException.class, () -> add("  ", BugSeverity.LOW));
        assertThrows(BugException.class, () -> add("x".repeat(BugService.MAX_TITLE_LENGTH + 1), BugSeverity.LOW));
        assertThrows(BugException.class, () -> service.add(
                project, "ok", "d".repeat(BugService.MAX_TEXT_LENGTH + 1), BugSeverity.LOW, ""));
    }

    @Test
    void addToArchivedProjectIsRejected() {
        Project archived = registry.archive(project.id());
        BugException e = assertThrows(BugException.class, () -> service.add(archived, "x", "", BugSeverity.LOW, ""));
        assertTrue(e.getMessage().contains("archived"));
    }

    @Test
    void updateChangesOnlyRequestedFields() {
        Bug b = service.add(project, "old", "desc", BugSeverity.LOW, "steps");

        Bug u = service.update(b.id(), new BugChanges("new", null, BugSeverity.CRITICAL, null, null));
        assertEquals("new", u.title());
        assertEquals("desc", u.description());
        assertEquals("steps", u.reproductionSteps());
        assertEquals(BugSeverity.CRITICAL, u.severity());
        assertEquals(BugStatus.OPEN, u.status());
        assertEquals(u, service.get(b.id()));

        Bug v = service.update(b.id(), new BugChanges(null, null, null, BugStatus.RESOLVED, null));
        assertEquals(BugStatus.RESOLVED, v.status());
        assertEquals("new", v.title());
    }

    @Test
    void emptyUpdateIsRejected() {
        Bug b = add("b", BugSeverity.LOW);
        BugException e = assertThrows(BugException.class,
                () -> service.update(b.id(), new BugChanges(null, null, null, null, null)));
        assertTrue(e.getMessage().contains("Nothing to update"));
    }

    @Test
    void missingBugReportsNotFound() {
        assertTrue(assertThrows(BugException.class, () -> service.get(404)).getMessage().contains("not found"));
        assertThrows(BugException.class,
                () -> service.update(404, new BugChanges("x", null, null, null, null)));
    }

    @Test
    void listFiltersByProjectStatusAndSeverity() throws IOException {
        Project other = registry.register(Files.createDirectories(tmp.resolve("work/other")), null, null);
        Bug a = add("a", BugSeverity.HIGH);
        Bug b = add("b", BugSeverity.LOW);
        service.add(other, "c", "", BugSeverity.HIGH, "");
        service.update(b.id(), new BugChanges(null, null, null, BugStatus.CLOSED, null));

        assertEquals(3, service.list(null, null, null).size());
        assertEquals(2, service.list(project.id(), null, null).size());
        assertEquals(List.of(b.id()), ids(service.list(null, BugStatus.CLOSED, null)));
        assertEquals(2, service.list(null, null, BugSeverity.HIGH).size());
        assertEquals(List.of(a.id()), ids(service.list(project.id(), BugStatus.OPEN, BugSeverity.HIGH)));
    }

    @Test
    void operationsWriteActivityLog() throws SQLException {
        Bug b = add("b", BugSeverity.LOW);
        service.update(b.id(), new BugChanges("renamed", null, null, null, null));

        List<String> actions = new ArrayList<>();
        try (Connection c = database.connectionProvider().open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT action FROM activity_logs WHERE action LIKE 'bug.%' ORDER BY id")) {
            while (rs.next()) {
                actions.add(rs.getString(1));
            }
        }
        assertEquals(List.of("bug.add", "bug.update"), actions);
    }

    @Test
    void resolveProjectUsesReferenceOrWorkingDirectory() throws IOException {
        Path inside = Files.createDirectories(tmp.resolve("work/app/src"));
        Path outside = Files.createDirectories(tmp.resolve("elsewhere"));

        assertEquals(project, service.resolveProject("app", outside));
        assertEquals(project, service.resolveProject(null, inside));
        BugException e = assertThrows(BugException.class, () -> service.resolveProject(null, outside));
        assertTrue(e.getMessage().contains("--project"));
    }

    private static List<Long> ids(List<Bug> bugs) {
        return bugs.stream().map(Bug::id).toList();
    }
}
