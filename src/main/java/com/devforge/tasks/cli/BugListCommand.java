package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TextTable;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.tasks.model.Bug;
import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.model.BugStatus;
import com.devforge.tasks.service.BugService;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "list",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "List bugs, optionally filtered.")
public class BugListCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>",
            description = "Only bugs of this project (default: all projects).")
    String project;

    @Option(names = "--status", description = "Only this status: ${COMPLETION-CANDIDATES}.")
    BugStatus status;

    @Option(names = "--severity", description = "Only this severity: ${COMPLETION-CANDIDATES}.")
    BugSeverity severity;

    private final AppContext context;

    public BugListCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        BugService service = BugServices.create(context);
        ProjectRegistry registry = context.projectRegistry();
        Long projectId = project == null ? null : registry.resolve(project).id();

        List<Bug> bugs = service.list(projectId, status, severity);
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Bug b : bugs) {
                items.add(BugJson.bug(b));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("count", bugs.size());
            doc.put("bugs", items);
            out.println(JsonOutput.toJson(doc));
        } else if (bugs.isEmpty()) {
            out.println("No bugs found.");
        } else {
            Map<Long, String> names = new HashMap<>();
            for (Project p : registry.list()) {
                names.put(p.id(), p.name());
            }
            List<List<String>> rows = new ArrayList<>();
            for (Bug b : bugs) {
                rows.add(List.of(
                        String.valueOf(b.id()),
                        names.getOrDefault(b.projectId(), "?"),
                        b.severity().name(),
                        b.status().name(),
                        b.title()));
            }
            out.print(TextTable.render(List.of("ID", "PROJECT", "SEVERITY", "STATUS", "TITLE"), rows));
            out.println(bugs.size() + " bug(s)");
        }
        out.flush();
    }
}
