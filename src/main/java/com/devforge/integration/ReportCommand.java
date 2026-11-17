package com.devforge.integration;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
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
        name = "report",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = {
            "Combined report from stored data: tasks, bugs, search index, duplicates, security and backups.",
            "Exit code 3 = security findings, 4 = security result incomplete."})
public class ReportCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project (default: the one containing the current directory, else all).")
    String project;

    private final AppContext context;

    public ReportCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        ProjectRegistry registry = context.projectRegistry();
        List<Project> selected;
        if (project != null) {
            selected = List.of(registry.resolve(project));
        } else {
            var containing = registry.findContaining(Path.of("").toAbsolutePath());
            selected = containing.isPresent() ? List.of(containing.get()) : registry.list();
        }
        PrintWriter out = spec.commandLine().getOut();
        if (selected.isEmpty()) {
            out.println("No projects registered. Run 'devforge init .' first.");
            out.flush();
            return 0;
        }
        List<ProjectReport> reports = new ArrayList<>();
        for (Project p : selected) {
            reports.add(ProjectReports.build(context, p));
        }
        return render(out, output.json, reports);
    }

    static int render(PrintWriter out, boolean json, List<ProjectReport> reports) {
        int code = 0;
        if (json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ProjectReport r : reports) {
                items.add(ProjectReports.toMap(r));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("projects", items);
            out.println(JsonOutput.toJson(doc));
        }
        for (ProjectReport r : reports) {
            int c = ProjectReports.exitCode(r);
            code = (code == 3 || c == 3) ? 3 : Math.max(code, c);
            if (!json) {
                ProjectReports.print(out, r);
            }
        }
        out.flush();
        return code;
    }
}
