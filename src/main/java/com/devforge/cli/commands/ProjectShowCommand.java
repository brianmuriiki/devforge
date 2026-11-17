package com.devforge.cli.commands;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
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
        description = "Show details of one project.")
public class ProjectShowCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id|name>", description = "Project id or name.")
    String reference;

    private final AppContext context;

    public ProjectShowCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        Project p = context.projectRegistry().resolve(reference);
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("project", JsonOutput.project(p));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Project " + p.id() + ": " + TerminalText.inline(p.name()));
            out.println("Path:        " + TerminalText.inline(p.rootPath()));
            out.println("Status:      " + p.status());
            out.println("Created:     " + p.createdAt());
            out.println("Updated:     " + p.updatedAt());
            if (!p.description().isEmpty()) {
                out.println("Description:");
                out.println(indent(TerminalText.block(p.description())));
            }
        }
        out.flush();
    }

    private static String indent(String text) {
        return "  " + text.replace("\n", "\n  ");
    }
}
