package com.devforge.tasks.cli;

import com.devforge.cli.VersionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Group command: {@code devforge task <subcommand>}. */
@Command(
        name = "task",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Manage tasks.")
public class TaskCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
