package com.devforge.environment.cli;

import com.devforge.cli.VersionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Group command: {@code devforge env <subcommand>}. */
@Command(
        name = "env",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Back up and restore development environment configuration (DevVault).")
public class EnvCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
