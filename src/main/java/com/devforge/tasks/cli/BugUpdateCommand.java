package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.tasks.model.Bug;
import com.devforge.tasks.model.BugSeverity;
import com.devforge.tasks.model.BugStatus;
import com.devforge.tasks.service.BugChanges;
import com.devforge.tasks.service.BugService;
import java.io.PrintWriter;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Change a bug. Only the options you give are changed.")
public class BugUpdateCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id>", description = "Bug id.")
    long id;

    @Option(names = "--title", description = "New title.")
    String title;

    @Option(names = {"-d", "--description"}, description = "New description.")
    String description;

    @Option(names = "--severity", description = "New severity: ${COMPLETION-CANDIDATES}.")
    BugSeverity severity;

    @Option(names = "--status", description = "New status: ${COMPLETION-CANDIDATES}.")
    BugStatus status;

    @Option(names = "--steps", description = "New reproduction steps.")
    String steps;

    private final AppContext context;

    public BugUpdateCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        BugService service = BugServices.create(context);
        Bug bug = service.update(id, new BugChanges(title, description, severity, status, steps));

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("bug", BugJson.bug(bug));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Updated bug " + bug.id() + ": " + TerminalText.inline(bug.title())
                    + " [" + bug.status() + ", " + bug.severity() + "]");
        }
        out.flush();
    }
}
