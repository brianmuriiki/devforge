package com.devforge.search.cli;

import com.devforge.cli.OutputOptions;
import com.devforge.cli.TerminalText;
import com.devforge.cli.VersionProvider;
import com.devforge.core.AppContext;
import com.devforge.core.project.Project;
import com.devforge.search.SearchException;
import com.devforge.search.model.IndexSummary;
import com.devforge.search.scan.FileWatcher;
import com.devforge.search.service.IndexService;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "watch",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Keep a project's index current while files change. Press Ctrl+C to stop.")
public class SearchWatchCommand implements Runnable {

    private static final Duration RECONCILE_EVERY = Duration.ofMinutes(5);

    @Spec CommandSpec spec;

    @Mixin OutputOptions output;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", description = "A directory inside a registered project.")
    Path directory;

    @Option(names = {"-p", "--project"}, paramLabel = "<id|name>", description = "Project to watch.")
    String project;

    @Option(names = "--seconds", defaultValue = "0",
            description = "Stop after this many seconds (default: run until interrupted).")
    long seconds;

    private final AppContext context;

    public SearchWatchCommand(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        IndexService service = SearchServices.create(context);
        Project p = service.resolveProject(project, directory.toAbsolutePath());
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try (FileWatcher watcher = new FileWatcher(Path.of(p.rootPath()))) {
            report(out, p, service.index(p), "Initial index of");
            if (watcher.incomplete()) {
                err.println("Warning: some directories could not be watched; periodic reconciliation covers them.");
            }
            out.println("Watching '" + TerminalText.inline(p.name()) + "' (Ctrl+C to stop)...");
            out.flush();

            long deadline = seconds > 0 ? System.nanoTime() + Duration.ofSeconds(seconds).toNanos() : Long.MAX_VALUE;
            long lastReconcile = System.nanoTime();
            while (System.nanoTime() < deadline) {
                FileWatcher.Batch batch = watcher.poll(Duration.ofSeconds(1));
                boolean due = System.nanoTime() - lastReconcile > RECONCILE_EVERY.toNanos();
                if (!batch.isEmpty() || due) {
                    Thread.sleep(300); // let a burst of events settle
                    watcher.poll(Duration.ZERO);
                    IndexSummary s = service.index(p); // incremental index doubles as reconciliation
                    lastReconcile = System.nanoTime();
                    if (batch.overflow()) {
                        err.println("Warning: filesystem events were dropped; the index was reconciled by a full scan.");
                    }
                    if (s.added() + s.updated() + s.removed() > 0) {
                        report(out, p, s, "Updated");
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new SearchException("Could not watch " + p.rootPath() + ": " + e.getMessage());
        }
    }

    private static void report(PrintWriter out, Project p, IndexSummary s, String verb) {
        out.println(verb + " '" + TerminalText.inline(p.name()) + "': " + s.added() + " added, " + s.updated()
                + " updated, " + s.removed() + " removed");
        out.flush();
    }
}
