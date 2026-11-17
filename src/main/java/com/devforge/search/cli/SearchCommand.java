package com.devforge.search.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.search.model.IndexStatus;
import com.devforge.search.model.SearchHit;
import com.devforge.search.service.IndexService;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code devforge search "text"} searches indexed files; the subcommands (index, rebuild, status,
 * duplicates, watch) manage the index. A query that equals a subcommand name must be written after
 * {@code --}, for example {@code devforge search -- index}.
 */
@Command(
        name = "search",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Search project files and manage the search index.")
public class SearchCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", paramLabel = "<query>", description = "Text to search for.")
    String query;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>",
            description = "Only this project (default: all projects).")
    String project;

    @Option(names = {"-e", "--extension"}, paramLabel = "<ext>", description = "Only files with this extension.")
    String extension;

    @Option(names = "--limit", defaultValue = "20", description = "Maximum results (default: ${DEFAULT-VALUE}).")
    int limit;

    private final AppContext context;

    public SearchCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        if (query == null) {
            spec.commandLine().usage(out);
            return 0;
        }
        IndexService service = SearchServices.create(context);
        Long projectId = project == null ? null : context.projectRegistry().resolve(project).id();
        List<SearchHit> hits = service.search(query, projectId, extension, limit);

        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (SearchHit h : hits) {
                items.add(SearchJson.hit(h));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("query", query);
            doc.put("count", hits.size());
            doc.put("results", items);
            out.println(JsonOutput.toJson(doc));
            out.flush();
            return hits.isEmpty() ? 1 : 0;
        }
        if (hits.isEmpty()) {
            PrintWriter err = spec.commandLine().getErr();
            boolean anythingIndexed = false;
            for (var p : context.projectRegistry().list()) {
                IndexStatus s = service.status(p);
                anythingIndexed |= s.files() > 0;
            }
            err.println(anythingIndexed
                    ? "Error: No matching files found."
                    : "Error: Nothing is indexed yet. Run 'devforge search index .' first.");
            err.flush();
            return 1;
        }
        for (SearchHit h : hits) {
            out.println(TerminalText.inline(h.project()) + ":" + TerminalText.inline(h.path())
                    + (h.snippet().isEmpty() ? "" : "  " + TerminalText.inline(h.snippet())));
        }
        out.println(hits.size() + " result(s)");
        out.flush();
        return 0;
    }
}
