package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TextTable;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.core.project.ProjectRegistry;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStatus;
import com.devforge.tasks.repo.TaskSort;
import com.devforge.tasks.service.TaskService;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "list",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "List tasks, optionally filtered.")
public class TaskListCommand implements Runnable {

    public enum SortKey {
        ID,
        DUE
    }

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>",
            description = "Only tasks of this project (default: all projects).")
    String project;

    @Option(names = "--status", description = "Only this status: ${COMPLETION-CANDIDATES}.")
    TaskStatus status;

    @Option(names = "--priority", description = "Only this priority: ${COMPLETION-CANDIDATES}.")
    TaskPriority priority;

    @Option(names = "--sort", defaultValue = "ID",
            description = "Sort order: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    SortKey sort;

    private final AppContext context;

    public TaskListCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        TaskService service = TaskServices.create(context);
        ProjectRegistry registry = context.projectRegistry();
        Long projectId = project == null ? null : registry.resolve(project).id();

        List<Task> tasks = service.list(
                projectId, status, priority, sort == SortKey.DUE ? TaskSort.DUE_DATE : TaskSort.ID);
        LocalDate today = service.today();
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Task t : tasks) {
                items.add(TaskJson.task(t, today));
            }
            Map<String, Object> doc = JsonOutput.document();
            doc.put("count", tasks.size());
            doc.put("tasks", items);
            out.println(JsonOutput.toJson(doc));
        } else if (tasks.isEmpty()) {
            out.println("No tasks found.");
        } else {
            Map<Long, String> names = new HashMap<>();
            for (Project p : registry.list()) {
                names.put(p.id(), p.name());
            }
            List<List<String>> rows = new ArrayList<>();
            for (Task t : tasks) {
                String due = t.dueDate() == null
                        ? "-"
                        : t.dueDate() + (t.isOverdue(today) ? " (overdue)" : "");
                rows.add(List.of(
                        String.valueOf(t.id()),
                        names.getOrDefault(t.projectId(), "?"),
                        t.status().name(),
                        t.priority().name(),
                        due,
                        t.title()));
            }
            out.print(TextTable.render(
                    List.of("ID", "PROJECT", "STATUS", "PRIORITY", "DUE", "TITLE"), rows));
            out.println(tasks.size() + " task(s)");
        }
        out.flush();
    }
}
