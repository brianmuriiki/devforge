package com.devforge.core.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.devforge.core.db.DatabaseManager;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectRegistryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);

    @TempDir Path tmp;

    private DatabaseManager database;
    private ProjectRegistry registry;

    @BeforeEach
    void setUp() {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        database = new DatabaseManager(paths, CLOCK);
        database.initialize();
        registry = new ProjectRegistry(database.connectionProvider(), CLOCK);
    }

    private Path dir(String relative) throws IOException {
        return Files.createDirectories(tmp.resolve("work").resolve(relative));
    }

    @Test
    void registersCanonicalPathWithDefaultName() throws IOException {
        Path dir = dir("my-app");
        Project p = registry.register(dir, null, null);

        assertEquals("my-app", p.name());
        assertEquals(dir.toRealPath().toString(), p.rootPath());
        assertEquals(ProjectStatus.ACTIVE, p.status());
        assertEquals("", p.description());
        assertEquals(Instant.parse("2026-11-13T00:00:00Z"), p.createdAt());
        assertEquals(1, registry.count());
    }

    @Test
    void usesExplicitNameAndDescription() throws IOException {
        Project p = registry.register(dir("x"), "  Shop  ", "  online store ");
        assertEquals("Shop", p.name());
        assertEquals("online store", p.description());
    }

    @Test
    void registeringSameDirectoryTwiceIsRejected() throws IOException {
        Path dir = dir("app");
        registry.register(dir, null, null);

        ProjectException e = assertThrows(ProjectException.class, () -> registry.register(dir, "other", null));
        assertTrue(e.getMessage().contains("already registered"));
        assertTrue(e.getMessage().contains("'app'"));
        assertEquals(1, registry.count());
    }

    @Test
    void differentSpellingOfSameDirectoryIsRejected() throws IOException {
        Path dir = dir("app");
        dir("app/sub");
        registry.register(dir, null, null);

        Path roundabout = dir.resolve("sub").resolve("..");
        ProjectException e =
                assertThrows(ProjectException.class, () -> registry.register(roundabout, "alias", null));
        assertTrue(e.getMessage().contains("already registered"));
    }

    @Test
    void symlinkToRegisteredDirectoryIsRejected() throws IOException {
        Path real = dir("real");
        Path link = tmp.resolve("work").resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        registry.register(real, null, null);

        ProjectException e = assertThrows(ProjectException.class, () -> registry.register(link, "via-link", null));
        assertTrue(e.getMessage().contains("already registered"));
    }

    @Test
    void nameCollisionIsCaseInsensitive() throws IOException {
        registry.register(dir("one"), "My-App", null);

        ProjectException e = assertThrows(ProjectException.class, () -> registry.register(dir("two"), "my-app", null));
        assertTrue(e.getMessage().contains("already exists"));
        assertTrue(e.getMessage().contains("--name"));
    }

    @Test
    void missingDirectoryIsRejected() {
        ProjectException e = assertThrows(
                ProjectException.class, () -> registry.register(tmp.resolve("nope"), null, null));
        assertTrue(e.getMessage().contains("does not exist"));
    }

    @Test
    void regularFileIsRejected() throws IOException {
        Path file = Files.writeString(dir("f").resolve("a.txt"), "hello");
        ProjectException e = assertThrows(ProjectException.class, () -> registry.register(file, null, null));
        assertTrue(e.getMessage().contains("Not a directory"));
    }

    @Test
    void blankNameIsRejected() throws IOException {
        Path dir = dir("blank");
        assertThrows(ProjectException.class, () -> registry.register(dir, "   ", null));
        assertEquals(0, registry.count());
    }

    @Test
    void listIsOrderedById() throws IOException {
        registry.register(dir("b"), null, null);
        registry.register(dir("a"), null, null);

        List<Project> projects = registry.list();
        assertEquals(List.of("b", "a"), projects.stream().map(Project::name).toList());
        assertTrue(projects.get(0).id() < projects.get(1).id());
    }

    @Test
    void registrationWritesActivityLog() throws IOException, SQLException {
        Project p = registry.register(dir("logged"), null, null);

        try (Connection c = database.connectionProvider().open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT project_id, module, action FROM activity_logs")) {
            assertTrue(rs.next());
            assertEquals(p.id(), rs.getLong(1));
            assertEquals("core", rs.getString(2));
            assertEquals("project.register", rs.getString(3));
        }
    }

    @Test
    void resolveFindsByIdOrCaseInsensitiveName() throws IOException {
        Project p = registry.register(dir("shop"), "Shop", null);
        assertEquals(p, registry.resolve(String.valueOf(p.id())));
        assertEquals(p, registry.resolve("shop"));
        assertEquals(p, registry.resolve("  SHOP "));
    }

    @Test
    void resolvePrefersIdButFallsBackToNumericName() throws IOException {
        registry.register(dir("first"), "2", null);   // id 1, named "2"
        registry.register(dir("second"), "p2", null); // id 2
        assertEquals("p2", registry.resolve("2").name());   // id wins
        assertEquals("2", registry.resolve("1").name());    // id 1 exists
        registry.register(dir("third"), "42", null);        // id 3, named "42"
        assertEquals("42", registry.resolve("42").name());  // no id 42: fall back to the name
    }

    @Test
    void resolveUnknownProjectFails() {
        ProjectException e = assertThrows(ProjectException.class, () -> registry.resolve("ghost"));
        assertTrue(e.getMessage().contains("No project found"));
        assertThrows(ProjectException.class, () -> registry.resolve("  "));
    }

    @Test
    void findContainingPicksDeepestRegisteredProject() throws IOException {
        Path inner = dir("a/b/src");
        dir("a/x");
        dir("other");
        Project outerProject = registry.register(tmp.resolve("work/a"), null, null);
        Project innerProject = registry.register(tmp.resolve("work/a/b"), null, null);

        assertEquals(innerProject, registry.findContaining(inner).orElseThrow());
        assertEquals(outerProject, registry.findContaining(tmp.resolve("work/a/x")).orElseThrow());
        assertTrue(registry.findContaining(tmp.resolve("work/other")).isEmpty());
        assertTrue(registry.findContaining(tmp.resolve("work/missing")).isEmpty());
    }

    @Test
    void updateChangesNameAndDescriptionOnly() throws IOException {
        Project p = registry.register(dir("a"), "Alpha", "old");
        Project u = registry.update(p.id(), "Beta", "new");

        assertEquals("Beta", u.name());
        assertEquals("new", u.description());
        assertEquals(p.rootPath(), u.rootPath());
        assertEquals(u, registry.resolve("Beta"));
    }

    @Test
    void updateWithNothingIsRejected() throws IOException {
        Project p = registry.register(dir("a"), null, null);
        ProjectException e = assertThrows(ProjectException.class, () -> registry.update(p.id(), null, null));
        assertTrue(e.getMessage().contains("Nothing to update"));
    }

    @Test
    void renameToExistingNameIsRejectedCaseInsensitively() throws IOException {
        registry.register(dir("a"), "Alpha", null);
        Project second = registry.register(dir("b"), "Bravo", null);

        ProjectException e = assertThrows(ProjectException.class, () -> registry.update(second.id(), "ALPHA", null));
        assertTrue(e.getMessage().contains("already exists"));
        assertEquals("Bravo", registry.resolve(String.valueOf(second.id())).name());
    }

    @Test
    void changingOnlyTheLetterCaseOfOwnNameIsAllowed() throws IOException {
        Project p = registry.register(dir("a"), "Alpha", null);
        assertEquals("alpha", registry.update(p.id(), "alpha", null).name());
    }

    @Test
    void archiveAndUnarchiveToggleStatus() throws IOException {
        Project p = registry.register(dir("a"), null, null);

        assertEquals(ProjectStatus.ARCHIVED, registry.archive(p.id()).status());
        ProjectException again = assertThrows(ProjectException.class, () -> registry.archive(p.id()));
        assertTrue(again.getMessage().contains("already archived"));

        assertEquals(ProjectStatus.ACTIVE, registry.unarchive(p.id()).status());
        assertThrows(ProjectException.class, () -> registry.unarchive(p.id()));
    }

    @Test
    void removeDeletesRegistryEntryButNeverTheDirectory() throws IOException, SQLException {
        Path d = dir("gone");
        Path keep = Files.writeString(d.resolve("keep.txt"), "precious");
        Project p = registry.register(d, null, null);

        Project removed = registry.remove(p.id());

        assertEquals(p.id(), removed.id());
        assertEquals(0, registry.count());
        assertTrue(Files.exists(keep));
        assertEquals("precious", Files.readString(keep));
        try (Connection c = database.connectionProvider().open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT project_id, details FROM activity_logs WHERE action = 'project.remove'")) {
            assertTrue(rs.next());
            rs.getLong(1);
            assertTrue(rs.wasNull());
            assertTrue(rs.getString(2).contains("name=gone"));
        }
    }

    @Test
    void removeUnknownProjectFails() {
        ProjectException e = assertThrows(ProjectException.class, () -> registry.remove(999));
        assertTrue(e.getMessage().contains("No project found"));
    }
}
