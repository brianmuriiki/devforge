package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.tasks.model.Bug;
import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.service.BugService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "add",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Record a bug.")
public class BugAddCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<title>", description = "Bug title.")
    String title;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>",
            description = "Project (default: the registered project containing the current directory).")
    String project;

    @Option(names = {"-d", "--description"}, defaultValue = "", description = "Bug description.")
    String description;

    @Option(names = "--severity", defaultValue = "MEDIUM",
            description = "Severity: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    BugSeverity severity;

    @Option(names = "--steps", defaultValue = "", description = "Reproduction steps.")
    String steps;

    private final AppContext context;

    public BugAddCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        BugService service = BugServices.create(context);
        Project target = service.resolveProject(project, Path.of("").toAbsolutePath());
        Bug bug = service.add(target, title, description, severity, steps);

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("bug", BugJson.bug(bug));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Recorded bug " + bug.id() + " in project '" + TerminalText.inline(target.name())
                    + "': " + TerminalText.inline(bug.title()));
        }
        out.flush();
    }
}
