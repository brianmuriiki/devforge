package com.devforge.cli;

/** Documented process exit codes. Codes 3 and 4 are added with DepGuard and warnings. */
public final class ExitCodes {

    public static final int SUCCESS = 0;
    public static final int FAILURE = 1;
    public static final int USAGE = 2; // produced by picocli for invalid arguments

    private ExitCodes() {}
}
