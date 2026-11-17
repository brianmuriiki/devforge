package com.devforge.cli.commands;

import com.devforge.cli.ConfirmOptions;
import com.devforge.cli.Confirmation;
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
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "remove",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Remove a project from DevForge, with its tasks and bugs. "
                + "The project's directory on disk is never touched.")
public class ProjectRemoveCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Mixin ConfirmOptions confirm;

    @Parameters(index = "0", paramLabel = "<id|name>", description = "Project id or name.")
    String reference;

    private final AppContext context;

    public ProjectRemoveCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        ProjectRegistry registry = context.projectRegistry();
        Project project = registry.resolve(reference);
        String path = TerminalText.inline(project.rootPath());

        Confirmation.require(context, confirm,
                "Remove project '" + TerminalText.inline(project.name()) + "' (id " + project.id()
                        + ") from DevForge? All of its tasks and bugs will be deleted. Files in "
                        + path + " are NOT touched. This cannot be undone.");
        Project removed = registry.remove(project.id());

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("removed", JsonOutput.project(removed));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Removed project '" + TerminalText.inline(removed.name()) + "' (id "
                    + removed.id() + "). Files in " + path + " were not touched.");
        }
        out.flush();
    }
}
