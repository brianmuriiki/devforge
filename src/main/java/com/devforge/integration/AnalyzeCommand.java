package com.devforge.integration;

import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.search.model.IndexSummary;
import com.devforge.search.service.IndexService;
import com.devforge.security.cli.SecurityServices;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "analyze",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = {
            "Register the project if needed, refresh its search index, scan its dependencies, then print the combined report.",
            "Exit code 3 = security findings, 4 = security result incomplete."})
public class AnalyzeCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "Project directory.")
    Path directory;

    @Option(names = "--offline", description = "Do not use the network for the dependency scan (cached data only).")
    boolean offline;

    private final AppContext context;

    public AnalyzeCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        ProjectRegistry registry = context.projectRegistry();
        PrintWriter out = spec.commandLine().getOut();

        Optional<Project> existing = registry.findContaining(directory.toAbsolutePath());
        Project project;
        if (existing.isPresent()) {
            project = existing.get();
        } else {
            project = registry.register(directory, null, null);
            if (!output.json) {
                out.println("Registered project '" + TerminalText.inline(project.name()) + "' at "
                        + TerminalText.inline(project.rootPath()));
            }
        }

        IndexService index = new IndexService(context.connectionProvider(), registry, context.config(), context.clock());
        IndexSummary summary = index.index(project);
        if (!output.json) {
            out.println("Indexed: " + summary.added() + " added, " + summary.updated() + " updated, " + summary.removed()
                    + " removed (" + summary.contentIndexed() + " searchable)");
        }
        SecurityServices.create(context).scan(project, offline);
        out.flush();

        return ReportCommand.render(out, output.json, List.of(ProjectReports.build(context, project)));
    }
}
