package com.devforge.tasks.cli;

import com.devforge.cli.VersionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Group command: {@code devforge bug <subcommand>}. */
@Command(
        name = "bug",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Track bugs.")
public class BugCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
