package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.service.TaskService;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "show",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show the details of one task.")
public class TaskShowCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id>", description = "Task id.")
    long id;

    private final AppContext context;

    public TaskShowCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        TaskService service = TaskServices.create(context);
        Task t = service.get(id);
        LocalDate today = service.today();
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("task", TaskJson.task(t, today));
            out.println(JsonOutput.toJson(doc));
        } else {
            String projectName = context.projectRegistry().findById(t.projectId())
                    .map(p -> TerminalText.inline(p.name()))
                    .orElse("?");
            out.println("Task " + t.id() + ": " + TerminalText.inline(t.title()));
            out.println("Project:     " + projectName + " (id " + t.projectId() + ")");
            out.println("Status:      " + t.status());
            out.println("Priority:    " + t.priority());
            out.println("Due:         " + (t.dueDate() == null
                    ? "-"
                    : t.dueDate() + (t.isOverdue(today) ? " (overdue)" : "")));
            out.println("Created:     " + t.createdAt());
            out.println("Updated:     " + t.updatedAt());
            if (t.completedAt() != null) {
                out.println("Completed:   " + t.completedAt());
            }
            if (!t.description().isEmpty()) {
                out.println("Description:");
                out.println("  " + TerminalText.block(t.description()).replace("\n", "\n  "));
            }
        }
        out.flush();
    }
}
