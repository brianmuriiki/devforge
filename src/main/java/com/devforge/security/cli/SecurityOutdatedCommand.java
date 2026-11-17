package com.devforge.security.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.TextTable;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.security.SecurityScanException;
import com.devforge.security.model.DependencyRecord;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.service.SecurityReports;
import com.devforge.security.service.SecurityService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "outdated",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "List dependencies with a newer release (from the latest stored scan). Nothing is upgraded.")
public class SecurityOutdatedCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project (default: current directory's project, else all).")
    String project;

    private final AppContext context;

    public SecurityOutdatedCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        SecurityService service = SecurityServices.create(context);
        PrintWriter out = spec.commandLine().getOut();
        List<Map<String, Object>> jsonItems = new ArrayList<>();
        boolean anyScan = false;
        for (Project p : SecurityServices.projects(context, project, Path.of("").toAbsolutePath())) {
            List<ManifestScan> scans = service.latestScans(p);
            anyScan |= !scans.isEmpty();
            List<List<String>> rows = new ArrayList<>();
            for (ManifestScan m : scans) {
                for (DependencyRecord d : m.dependencies()) {
                    if (SecurityReports.isOutdated(d)) {
                        rows.add(List.of(d.name(), d.resolvedVersion(), d.latestVersion(), m.scan().manifestPath(),
                                "review release notes before upgrading; compatibility is not verified"));
                        Map<String, Object> item = SecurityJson.dependency(d);
                        item.put("project", p.name());
                        item.put("manifest", m.scan().manifestPath());
                        item.put("recommendation", "Review release notes before upgrading; compatibility is not verified.");
                        jsonItems.add(item);
                    }
                }
            }
            if (!output.json && !scans.isEmpty()) {
                out.println("Project '" + TerminalText.inline(p.name()) + "':");
                out.print(rows.isEmpty()
                        ? "  No outdated dependencies among those with a known latest version.\n"
                        : TextTable.render(List.of("PACKAGE", "CURRENT", "LATEST", "MANIFEST", "RECOMMENDATION"), rows));
            }
        }
        if (!anyScan) {
            throw new SecurityScanException("No scan results yet. Run 'devforge security scan' first.");
        }
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("count", jsonItems.size());
            doc.put("outdated", jsonItems);
            out.println(JsonOutput.toJson(doc));
        }
        out.flush();
    }
}
