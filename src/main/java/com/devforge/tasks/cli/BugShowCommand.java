package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.tasks.model.Bug;
import com.devforge.tasks.service.BugService;
import java.io.PrintWriter;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "show",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show the details of one bug.")
public class BugShowCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id>", description = "Bug id.")
    long id;

    private final AppContext context;

    public BugShowCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        BugService service = BugServices.create(context);
        Bug b = service.get(id);
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("bug", BugJson.bug(b));
            out.println(JsonOutput.toJson(doc));
        } else {
            String projectName = context.projectRegistry().findById(b.projectId())
                    .map(p -> TerminalText.inline(p.name()))
                    .orElse("?");
            out.println("Bug " + b.id() + ": " + TerminalText.inline(b.title()));
            out.println("Project:     " + projectName + " (id " + b.projectId() + ")");
            out.println("Severity:    " + b.severity());
            out.println("Status:      " + b.status());
            out.println("Created:     " + b.createdAt());
            out.println("Updated:     " + b.updatedAt());
            if (!b.description().isEmpty()) {
                out.println("Description:");
                out.println("  " + TerminalText.block(b.description()).replace("\n", "\n  "));
            }
            if (!b.reproductionSteps().isEmpty()) {
                out.println("Reproduction steps:");
                out.println("  " + TerminalText.block(b.reproductionSteps()).replace("\n", "\n  "));
            }
        }
        out.flush();
    }
}
