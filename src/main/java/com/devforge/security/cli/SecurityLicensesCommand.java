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
import com.devforge.security.service.LicensePolicy;
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
        name = "licenses",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show dependency licenses from the latest stored scan and flag those needing review.")
public class SecurityLicensesCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project (default: current directory's project, else all).")
    String project;

    @Option(names = "--review-only", description = "Only show licenses that need review.")
    boolean reviewOnly;

    private final AppContext context;

    public SecurityLicensesCommand(AppContext context) {
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
                    LicensePolicy.Review review = LicensePolicy.review(d.license());
                    if (reviewOnly && !review.needsReview()) {
                        continue;
                    }
                    rows.add(List.of(d.name(), d.license() == null ? "unknown" : d.license(),
                            review.needsReview() ? "REVIEW: " + review.reason() : "ok"));
                    Map<String, Object> item = SecurityJson.dependency(d);
                    item.put("project", p.name());
                    item.put("reviewReason", review.reason());
                    jsonItems.add(item);
                }
            }
            if (!output.json && !scans.isEmpty()) {
                out.println("Project '" + TerminalText.inline(p.name()) + "':");
                out.print(TextTable.render(List.of("PACKAGE", "LICENSE", "STATUS"), rows));
            }
        }
        if (!anyScan) {
            throw new SecurityScanException("No scan results yet. Run 'devforge security scan' first.");
        }
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("disclaimer", LicensePolicy.DISCLAIMER);
            doc.put("count", jsonItems.size());
            doc.put("licenses", jsonItems);
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println(LicensePolicy.DISCLAIMER);
        }
        out.flush();
    }
}
