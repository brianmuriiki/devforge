package com.devforge.search.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.search.model.DuplicateGroup;
import com.devforge.search.model.DuplicateReport;
import com.devforge.search.service.IndexService;
import java.io.PrintWriter;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "duplicates",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "List files with identical content (SHA-256). Nothing is ever deleted.")
public class SearchDuplicatesCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Only this project (default: all).")
    String project;

    private final AppContext context;

    public SearchDuplicatesCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        IndexService service = SearchServices.create(context);
        Long projectId = project == null ? null : context.projectRegistry().resolve(project).id();
        DuplicateReport report = service.duplicates(projectId);
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.putAll(SearchJson.duplicates(report));
            out.println(JsonOutput.toJson(doc));
        } else if (report.groups().isEmpty()) {
            out.println("No duplicate files found.");
        } else {
            int n = 1;
            for (DuplicateGroup g : report.groups()) {
                out.println("Group " + n++ + " (" + g.size() + " bytes each, sha256 " + g.sha256().substring(0, 12) + "...)");
                for (String location : g.locations()) {
                    out.println("  " + TerminalText.inline(location));
                }
            }
            out.println(report.groups().size() + " group(s); " + report.wastedBytes() + " bytes could be reclaimed. "
                    + "No files were changed.");
        }
        if (report.unreadable() > 0 && !output.json) {
            out.println(report.unreadable() + " file(s) could not be read and were skipped.");
        }
        out.flush();
    }
}
