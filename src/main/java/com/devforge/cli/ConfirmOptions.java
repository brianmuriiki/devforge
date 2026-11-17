package com.devforge.cli;

import picocli.CommandLine.Option;

/** Added to destructive commands with {@code @Mixin}. */
public class ConfirmOptions {

    @Option(names = {"-y", "--yes"}, description = "Do not ask for confirmation.")
    public boolean yes;
}
