package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.tasks.model.Task;
import com.devforge.tasks.service.TaskService;
import java.io.PrintWriter;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "complete",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Mark a task as done.")
public class TaskCompleteCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", paramLabel = "<id>", description = "Task id.")
    long id;

    private final AppContext context;

    public TaskCompleteCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        TaskService service = TaskServices.create(context);
        Task task = service.complete(id);
        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("task", TaskJson.task(task, service.today()));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Completed task " + task.id() + ": " + TerminalText.inline(task.title()));
        }
        out.flush();
    }
}
