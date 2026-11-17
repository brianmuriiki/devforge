package com.devforge.security.cli;

import com.devforge.security.model.DependencyRecord;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.model.Severity;
import com.devforge.security.model.VulnerabilityRecord;
import com.devforge.security.service.LicensePolicy;
import com.devforge.security.service.SecurityReports;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON shapes for DepGuard output (part of the CLI contract; also used by the combined report). */
public final class SecurityJson {

    private SecurityJson() {}

    public static Map<String, Object> vulnerability(VulnerabilityRecord v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("advisoryId", v.advisoryId());
        m.put("package", v.packageName());
        m.put("installedVersion", v.installedVersion());
        m.put("severity", v.severity().name());
        m.put("affectedRange", v.affectedRange());
        m.put("fixedVersions", v.fixedVersions());
        m.put("description", v.description());
        m.put("references", v.references());
        m.put("source", v.source());
        m.put("fromCache", v.fromCache());
        return m;
    }

    public static Map<String, Object> dependency(DependencyRecord d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", d.name());
        m.put("declaredVersion", d.declaredVersion());
        m.put("resolvedVersion", d.resolvedVersion());
        m.put("direct", d.direct());
        m.put("scope", d.scope());
        m.put("checkStatus", d.checkStatus().name());
        m.put("latestVersion", d.latestVersion());
        m.put("outdated", SecurityReports.isOutdated(d));
        m.put("license", d.license());
        m.put("licenseNeedsReview", LicensePolicy.review(d.license()).needsReview());
        return m;
    }

    public static Map<String, Object> manifest(ManifestScan s, boolean detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("manifest", s.scan().manifestPath());
        m.put("ecosystem", s.scan().ecosystem().name());
        m.put("status", s.scan().status().name());
        m.put("message", s.scan().message());
        m.put("offline", s.scan().offline());
        m.put("dependencyCount", s.dependencies().size());
        m.put("vulnerabilityCount", s.vulnerabilities().size());
        if (detail) {
            List<Map<String, Object>> deps = new ArrayList<>();
            for (DependencyRecord d : s.dependencies()) {
                deps.add(dependency(d));
            }
            List<Map<String, Object>> vulns = new ArrayList<>();
            for (VulnerabilityRecord v : s.vulnerabilities()) {
                vulns.add(vulnerability(v));
            }
            m.put("dependencies", deps);
            m.put("vulnerabilities", vulns);
        }
        return m;
    }

    public static Map<String, Object> report(SecurityReport r, boolean detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", r.projectId());
        m.put("project", r.project());
        m.put("scanned", r.scanned());
        m.put("status", r.overallStatus() == null ? "NOT_SCANNED" : r.overallStatus().name());
        m.put("scannedAt", r.scannedAt() == null ? null : r.scannedAt().toString());
        m.put("manifests", r.manifestCount());
        m.put("totalDependencies", r.totalDependencies());
        m.put("vulnerableDependencies", r.vulnerableDependencies());
        m.put("vulnerabilities", r.vulnerabilityCount());
        Map<String, Object> sev = new LinkedHashMap<>();
        for (Severity s : new Severity[] {Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.UNKNOWN}) {
            sev.put(s.name(), r.bySeverity().getOrDefault(s, 0));
        }
        m.put("bySeverity", sev);
        m.put("outdated", r.outdated());
        m.put("licenseWarnings", r.licenseWarnings());
        m.put("unresolved", r.unresolved());
        m.put("unavailable", r.unavailable());
        List<Map<String, Object>> manifests = new ArrayList<>();
        for (ManifestScan s : r.manifests()) {
            manifests.add(manifest(s, detail));
        }
        m.put("manifestScans", manifests);
        return m;
    }
}
