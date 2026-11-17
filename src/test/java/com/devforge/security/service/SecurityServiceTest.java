package com.devforge.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.config.Configuration;
import com.devforge.core.db.DatabaseManager;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.security.model.Advisory;
import com.devforge.security.model.CheckStatus;
import com.devforge.security.model.Dependency;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.PackageMetadata;
import com.devforge.security.model.ScanStatus;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.model.Severity;
import com.devforge.security.provider.AdvisoryProvider;
import com.devforge.security.provider.MetadataProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-13T00:00:00Z");

    /** Scripted advisory source: per-package answers, optional failure, call log. */
    private static final class FakeAdvisories implements AdvisoryProvider {
        final Map<String, List<Advisory>> answers = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        boolean failing;

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public List<Advisory> advisoriesFor(Dependency d) throws IOException {
            calls.add(d.name() + "@" + d.resolvedVersion());
            if (failing) {
                throw new IOException("network down");
            }
            return answers.getOrDefault(d.name(), List.of());
        }
    }

    private static final class FakeMetadata implements MetadataProvider {
        final Map<String, PackageMetadata> answers = new HashMap<>();
        boolean failing;

        @Override
        public PackageMetadata lookup(Dependency d) throws IOException {
            if (failing) {
                throw new IOException("network down");
            }
            return answers.getOrDefault(d.name(), new PackageMetadata(null, null));
        }
    }

    private static Advisory advisory(String id, Severity s) {
        return new Advisory(id, "problem " + id, s, List.of("9.9.9"), "fixed 9.9.9", List.of("https://e.com/" + id), List.of(), "fake");
    }

    @TempDir Path tmp;

    private DatabaseManager database;
    private ProjectRegistry registry;
    private Project project;
    private Path root;
    private FakeAdvisories advisories;
    private FakeMetadata metadata;

    private SecurityService service(Instant now, int cacheHours) {
        return new SecurityService(database.connectionProvider(),
                new Configuration("WARN", 1024 * 1024, List.of(), cacheHours, 10),
                Clock.fixed(now, ZoneOffset.UTC), advisories, metadata);
    }

    private SecurityService service() {
        return service(T0, 24);
    }

    private void write(String rel, String content) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        database = new DatabaseManager(paths, Clock.fixed(T0, ZoneOffset.UTC));
        database.initialize();
        registry = new ProjectRegistry(database.connectionProvider(), Clock.fixed(T0, ZoneOffset.UTC));
        root = Files.createDirectories(tmp.resolve("work/app"));
        project = registry.register(root, null, null);
        advisories = new FakeAdvisories();
        metadata = new FakeMetadata();
        write("package.json", "{ \"dependencies\": { \"left-pad\": \"1.3.0\" } }");
    }

    private int count(String sql) throws SQLException {
        try (Connection c = database.connectionProvider().open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.getInt(1);
        }
    }

    @Test
    void cleanScanOfCheckedDependenciesIsComplete() {
        metadata.answers.put("left-pad", new PackageMetadata("1.3.0", "MIT"));
        List<ManifestScan> scans = service().scan(project, false);

        assertEquals(1, scans.size());
        ManifestScan m = scans.get(0);
        assertEquals(ScanStatus.COMPLETE, m.scan().status());
        assertEquals("package.json", m.scan().manifestPath());
        assertEquals(CheckStatus.CHECKED, m.dependencies().get(0).checkStatus());
        assertEquals("MIT", m.dependencies().get(0).license());
        assertEquals(0, SecurityReports.exitCode(service().report(project)));
    }

    @Test
    void findingsAreStoredWithSeverityAndGiveExitCodeThree() {
        advisories.answers.put("left-pad", List.of(advisory("GHSA-1", Severity.HIGH), advisory("GHSA-2", Severity.LOW)));
        ManifestScan m = service().scan(project, false).get(0);

        assertEquals(ScanStatus.COMPLETE_WITH_FINDINGS, m.scan().status());
        assertEquals(2, m.vulnerabilities().size());
        SecurityReport r = service().report(project);
        assertEquals(2, r.vulnerabilityCount());
        assertEquals(1, (int) r.bySeverity().get(Severity.HIGH));
        assertEquals(3, SecurityReports.exitCode(r));
        assertEquals("GHSA-1", service().latestScans(project).get(0).vulnerabilities().get(0).advisoryId());
    }

    @Test
    void unresolvedVersionsMakeTheScanIncompleteNeverClean() throws IOException {
        write("package.json", "{ \"dependencies\": { \"left-pad\": \"^1.0.0\" } }");
        ManifestScan m = service().scan(project, false).get(0);

        assertEquals(ScanStatus.INCOMPLETE, m.scan().status());
        assertEquals(CheckStatus.UNRESOLVED, m.dependencies().get(0).checkStatus());
        assertTrue(m.scan().message().contains("no exact version"));
        assertTrue(advisories.calls.isEmpty());
        assertEquals(4, SecurityReports.exitCode(service().report(project)));
    }

    @Test
    void advisoryOutageWithoutCacheIsUnavailableNeverClean() {
        advisories.failing = true;
        ManifestScan m = service().scan(project, false).get(0);

        assertEquals(ScanStatus.UNAVAILABLE, m.scan().status());
        assertEquals(CheckStatus.UNAVAILABLE, m.dependencies().get(0).checkStatus());
        assertTrue(m.vulnerabilities().isEmpty());
        assertEquals(4, SecurityReports.exitCode(service().report(project)));
        assertFalse(service().report(project).overallStatus() == ScanStatus.COMPLETE);
    }

    @Test
    void outageFallsBackToCachedAdvisoriesAndKeepsTheFindings() {
        advisories.answers.put("left-pad", List.of(advisory("GHSA-9", Severity.CRITICAL)));
        service().scan(project, false);

        advisories.failing = true;
        ManifestScan m = service(T0.plus(Duration.ofDays(3)), 24).scan(project, false).get(0);
        assertEquals(ScanStatus.COMPLETE_WITH_FINDINGS, m.scan().status());
        assertTrue(m.vulnerabilities().get(0).fromCache());
        assertEquals(2, advisories.calls.size()); // it did try again, because the cache was stale
    }

    @Test
    void freshCacheAvoidsRepeatedQueries() {
        service().scan(project, false);
        service().scan(project, false);
        assertEquals(1, advisories.calls.size());
    }

    @Test
    void expiredCacheIsRefreshed() {
        service(T0, 1).scan(project, false);
        service(T0.plus(Duration.ofHours(2)), 1).scan(project, false);
        assertEquals(2, advisories.calls.size());
    }

    @Test
    void offlineModeNeverCallsProvidersAndUsesCachedData() {
        advisories.answers.put("left-pad", List.of(advisory("GHSA-5", Severity.MEDIUM)));
        service().scan(project, false);
        advisories.calls.clear();

        ManifestScan m = service().scan(project, true).get(0);
        assertTrue(advisories.calls.isEmpty());
        assertEquals(ScanStatus.COMPLETE_WITH_FINDINGS, m.scan().status());
        assertTrue(m.scan().offline());
    }

    @Test
    void offlineWithoutCacheIsUnavailable() {
        ManifestScan m = service().scan(project, true).get(0);
        assertTrue(advisories.calls.isEmpty());
        assertEquals(ScanStatus.UNAVAILABLE, m.scan().status());
    }

    @Test
    void damagedCacheEntriesAreTreatedAsMissingNotAsNoAdvisories() throws SQLException {
        try (Connection c = database.connectionProvider().open(); Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO advisory_cache VALUES ('NPM', 'left-pad', '1.3.0', '{ damaged', '2026-11-13T00:00:00Z')");
        }
        ManifestScan m = service().scan(project, true).get(0);
        assertEquals(ScanStatus.UNAVAILABLE, m.scan().status());
    }

    @Test
    void circuitBreakerStopsHammeringADeadService() throws IOException {
        StringBuilder deps = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            deps.append(i > 1 ? ", " : "").append("\"pkg").append(i).append("\": \"1.0.").append(i).append("\"");
        }
        write("package.json", "{ \"dependencies\": { " + deps + " } }");
        advisories.failing = true;
        metadata.failing = true;

        ManifestScan m = service().scan(project, false).get(0);
        assertEquals(3, advisories.calls.size());
        assertEquals(6, m.dependencies().size());
        assertTrue(m.dependencies().stream().allMatch(d -> d.checkStatus() == CheckStatus.UNAVAILABLE));
        assertEquals(ScanStatus.UNAVAILABLE, m.scan().status());
    }

    @Test
    void metadataFailuresDoNotAffectTheSecurityStatus() {
        metadata.failing = true;
        ManifestScan m = service().scan(project, false).get(0);
        assertEquals(ScanStatus.COMPLETE, m.scan().status());
        assertFalse(m.dependencies().get(0).metadataOk());
        assertNull(m.dependencies().get(0).latestVersion());
    }

    @Test
    void outdatedAndLicenseWarningsAreCounted() throws IOException {
        write("package.json", "{ \"dependencies\": { \"a\": \"1.0.0\", \"b\": \"2.0.0\" } }");
        metadata.answers.put("a", new PackageMetadata("3.0.0", "GPL-3.0"));
        metadata.answers.put("b", new PackageMetadata("2.0.0", "MIT"));
        service().scan(project, false);

        SecurityReport r = service().report(project);
        assertEquals(1, r.outdated());
        assertEquals(1, r.licenseWarnings());
        assertEquals(1, SecurityReports.outdated(r.manifests()).size());
        assertEquals("a", SecurityReports.outdated(r.manifests()).get(0).name());
    }

    @Test
    void mavenAndPythonManifestsAreScannedTogether() throws IOException {
        write("pom.xml", "<project><dependencies><dependency><groupId>org.junit.jupiter</groupId>"
                + "<artifactId>junit-jupiter</artifactId><version>5.10.2</version></dependency></dependencies></project>");
        write("api/requirements.txt", "Django==4.2.1\n");
        List<ManifestScan> scans = service().scan(project, false);

        assertEquals(3, scans.size());
        assertTrue(advisories.calls.contains("org.junit.jupiter:junit-jupiter@5.10.2"));
        assertTrue(advisories.calls.contains("django@4.2.1"));
        assertEquals(3, service().report(project).manifestCount());
    }

    @Test
    void aBrokenManifestIsReportedWithoutStoppingTheOthers() throws IOException {
        write("pom.xml", "<project><broken></project>");
        List<ManifestScan> scans = service().scan(project, false);

        assertEquals(2, scans.size());
        ManifestScan pom = scans.stream().filter(s -> s.scan().manifestPath().equals("pom.xml")).findFirst().orElseThrow();
        assertEquals(ScanStatus.INCOMPLETE, pom.scan().status());
        assertTrue(pom.scan().message().contains("pom.xml"));
        ManifestScan npm = scans.stream().filter(s -> s.scan().manifestPath().equals("package.json")).findFirst().orElseThrow();
        assertEquals(ScanStatus.COMPLETE, npm.scan().status());
    }

    @Test
    void noManifestsMeansNothingToReport() throws IOException {
        Files.delete(root.resolve("package.json"));
        assertTrue(service().scan(project, false).isEmpty());
        assertFalse(service().report(project).scanned());
    }

    @Test
    void scanHistoryIsKeptButOnlyTheLatestRunIsReported() throws SQLException {
        service().scan(project, false);
        service().scan(project, false);
        assertEquals(2, count("SELECT COUNT(*) FROM dependency_scans"));
        assertEquals(1, service().latestScans(project).size());
    }

    @Test
    void aManifestThatDisappearedIsNoLongerInTheLatestRun() throws IOException {
        write("api/requirements.txt", "Django==4.2.1\n");
        service(T0, 24).scan(project, false);
        Files.delete(root.resolve("api/requirements.txt"));
        service(T0.plusSeconds(60), 24).scan(project, false);
        assertEquals(List.of("package.json"),
                service().latestScans(project).stream().map(s -> s.scan().manifestPath()).toList());
    }

    @Test
    void scanningDoesNotModifyTheProjectDirectory() throws IOException {
        String before = Files.readString(root.resolve("package.json"));
        service().scan(project, false);
        assertEquals(before, Files.readString(root.resolve("package.json")));
        try (var files = Files.list(root)) {
            assertEquals(List.of("package.json"), files.map(p -> p.getFileName().toString()).toList());
        }
    }
}
