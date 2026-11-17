package com.devforge.environment.cli;

import com.devforge.cli.VersionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Group command: {@code devforge env profile <subcommand>}. */
@Command(
        name = "profile",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Manage backup profiles.")
public class EnvProfileCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
