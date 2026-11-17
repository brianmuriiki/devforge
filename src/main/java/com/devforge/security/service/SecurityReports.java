package com.devforge.security.service;

import com.devforge.core.project.Project;
import com.devforge.security.model.DependencyRecord;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.ScanStatus;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.model.Severity;
import com.devforge.security.model.VulnerabilityRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregation of stored scans into reports, plus the exit-code rule. */
public final class SecurityReports {

    private SecurityReports() {}

    public static SecurityReport build(Project project, List<ManifestScan> scans) {
        Map<Severity, Integer> bySeverity = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            bySeverity.put(s, 0);
        }
        if (scans.isEmpty()) {
            return new SecurityReport(project.id(), project.name(), null, null, 0, 0, 0, 0, bySeverity, 0, 0, 0, 0, scans);
        }
        int total = 0;
        int vulnerabilities = 0;
        int outdated = 0;
        int licenseWarnings = 0;
        int unresolved = 0;
        int unavailable = 0;
        Set<String> vulnerable = new HashSet<>();
        Instant latest = scans.get(0).scan().scannedAt();
        ScanStatus overall = ScanStatus.COMPLETE;
        for (ManifestScan m : scans) {
            total += m.dependencies().size();
            overall = worse(overall, m.scan().status());
            if (m.scan().scannedAt().isAfter(latest)) {
                latest = m.scan().scannedAt();
            }
            for (VulnerabilityRecord v : m.vulnerabilities()) {
                vulnerabilities++;
                bySeverity.merge(v.severity(), 1, Integer::sum);
                vulnerable.add(m.scan().id() + "|" + v.packageName());
            }
            for (DependencyRecord d : m.dependencies()) {
                if (isOutdated(d)) {
                    outdated++;
                }
                if (LicensePolicy.review(d.license()).needsReview()) {
                    licenseWarnings++;
                }
                switch (d.checkStatus()) {
                    case UNRESOLVED -> unresolved++;
                    case UNAVAILABLE -> unavailable++;
                    default -> { }
                }
            }
        }
        return new SecurityReport(project.id(), project.name(), overall, latest, scans.size(), total,
                vulnerable.size(), vulnerabilities, bySeverity, outdated, licenseWarnings, unresolved, unavailable, scans);
    }

    public static boolean isOutdated(DependencyRecord d) {
        return d.resolvedVersion() != null && d.latestVersion() != null
                && Versions.isNewer(d.resolvedVersion(), d.latestVersion());
    }

    public static List<DependencyRecord> outdated(List<ManifestScan> scans) {
        List<DependencyRecord> out = new ArrayList<>();
        for (ManifestScan m : scans) {
            for (DependencyRecord d : m.dependencies()) {
                if (isOutdated(d)) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    /** Process exit code for a report: 3 if any finding exists, else 4 if the result is not trustworthy, else 0. */
    public static int exitCode(SecurityReport report) {
        if (report.vulnerabilityCount() > 0) {
            return 3;
        }
        if (report.overallStatus() == ScanStatus.INCOMPLETE || report.overallStatus() == ScanStatus.UNAVAILABLE) {
            return 4;
        }
        return 0;
    }

    private static ScanStatus worse(ScanStatus a, ScanStatus b) {
        return rank(b) > rank(a) ? b : a;
    }

    private static int rank(ScanStatus s) {
        return switch (s) {
            case COMPLETE -> 0;
            case COMPLETE_WITH_FINDINGS -> 1;
            case INCOMPLETE -> 2;
            case UNAVAILABLE -> 3;
        };
    }
}
