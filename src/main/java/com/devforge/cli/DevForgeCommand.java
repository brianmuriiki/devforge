package com.devforge.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Root command. Module and core subcommands are registered in {@link CommandFactory}. */
@Command(
        name = "devforge",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Developer productivity and project management CLI.",
        subcommands = {})
public class DevForgeCommand implements Runnable {

    @Spec
    CommandSpec spec;

    /** With no subcommand, show help (to stdout) and succeed. */
    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
