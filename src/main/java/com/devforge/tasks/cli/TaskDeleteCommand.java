package com.devforge.tasks.cli;

import com.devforge.cli.ConfirmOptions;
import com.devforge.cli.Confirmation;
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
        name = "delete",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Permanently delete a task.")
public class TaskDeleteCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Mixin ConfirmOptions confirm;

    @Parameters(index = "0", paramLabel = "<id>", description = "Task id.")
    long id;

    private final AppContext context;

    public TaskDeleteCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        TaskService service = TaskServices.create(context);
        Task task = service.get(id); // fails early with "not found" before asking anything
        Confirmation.require(context, confirm,
                "Delete task " + task.id() + " '" + TerminalText.inline(task.title())
                        + "'? This cannot be undone.");
        Task deleted = service.delete(id);

        PrintWriter out = spec.commandLine().getOut();
        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.put("deleted", TaskJson.task(deleted, service.today()));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Deleted task " + deleted.id() + ": " + TerminalText.inline(deleted.title()));
        }
        out.flush();
    }
}
