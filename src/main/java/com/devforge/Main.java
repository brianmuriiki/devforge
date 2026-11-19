package com.devforge;

import com.devforge.cli.CommandFactory;
import com.devforge.cli.ExitCodes;
import com.devforge.cli.InteractiveShell;
import com.devforge.core.AppContext;
import com.devforge.core.DevForgeException;
import com.devforge.core.LoggingManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Application entry point. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Runs the CLI against the real machine (with file logging) and returns the exit code. */
    public static int run(String... args) {
        AppContext context = AppContext.system();
        try {
            LoggingManager.configure(context.paths(), context.config().logLevel());
        } catch (DevForgeException e) {
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.FAILURE;
        }
        return run(context, args);
    }

    /** Runs the CLI with an explicit context. Safe to call from tests. */
    public static int run(AppContext context, String... args) {
        if (args.length == 0) {
            return new InteractiveShell(
                            context,
                            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                            new PrintWriter(System.out, true),
                            new PrintWriter(System.err, true))
                    .run();
        }
        return CommandFactory.create(context).execute(args);
    }
}
