package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.service.TaskService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "add",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Add a task to a project.")
public class TaskAddCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<title>", description = "Task title.")
    String title;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>",
            description = "Project (default: the registered project containing the current directory).")
    String project;

    @Option(names = {"-d", "--description"}, defaultValue = "", description = "Task description.")
    String description;

    @Option(names = "--priority", defaultValue = "MEDIUM",
            description = "Priority: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    TaskPriority priority;

    @Option(names = "--due", paramLabel = "<YYYY-MM-DD>", description = "Due date.")
    String due;

    private final AppContext context;

    public TaskAddCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        TaskService service = TaskServices.create(context);
        Project target = service.resolveProject(project, Path.of("").toAbsolutePath());
        LocalDate dueDate = due == null ? null : TaskService.parseDueDate(due);
        Task task = service.add(target, title, description, priority, dueDate);

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("task", TaskJson.task(task, service.today()));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Added task " + task.id() + " to project '" + TerminalText.inline(target.name())
                    + "': " + TerminalText.inline(task.title()));
        }
        out.flush();
    }
}
