package com.devforge.security.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.security.SecurityScanException;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.service.SecurityReports;
import com.devforge.security.service.SecurityService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "audit",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show the findings of the latest stored scan (no network). Exit code 3 if any finding exists.")
public class SecurityAuditCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project (default: current directory's project, else all).")
    String project;

    private final AppContext context;

    public SecurityAuditCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        SecurityService service = SecurityServices.create(context);
        List<SecurityReport> reports = new ArrayList<>();
        for (Project p : SecurityServices.projects(context, project, Path.of("").toAbsolutePath())) {
            reports.add(service.report(p));
        }
        if (reports.stream().noneMatch(SecurityReport::scanned)) {
            throw new SecurityScanException("No scan results yet. Run 'devforge security scan' first.");
        }
        PrintWriter out = spec.commandLine().getOut();
        int code = 0;
        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (SecurityReport r : reports) {
                items.add(SecurityJson.report(r, true));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("projects", items);
            out.println(JsonOutput.toJson(doc));
        }
        for (SecurityReport r : reports) {
            code = combine(code, SecurityReports.exitCode(r));
            if (!output.json) {
                for (ManifestScan m : r.manifests()) {
                    SecurityText.printManifest(out, m, true);
                }
                SecurityText.printSummary(out, r);
            }
        }
        out.flush();
        return code;
    }

    /** Findings (3) outrank incomplete (4), which outranks success (0). */
    static int combine(int a, int b) {
        if (a == 3 || b == 3) {
            return 3;
        }
        return Math.max(a, b);
    }
}
