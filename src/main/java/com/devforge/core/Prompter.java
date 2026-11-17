package com.devforge.core;

import java.io.Console;
import java.util.Optional;

/** Asks the user one question. Empty means no answer could be obtained (no terminal, end of input). */
@FunctionalInterface
public interface Prompter {

    Optional<String> ask(String prompt);

    /** Uses the process console; empty when there is none (output or input redirected, CI). */
    static Prompter system() {
        return prompt -> {
            Console console = System.console();
            if (console == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(console.readLine("%s", prompt));
        };
    }

    /** Never answers. Used by tests and non-interactive contexts. */
    static Prompter unavailable() {
        return prompt -> Optional.empty();
    }
}
