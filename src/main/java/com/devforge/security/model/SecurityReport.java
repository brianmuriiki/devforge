package com.devforge.security.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Summary of a project's latest scans.
 *
 * @param overallStatus null when the project has never been scanned; otherwise the worst manifest status
 *                      (UNAVAILABLE &gt; INCOMPLETE &gt; COMPLETE_WITH_FINDINGS &gt; COMPLETE)
 */
public record SecurityReport(
        long projectId,
        String project,
        ScanStatus overallStatus,
        Instant scannedAt,
        int manifestCount,
        int totalDependencies,
        int vulnerableDependencies,
        int vulnerabilityCount,
        Map<Severity, Integer> bySeverity,
        int outdated,
        int licenseWarnings,
        int unresolved,
        int unavailable,
        List<ManifestScan> manifests) {

    public boolean scanned() {
        return overallStatus != null;
    }
}
