package com.devforge.search.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TextTable;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.search.model.IndexStatus;
import com.devforge.search.service.IndexService;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "status",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show how much of each project is indexed.")
public class SearchStatusCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Only this project (default: all).")
    String project;

    private final AppContext context;

    public SearchStatusCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        IndexService service = SearchServices.create(context);
        List<Project> selected = project == null
                ? context.projectRegistry().list()
                : List.of(context.projectRegistry().resolve(project));
        List<IndexStatus> statuses = new ArrayList<>();
        for (Project p : selected) {
            statuses.add(service.status(p));
        }
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (IndexStatus s : statuses) {
                items.add(SearchJson.status(s));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("projects", items);
            out.println(JsonOutput.toJson(doc));
        } else if (statuses.isEmpty()) {
            out.println("No projects registered.");
        } else {
            List<List<String>> rows = new ArrayList<>();
            for (IndexStatus s : statuses) {
                rows.add(List.of(s.project(), String.valueOf(s.files()), String.valueOf(s.contentIndexed()),
                        s.totalBytes() + " B", s.lastIndexedAt() == null ? "never" : s.lastIndexedAt().toString()));
            }
            out.print(TextTable.render(List.of("PROJECT", "FILES", "SEARCHABLE", "SIZE", "LAST INDEXED"), rows));
        }
        out.flush();
    }
}
