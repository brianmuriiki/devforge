package com.devforge.core;

import java.io.Console;
import java.util.Optional;

/** Asks the user one question. Empty means no answer could be obtained (no terminal, end of input). */
@FunctionalInterface
public interface Prompter {

    Optional<String> ask(String prompt);

    /** Reads a secret without echoing it where the platform provides an interactive console. */
    default Optional<char[]> askSecret(String prompt) {
        return ask(prompt).map(String::toCharArray);
    }

    /** Uses the process console; empty when there is none (output or input redirected, CI). */
    static Prompter system() {
        return new Prompter() {
            @Override
            public Optional<String> ask(String prompt) {
                Console console = System.console();
                return console == null ? Optional.empty() : Optional.ofNullable(console.readLine("%s", prompt));
            }

            @Override
            public Optional<char[]> askSecret(String prompt) {
                Console console = System.console();
                return console == null ? Optional.empty() : Optional.ofNullable(console.readPassword("%s", prompt));
            }
        };
    }

    /** Never answers. Used by tests and non-interactive contexts. */
    static Prompter unavailable() {
        return prompt -> Optional.empty();
    }
}
