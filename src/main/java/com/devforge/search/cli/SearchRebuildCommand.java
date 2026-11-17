package com.devforge.search.cli;

import com.devforge.cli.OutputOptions;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.search.model.IndexSummary;
import com.devforge.search.service.IndexService;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "rebuild",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Discard a project's index and rebuild it from scratch (full reconciliation).")
public class SearchRebuildCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "A directory inside a registered project.")
    Path directory;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project to rebuild.")
    String project;

    private final AppContext context;

    public SearchRebuildCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        IndexService service = SearchServices.create(context);
        Project p = service.resolveProject(project, directory.toAbsolutePath());
        IndexSummary s = service.rebuild(p);
        SearchIndexCommand.print(spec.commandLine().getOut(), output.json, p, s, "Rebuilt index of");
    }
}
