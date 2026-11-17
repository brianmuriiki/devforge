package com.devforge.cli.commands;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
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
        description = "Change a project's name or description.")
public class ProjectUpdateCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id|name>", description = "Project id or name.")
    String reference;

    @Option(names = "--name", description = "New project name.")
    String name;

    @Option(names = {"-d", "--description"}, description = "New description.")
    String description;

    private final AppContext context;

    public ProjectUpdateCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        ProjectRegistry registry = context.projectRegistry();
        Project current = registry.resolve(reference);
        Project updated = registry.update(current.id(), name, description);

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("project", JsonOutput.project(updated));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Updated project " + updated.id() + ": " + TerminalText.inline(updated.name()));
        }
        out.flush();
    }
}
