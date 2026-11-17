package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStatus;
import com.devforge.tasks.service.TaskChanges;
import com.devforge.tasks.service.TaskService;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.Map;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Change a task. Only the options you give are changed.")
public class TaskUpdateCommand implements Runnable {

    /** {@code --due} and {@code --clear-due} cannot be combined (Picocli reports exit code 2). */
    static class DueChange {
        @Option(names = "--due", paramLabel = "<YYYY-MM-DD>", description = "Set the due date.")
        String due;

        @Option(names = "--clear-due", description = "Remove the due date.")
        boolean clearDue;
    }

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id>", description = "Task id.")
    long id;

    @Option(names = "--title", description = "New title.")
    String title;

    @Option(names = {"-d", "--description"}, description = "New description.")
    String description;

    @Option(names = "--priority", description = "New priority: ${COMPLETION-CANDIDATES}.")
    TaskPriority priority;

    @Option(names = "--status", description = "New status: ${COMPLETION-CANDIDATES}.")
    TaskStatus status;

    @ArgGroup(exclusive = true)
    DueChange dueChange;

    private final AppContext context;

    public TaskUpdateCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        LocalDate dueDate = null;
        boolean clearDue = false;
        if (dueChange != null) {
            clearDue = dueChange.clearDue;
            dueDate = dueChange.due == null ? null : TaskService.parseDueDate(dueChange.due);
        }
        TaskService service = TaskServices.create(context);
        Task task = service.update(
                id, new TaskChanges(title, description, priority, status, dueDate, clearDue));

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("task", TaskJson.task(task, service.today()));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Updated task " + task.id() + ": " + TerminalText.inline(task.title())
                    + " [" + task.status() + ", " + task.priority() + "]");
        }
        out.flush();
    }
}
