package com.devforge.search.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.config.Configuration;
import com.devforge.core.db.DatabaseManager;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.search.SearchException;
import com.devforge.search.model.DuplicateGroup;
import com.devforge.search.model.DuplicateReport;
import com.devforge.search.model.IndexStatus;
import com.devforge.search.model.IndexSummary;
import com.devforge.search.model.SearchHit;
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

class IndexServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);

    @TempDir Path tmp;

    private DatabaseManager database;
    private ProjectRegistry registry;
    private IndexService service;
    private Project project;
    private Path root;

    private void write(String rel, String content) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private IndexService serviceWith(Configuration config) {
        return new IndexService(database.connectionProvider(), registry, config, CLOCK);
    }

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        database = new DatabaseManager(paths, CLOCK);
        database.initialize();
        registry = new ProjectRegistry(database.connectionProvider(), CLOCK);
        service = serviceWith(Configuration.defaults());
        root = Files.createDirectories(tmp.resolve("work/app"));
        project = registry.register(root, null, null);
        write("src/Auth.java", "class AuthService { void authentication() {} }");
        write("README.md", "Database connection pool settings");
        write(".env", "SECRET=topsecretvalue");
        write("node_modules/dep/index.js", "authentication from a dependency");
        Files.write(root.resolve("image.png"), new byte[] {1, 2, 3, 0, 4});
    }

    private int count(String sql) throws SQLException {
        try (Connection c = database.connectionProvider().open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.getInt(1);
        }
    }

    @Test
    void indexAddsFilesAndOnlyTextContentIsSearchable() {
        IndexSummary s = service.index(project);
        assertEquals(3, s.added()); // Auth.java, README.md, image.png (no .env, no node_modules)
        assertEquals(2, s.contentIndexed());
        assertEquals(0, s.updated());
        assertEquals(0, s.removed());

        List<SearchHit> hits = service.search("authentication", null, null, 20);
        assertEquals(1, hits.size());
        assertEquals("src/Auth.java", hits.get(0).path());
        assertEquals("content", hits.get(0).matchType());
        assertEquals("app", hits.get(0).project());
        assertTrue(hits.get(0).snippet().contains("[authentication]"));
    }

    @Test
    void multiWordSearchRequiresAllWordsAndIsCaseInsensitive() {
        service.index(project);
        assertEquals(1, service.search("DATABASE connection", null, null, 20).size());
        assertTrue(service.search("database kangaroo", null, null, 20).isEmpty());
    }

    @Test
    void secretsAndDependencyDirectoriesAreNeverIndexed() {
        service.index(project);
        assertTrue(service.search("topsecretvalue", null, null, 20).isEmpty());
        assertTrue(service.search("dependency", null, null, 20).isEmpty());
        assertTrue(service.search(".env", null, null, 20).isEmpty());
    }

    @Test
    void filenamesAreSearchable() {
        service.index(project);
        List<SearchHit> hits = service.search("Auth.java", null, null, 20);
        assertTrue(hits.stream().anyMatch(h -> h.path().equals("src/Auth.java")));
        assertTrue(service.search("image.png", null, null, 20).stream().anyMatch(h -> h.matchType().equals("name")));
    }

    @Test
    void extensionAndProjectFilters() throws IOException {
        service.index(project);
        assertEquals(1, service.search("pool", null, "md", 20).size());
        assertEquals(1, service.search("pool", null, ".MD", 20).size());
        assertTrue(service.search("pool", null, "java", 20).isEmpty());
        assertEquals(1, service.search("pool", project.id(), null, 20).size());
        assertTrue(service.search("pool", project.id() + 99, null, 20).isEmpty());
    }

    @Test
    void searchSpansProjects() throws IOException {
        Path other = Files.createDirectories(tmp.resolve("work/other"));
        Files.writeString(other.resolve("notes.txt"), "authentication notes");
        Project p2 = registry.register(other, null, null);
        service.index(project);
        service.index(p2);
        List<SearchHit> hits = service.search("authentication", null, null, 20);
        assertEquals(2, hits.size());
        assertEquals(1, service.search("authentication", p2.id(), null, 20).size());
    }

    @Test
    void incrementalIndexingTracksChangesAdditionsAndDeletions() throws IOException {
        service.index(project);

        IndexSummary same = service.index(project);
        assertEquals(0, same.added() + same.updated() + same.removed());
        assertEquals(3, same.unchanged());

        write("src/Auth.java", "class AuthService { void login() { /* totally different content now */ } }");
        write("src/New.java", "class Brandnew {}");
        Files.delete(root.resolve("README.md"));
        IndexSummary s = service.index(project);
        assertEquals(1, s.added());
        assertEquals(1, s.updated());
        assertEquals(1, s.removed());

        assertTrue(service.search("authentication", null, null, 20).isEmpty());
        assertEquals(1, service.search("login", null, null, 20).size());
        assertEquals(1, service.search("Brandnew", null, null, 20).size());
        assertTrue(service.search("pool", null, null, 20).isEmpty());
    }

    @Test
    void rebuildGivesTheSameResultsAsIncrementalIndexing() {
        service.index(project);
        IndexSummary rebuilt = service.rebuild(project);
        assertEquals(3, rebuilt.added());
        assertEquals(0, rebuilt.unchanged());
        assertEquals(1, service.search("authentication", null, null, 20).size());
    }

    @Test
    void largeFilesAreListedButTheirContentIsNotIndexed() throws IOException {
        IndexService small = serviceWith(new Configuration("WARN", 1024, List.of(), 24, 10));
        write("big.txt", "needleinhaystack " + "x".repeat(5000));
        IndexSummary s = small.index(project);
        assertEquals(4, s.added());
        assertEquals(2, s.contentIndexed()); // big.txt skipped
        assertTrue(small.search("needleinhaystack", null, null, 20).isEmpty());
        assertEquals(4, small.status(project).files());
    }

    @Test
    void configuredIgnorePatternsApply() throws IOException {
        IndexService svc = serviceWith(new Configuration("WARN", 1024 * 1024, List.of("*.md"), 24, 10));
        IndexSummary s = svc.index(project);
        assertEquals(2, s.added());
        assertTrue(svc.search("pool", null, null, 20).isEmpty());
    }

    @Test
    void statusReportsTotalsAndLastIndexTime() {
        IndexStatus before = service.status(project);
        assertEquals(0, before.files());
        assertNull(before.lastIndexedAt());

        service.index(project);
        IndexStatus after = service.status(project);
        assertEquals(3, after.files());
        assertEquals(2, after.contentIndexed());
        assertEquals(Instant.parse("2026-11-13T00:00:00Z"), after.lastIndexedAt());
        assertTrue(after.totalBytes() > 0);
    }

    @Test
    void duplicatesAreFoundByContentHashForTextAndBinaryFilesAndNothingIsDeleted() throws IOException {
        write("a/one.txt", "same content");
        write("b/two.txt", "same content");
        write("c/other.txt", "diff content"); // same size, different hash
        Files.write(root.resolve("pic1.bin"), new byte[] {9, 8, 7, 0, 6});
        Files.write(root.resolve("pic2.bin"), new byte[] {9, 8, 7, 0, 6});
        service.index(project);

        DuplicateReport report = service.duplicates(null);
        assertEquals(2, report.groups().size());
        DuplicateGroup text = report.groups().stream().filter(g -> g.size() == 12).findFirst().orElseThrow();
        assertEquals(List.of("app:a/one.txt", "app:b/two.txt"), text.locations());
        assertEquals(64, text.sha256().length());
        DuplicateGroup binary = report.groups().stream().filter(g -> g.size() == 5).findFirst().orElseThrow();
        assertEquals(List.of("app:pic1.bin", "app:pic2.bin"), binary.locations());
        assertEquals(12 + 5, report.wastedBytes());
        assertEquals(0, report.unreadable());

        assertTrue(Files.exists(root.resolve("a/one.txt")));
        assertTrue(Files.exists(root.resolve("b/two.txt")));
        assertEquals(1, service.duplicates(project.id()).groups().stream().filter(g -> g.size() == 12).count());
    }

    @Test
    void duplicateDetectionToleratesFilesDeletedAfterIndexing() throws IOException {
        Files.write(root.resolve("x1.bin"), new byte[] {1, 1, 1, 0});
        Files.write(root.resolve("x2.bin"), new byte[] {1, 1, 1, 0});
        service.index(project);
        Files.delete(root.resolve("x2.bin"));
        DuplicateReport report = service.duplicates(null);
        assertEquals(1, report.unreadable());
        assertTrue(report.groups().isEmpty());
    }

    @Test
    void removedProjectsLeaveNoOrphanedSearchContent() throws IOException, SQLException {
        service.index(project);
        assertTrue(count("SELECT COUNT(*) FROM file_contents") > 0);

        registry.remove(project.id());
        assertEquals(0, count("SELECT COUNT(*) FROM file_index"));

        Path other = Files.createDirectories(tmp.resolve("work/other"));
        Files.writeString(other.resolve("n.txt"), "x");
        service.index(registry.register(other, null, null)); // any index run purges orphans
        assertEquals(1, count("SELECT COUNT(*) FROM file_contents"));
    }

    @Test
    void blankAndHostileQueriesAreSafe() {
        service.index(project);
        assertThrows(SearchException.class, () -> service.search("   ", null, null, 20));
        assertTrue(service.search("foo\" OR \"bar", null, null, 20).isEmpty());
        assertTrue(service.search("authentication NEAR(", null, null, 20).isEmpty());
        assertEquals(1, service.search("authentication", null, null, 1).size());
    }

    @Test
    void indexingAMissingDirectoryFailsCleanly() throws IOException {
        Path gone = Files.createDirectories(tmp.resolve("work/gone"));
        Project p = registry.register(gone, null, null);
        Files.delete(gone);
        assertThrows(SearchException.class, () -> service.index(p));
    }

    @Test
    void resolveProjectUsesReferenceOrContainingDirectory() throws IOException {
        assertEquals(project, service.resolveProject("app", tmp));
        assertEquals(project, service.resolveProject(null, root.resolve("src")));
        assertThrows(SearchException.class, () -> service.resolveProject(null, tmp.resolve("elsewhere")));
        assertFalse(service.search("zzz", null, null, 5).size() > 0);
    }
}
