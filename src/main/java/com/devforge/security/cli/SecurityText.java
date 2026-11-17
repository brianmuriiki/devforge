package com.devforge.security.cli;

import com.devforge.cli.TerminalText;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.ScanStatus;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.model.VulnerabilityRecord;
import java.io.PrintWriter;

/** Human-readable DepGuard output. Everything that came from a manifest or the network is sanitized. */
final class SecurityText {

    private SecurityText() {}

    static void printManifest(PrintWriter out, ManifestScan m, boolean withFindings) {
        out.println(TerminalText.inline(m.scan().manifestPath()) + " (" + m.scan().ecosystem() + "): "
                + m.scan().status() + " - " + m.dependencies().size() + " dependencies, "
                + m.vulnerabilities().size() + " vulnerabilities");
        if (!m.scan().message().isEmpty()) {
            out.println("  note: " + TerminalText.inline(m.scan().message()));
        }
        if (withFindings) {
            for (VulnerabilityRecord v : m.vulnerabilities()) {
                out.println("  [" + v.severity() + "] " + TerminalText.inline(v.advisoryId()) + "  "
                        + TerminalText.inline(v.packageName()) + " " + TerminalText.inline(v.installedVersion())
                        + (v.fixedVersions().isEmpty() ? "" : "  (fixed in: "
                                + TerminalText.inline(String.join(", ", v.fixedVersions())) + ")"));
                if (!v.description().isEmpty()) {
                    out.println("      " + TerminalText.inline(v.description()));
                }
            }
        }
    }

    static void printSummary(PrintWriter out, SecurityReport r) {
        if (!r.scanned()) {
            out.println("Project '" + TerminalText.inline(r.project()) + "': no scan results. Run 'devforge security scan'.");
            return;
        }
        out.println("Summary for '" + TerminalText.inline(r.project()) + "' (" + r.scannedAt() + "): " + r.overallStatus());
        out.println("  Dependencies:  " + r.totalDependencies() + " in " + r.manifestCount() + " manifest(s)");
        out.println("  Vulnerable:    " + r.vulnerableDependencies() + " dependencies, " + r.vulnerabilityCount()
                + " findings (critical " + r.bySeverity().getOrDefault(com.devforge.security.model.Severity.CRITICAL, 0)
                + ", high " + r.bySeverity().getOrDefault(com.devforge.security.model.Severity.HIGH, 0)
                + ", medium " + r.bySeverity().getOrDefault(com.devforge.security.model.Severity.MEDIUM, 0)
                + ", low " + r.bySeverity().getOrDefault(com.devforge.security.model.Severity.LOW, 0)
                + ", unknown " + r.bySeverity().getOrDefault(com.devforge.security.model.Severity.UNKNOWN, 0) + ")");
        out.println("  Outdated:      " + r.outdated());
        out.println("  License review:" + " " + r.licenseWarnings());
        if (r.overallStatus() == ScanStatus.INCOMPLETE || r.overallStatus() == ScanStatus.UNAVAILABLE) {
            out.println("  WARNING: this scan is " + r.overallStatus() + " (" + r.unresolved() + " without exact version, "
                    + r.unavailable() + " without advisory data). It is NOT a clean result.");
        } else if (r.vulnerabilityCount() == 0 && r.manifestCount() > 0) {
            out.println("  No known vulnerabilities were found for the checked versions.");
        }
    }
}
