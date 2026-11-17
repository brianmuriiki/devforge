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
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "archive",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Archive a project. Its data is kept; new tasks and bugs are blocked until unarchived.")
public class ProjectArchiveCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id|name>", description = "Project id or name.")
    String reference;

    private final AppContext context;

    public ProjectArchiveCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        ProjectRegistry registry = context.projectRegistry();
        Project archived = registry.archive(registry.resolve(reference).id());

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("project", JsonOutput.project(archived));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Archived project '" + TerminalText.inline(archived.name()) + "' (id "
                    + archived.id() + "). Undo with 'devforge project unarchive " + archived.id() + "'.");
        }
        out.flush();
    }
}
