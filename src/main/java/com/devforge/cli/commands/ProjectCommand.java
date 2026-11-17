package com.devforge.cli.commands;

import com.devforge.cli.VersionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Group command: {@code devforge project <subcommand>}. */
@Command(
        name = "project",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Manage registered projects.")
public class ProjectCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
