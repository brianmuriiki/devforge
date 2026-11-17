package com.devforge.tasks.cli;

import com.devforge.cli.JsonOutput;
import com.devforge.cli.OutputOptions;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.tasks.model.BugStatus;
import com.devforge.tasks.model.TaskPriority;
import com.devforge.tasks.model.TaskStats;
import com.devforge.tasks.service.BugService;
import com.devforge.tasks.service.TaskService;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "stats",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Show task progress and bug counts.")
public class TaskStatsCommand implements Runnable {

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>",
            description = "Only this project (default: all projects).")
    String project;

    private final AppContext context;

    public TaskStatsCommand(AppContext context) {
        this.context = context;
    }

    /** The JSON shape of task statistics; shared with the combined report. */
    public static Map<String, Object> toMap(TaskStats s, Map<BugStatus, Integer> bugs) {
        Map<String, Object> tasks = new LinkedHashMap<>();
        tasks.put("total", s.total());
        tasks.put("todo", s.todo());
        tasks.put("inProgress", s.inProgress());
        tasks.put("blocked", s.blocked());
        tasks.put("done", s.done());
        tasks.put("overdue", s.overdue());
        tasks.put("completionPercent", s.completionPercent());
        Map<String, Object> priorities = new LinkedHashMap<>();
        for (TaskPriority p : TaskPriority.values()) {
            priorities.put(p.name(), s.byPriority().getOrDefault(p, 0));
        }
        tasks.put("byPriority", priorities);
        Map<String, Object> bugMap = new LinkedHashMap<>();
        for (BugStatus b : BugStatus.values()) {
            bugMap.put(b.name(), bugs.getOrDefault(b, 0));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tasks", tasks);
        m.put("bugs", bugMap);
        return m;
    }

    @Override
    public void run() {
        Long projectId = project == null ? null : context.projectRegistry().resolve(project).id();
        TaskService tasks = TaskServices.create(context);
        BugService bugs = BugServices.create(context);
        TaskStats s = tasks.stats(projectId);
        Map<BugStatus, Integer> bugCounts = bugs.statusCounts(projectId);
        PrintWriter out = spec.commandLine().getOut();

        if (output.json) {
            Map<String, Object> doc = JsonOutput.document();
            doc.putAll(toMap(s, bugCounts));
            out.println(JsonOutput.toJson(doc));
        } else {
            out.println("Tasks:          " + s.total());
            out.println("  Completed:    " + s.done() + " (" + s.completionPercent() + "%)");
            out.println("  In progress:  " + s.inProgress());
            out.println("  To do:        " + s.todo());
            out.println("  Blocked:      " + s.blocked());
            out.println("  Overdue:      " + s.overdue());
            StringBuilder pr = new StringBuilder();
            for (TaskPriority p : TaskPriority.values()) {
                pr.append(pr.length() == 0 ? "" : ", ").append(p).append(' ').append(s.byPriority().getOrDefault(p, 0));
            }
            out.println("Priorities:     " + pr);
            StringBuilder bs = new StringBuilder();
            for (BugStatus b : BugStatus.values()) {
                bs.append(bs.length() == 0 ? "" : ", ").append(b).append(' ').append(bugCounts.getOrDefault(b, 0));
            }
            out.println("Bugs:           " + bs);
        }
        out.flush();
    }
}
