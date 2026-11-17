package com.devforge.cli.commands;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
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
        name = "init",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Register a directory as a DevForge project.")
public class InitCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".",
            description = "Project directory (default: current directory).")
    Path directory;

    @Option(names = "--name", description = "Project name (default: the directory name).")
    String name;

    @Option(names = "--description", defaultValue = "", description = "Short project description.")
    String description;

    private final AppContext context;

    public InitCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        Project project = context.projectRegistry().register(directory, name, description);
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("project", JsonOutput.project(project));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Registered project '" + project.name() + "' (id " + project.id()
                    + ") at " + project.rootPath());
        }
        out.flush();
    }
}
