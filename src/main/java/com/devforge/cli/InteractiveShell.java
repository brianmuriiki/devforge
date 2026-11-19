package com.devforge.cli;

import com.devforge.core.AppContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;

/** Interactive command loop for the CLI. */
public final class InteractiveShell {

    private static final String PROMPT = "\u001B[31mdevforge\u001B[0m> ";

    private final AppContext context;
    private final BufferedReader input;
    private final PrintWriter out;
    private final PrintWriter err;

    public InteractiveShell(AppContext context, BufferedReader input, PrintWriter out, PrintWriter err) {
        this.context = context;
        this.input = input;
        this.out = out;
        this.err = err;
    }

    public int run() {
        String line;
        while (true) {
            out.print(PROMPT);
            out.flush();
            try {
                line = input.readLine();
            } catch (IOException e) {
                err.println("Error: Could not read command: " + e.getMessage());
                err.flush();
                return ExitCodes.FAILURE;
            }
            if (line == null) {
                out.println();
                return ExitCodes.SUCCESS;
            }
            if (line.isBlank()) {
                continue;
            }

            String[] args;
            try {
                args = tokenize(line);
            } catch (IllegalArgumentException e) {
                err.println("Error: " + e.getMessage());
                err.flush();
                continue;
            }
            if (args.length == 0) {
                continue;
            }
            if (args[0].equalsIgnoreCase("exit")) {
                if (args.length == 1) {
                    return ExitCodes.SUCCESS;
                }
                err.println("Usage: exit");
                err.flush();
                continue;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
                args = new String[] {"--help"};
            }
            CommandLine commandLine = CommandFactory.create(context);
            commandLine.setOut(out);
            commandLine.setErr(err);
            commandLine.execute(args);
        }
    }

    private static String[] tokenize(String line) {
        List<String> args = new ArrayList<>();
        StringBuilder argument = new StringBuilder();
        char quote = 0;
        boolean argumentStarted = false;

        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (quote == '\'') {
                if (current == quote) {
                    quote = 0;
                } else {
                    argument.append(current);
                }
                argumentStarted = true;
                continue;
            }
            if (current == '\\' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                boolean escaped = quote == '"'
                        ? next == '"' || next == '\\'
                        : Character.isWhitespace(next) || next == '\'' || next == '"' || next == '\\';
                if (escaped) {
                    argument.append(next);
                    argumentStarted = true;
                    i++;
                    continue;
                }
                argument.append(current);
                argumentStarted = true;
                continue;
            }
            if (quote == '"') {
                if (current == quote) {
                    quote = 0;
                } else {
                    argument.append(current);
                }
                argumentStarted = true;
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                argumentStarted = true;
            } else if (Character.isWhitespace(current)) {
                if (argumentStarted) {
                    args.add(argument.toString());
                    argument.setLength(0);
                    argumentStarted = false;
                }
            } else {
                argument.append(current);
                argumentStarted = true;
            }
        }

        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed quote in command.");
        }
        if (argumentStarted) {
            args.add(argument.toString());
        }
        return args.toArray(String[]::new);
    }
}
