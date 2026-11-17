package com.devforge.search.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.search.model.IndexSummary;
import com.devforge.search.service.IndexService;
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
        name = "index",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Index (or update the index of) the registered project containing a directory.")
public class SearchIndexCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "A directory inside a registered project.")
    Path directory;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project to index (instead of a directory).")
    String project;

    private final AppContext context;

    public SearchIndexCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        IndexService service = SearchServices.create(context);
        Project p = service.resolveProject(project, directory.toAbsolutePath());
        IndexSummary s = service.index(p);
        print(spec.commandLine().getOut(), output.json, p, s, "Indexed");
    }

    static void print(PrintWriter out, boolean json, Project p, IndexSummary s, String verb) {
        if (json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("projectId", p.id());
            doc.put("project", p.name());
            doc.put("summary", SearchJson.summary(s));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println(verb + " project '" + TerminalText.inline(p.name()) + "': " + s.added() + " added, "
                    + s.updated() + " updated, " + s.removed() + " removed, " + s.unchanged() + " unchanged ("
                    + s.contentIndexed() + " files searchable)"
                    + (s.unreadable() > 0 ? "; " + s.unreadable() + " unreadable" : ""));
        }
        out.flush();
    }
}
