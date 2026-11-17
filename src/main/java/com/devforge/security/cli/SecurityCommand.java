package com.devforge.security.cli;

import com.devforge.cli.VersionProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Group command: {@code devforge security <subcommand>}. */
@Command(
        name = "security",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Dependency security, outdated packages and licenses (DepGuard).")
public class SecurityCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
