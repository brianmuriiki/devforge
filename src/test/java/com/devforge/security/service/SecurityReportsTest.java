package com.devforge.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectStatus;
import com.devforge.security.model.CheckStatus;
import com.devforge.security.model.DependencyRecord;
import com.devforge.security.model.Ecosystem;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.ScanRecord;
import com.devforge.security.model.ScanStatus;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.model.Severity;
import com.devforge.security.model.VulnerabilityRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityReportsTest {

    private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");
    private static final Project PROJECT = new Project(1, "app", "/app", "", ProjectStatus.ACTIVE, T, T);

    private static DependencyRecord dep(String name, String resolved, String latest, String license, CheckStatus check) {
        return new DependencyRecord(1, 1, name, "", resolved, true, "compile", check, latest, license, latest != null);
    }

    private static VulnerabilityRecord vuln(String pkg, Severity s) {
        return new VulnerabilityRecord(1, pkg, "1.0", "", s, "ADV-" + pkg + s, List.of(), "d", List.of(), "osv.dev", false);
    }

    private static ManifestScan scan(long id, ScanStatus status, List<DependencyRecord> deps, List<VulnerabilityRecord> vulns) {
        return new ManifestScan(new ScanRecord(id, 1, Ecosystem.NPM, "package.json", T, status, "", false), deps, vulns);
    }

    @Test
    void noScansMeansNotScannedAndExitCodeZero() {
        SecurityReport r = SecurityReports.build(PROJECT, List.of());
        assertFalse(r.scanned());
        assertEquals(0, SecurityReports.exitCode(r));
    }

    @Test
    void cleanCompleteScanIsExitZero() {
        SecurityReport r = SecurityReports.build(PROJECT, List.of(
                scan(1, ScanStatus.COMPLETE, List.of(dep("a", "1.0", "1.0", "MIT", CheckStatus.CHECKED)), List.of())));
        assertEquals(ScanStatus.COMPLETE, r.overallStatus());
        assertEquals(0, SecurityReports.exitCode(r));
    }

    @Test
    void findingsGiveExitThreeAndAreCountedBySeverity() {
        SecurityReport r = SecurityReports.build(PROJECT, List.of(scan(1, ScanStatus.COMPLETE_WITH_FINDINGS,
                List.of(dep("a", "1.0", "1.0", "MIT", CheckStatus.CHECKED), dep("b", "1.0", "1.0", "MIT", CheckStatus.CHECKED)),
                List.of(vuln("a", Severity.CRITICAL), vuln("a", Severity.LOW), vuln("b", Severity.HIGH)))));
        assertEquals(3, r.vulnerabilityCount());
        assertEquals(2, r.vulnerableDependencies());
        assertEquals(1, (int) r.bySeverity().get(Severity.CRITICAL));
        assertEquals(1, (int) r.bySeverity().get(Severity.HIGH));
        assertEquals(0, (int) r.bySeverity().get(Severity.MEDIUM));
        assertEquals(1, (int) r.bySeverity().get(Severity.LOW));
        assertEquals(3, SecurityReports.exitCode(r));
    }

    @Test
    void incompleteOrUnavailableScansAreNeverExitZero() {
        for (ScanStatus s : new ScanStatus[] {ScanStatus.INCOMPLETE, ScanStatus.UNAVAILABLE}) {
            SecurityReport r = SecurityReports.build(PROJECT, List.of(scan(1, s,
                    List.of(dep("a", null, null, null, CheckStatus.UNRESOLVED)), List.of())));
            assertEquals(4, SecurityReports.exitCode(r), s.name());
        }
    }

    @Test
    void findingsOutrankIncompleteInExitCode() {
        SecurityReport r = SecurityReports.build(PROJECT, List.of(
                scan(1, ScanStatus.INCOMPLETE, List.of(dep("a", "1.0", null, null, CheckStatus.CHECKED)), List.of(vuln("a", Severity.LOW)))));
        assertEquals(3, SecurityReports.exitCode(r));
    }

    @Test
    void overallStatusIsTheWorstManifestStatus() {
        SecurityReport r = SecurityReports.build(PROJECT, List.of(
                scan(1, ScanStatus.COMPLETE, List.of(), List.of()),
                scan(2, ScanStatus.UNAVAILABLE, List.of(), List.of()),
                scan(3, ScanStatus.INCOMPLETE, List.of(), List.of())));
        assertEquals(ScanStatus.UNAVAILABLE, r.overallStatus());
    }

    @Test
    void countsOutdatedLicenseWarningsAndUnresolved() {
        SecurityReport r = SecurityReports.build(PROJECT, List.of(scan(1, ScanStatus.INCOMPLETE, List.of(
                dep("old", "1.0.0", "2.0.0", "MIT", CheckStatus.CHECKED),
                dep("current", "2.0.0", "2.0.0", "GPL-3.0", CheckStatus.CHECKED),
                dep("nolicense", "1.0.0", "1.0.0", null, CheckStatus.CHECKED),
                dep("range", null, "3.0.0", "MIT", CheckStatus.UNRESOLVED),
                dep("offline", "1.0.0", null, "MIT", CheckStatus.UNAVAILABLE)), List.of())));
        assertEquals(1, r.outdated());
        assertEquals(2, r.licenseWarnings());
        assertEquals(1, r.unresolved());
        assertEquals(1, r.unavailable());
        assertEquals(5, r.totalDependencies());
        assertTrue(SecurityReports.isOutdated(dep("old", "1.0.0", "2.0.0", "MIT", CheckStatus.CHECKED)));
        assertFalse(SecurityReports.isOutdated(dep("x", null, "2.0.0", "MIT", CheckStatus.UNRESOLVED)));
    }
}
