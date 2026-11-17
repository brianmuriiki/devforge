package com.devforge.cli.commands;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TextTable;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "list",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "List registered projects.")
public class ProjectListCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    private final AppContext context;

    public ProjectListCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        List<Project> projects = context.projectRegistry().list();
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Project p : projects) {
                items.add(JsonOutput.project(p));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("count", projects.size());
            doc.put("projects", items);
            out.println(JsonOutput.toJson(doc));
        } else if (projects.isEmpty()) {
            out.println("No projects registered. Run 'devforge init .' to register one.");
        } else {
            out.print(table(projects));
        }
        out.flush();
    }

    private static String table(List<Project> projects) {
        List<List<String>> rows = new ArrayList<>();
        for (Project p : projects) {
            rows.add(List.of(String.valueOf(p.id()), p.name(), p.status().name(), p.rootPath()));
        }
        return TextTable.render(List.of("ID", "NAME", "STATUS", "PATH"), rows);
    }
}
