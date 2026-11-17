package com.devforge.cli;

import picocli.CommandLine.Option;

/** Options shared by every command, added with {@code @Mixin}. */
public class OutputOptions {

    @Option(names = "--json", description = "Print machine-readable JSON instead of text.")
    public boolean json;

    @Option(names = "--verbose", description = "Show technical details when an error occurs.")
    public boolean verbose;
}
