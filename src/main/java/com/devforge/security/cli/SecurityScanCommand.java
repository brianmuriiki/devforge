package com.devforge.security.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.security.model.ManifestScan;
import com.devforge.security.model.SecurityReport;
import com.devforge.security.service.SecurityReports;
import com.devforge.security.service.SecurityService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "scan",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = {
            "Scan a project's dependency manifests for known vulnerabilities, newer releases and licenses.",
            "Exit code 3 = vulnerabilities found, 4 = scan incomplete or advisory data unavailable."})
public class SecurityScanCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "A directory inside a registered project.")
    Path directory;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project to scan.")
    String project;

    @Option(names = "--offline", description = "Use cached advisory and registry data only; make no network requests.")
    boolean offline;

    private final AppContext context;

    public SecurityScanCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        Project p = SecurityServices.single(context, project, directory.toAbsolutePath());
        SecurityService service = SecurityServices.create(context);
        List<ManifestScan> scans = service.scan(p, offline);
        SecurityReport report = SecurityReports.build(p, scans);
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.putAll(SecurityJson.report(report, true));
            out.println(JsonOutput.toJson(doc));
        } else if (scans.isEmpty()) {
            out.println("No supported dependency manifests (pom.xml, package.json, requirements.txt, pyproject.toml) found.");
        } else {
            for (ManifestScan m : scans) {
                SecurityText.printManifest(out, m, true);
            }
            SecurityText.printSummary(out, report);
        }
        out.flush();
        return SecurityReports.exitCode(report);
    }
}
