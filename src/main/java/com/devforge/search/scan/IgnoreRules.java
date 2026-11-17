package com.devforge.search.scan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A practical subset of {@code .gitignore} syntax: comments, blank lines, {@code *}, {@code ?},
 * {@code **}, a leading {@code /} (anchored), a trailing {@code /} (directories only) and
 * {@code !} negation (last matching rule wins). Only the project's root {@code .gitignore} is read.
 * Re-including a file inside an ignored directory is not supported.
 */
public final class IgnoreRules {

    private record Rule(Pattern pattern, boolean negated, boolean dirOnly) {}

    private static final long MAX_GITIGNORE_BYTES = 1024 * 1024;

    private final List<Rule> rules;

    private IgnoreRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static IgnoreRules empty() {
        return new IgnoreRules(List.of());
    }

    public static IgnoreRules parse(List<String> lines) {
        List<Rule> rules = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            boolean negated = line.startsWith("!");
            if (negated) {
                line = line.substring(1);
            }
            boolean dirOnly = line.endsWith("/");
            if (dirOnly) {
                line = line.substring(0, line.length() - 1);
            }
            boolean anchored = line.contains("/");
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            if (line.isEmpty()) {
                continue;
            }
            String regex = (anchored ? "^" : "^(?:.*/)?") + globToRegex(line) + "$";
            rules.add(new Rule(Pattern.compile(regex), negated, dirOnly));
        }
        return new IgnoreRules(List.copyOf(rules));
    }

    /** The project's root {@code .gitignore} (if any) followed by {@code extraPatterns}. */
    public static IgnoreRules forProject(Path root, List<String> extraPatterns) {
        List<String> lines = new ArrayList<>();
        Path gitignore = root.resolve(".gitignore");
        try {
            if (Files.isRegularFile(gitignore) && Files.size(gitignore) <= MAX_GITIGNORE_BYTES) {
                lines.addAll(Files.readAllLines(gitignore, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // An unreadable or non-UTF-8 .gitignore is skipped; built-in exclusions still apply.
        }
        lines.addAll(extraPatterns);
        return parse(lines);
    }

    /** @param relativePath path relative to the project root, using {@code /} separators */
    public boolean isIgnored(String relativePath, boolean isDirectory) {
        boolean ignored = false;
        for (Rule rule : rules) {
            if (rule.dirOnly() && !isDirectory) {
                continue;
            }
            if (rule.pattern().matcher(relativePath).matches()) {
                ignored = !rule.negated();
            }
        }
        return ignored;
    }

    static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char ch = glob.charAt(i);
            switch (ch) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            i++;
                            sb.append("(?:.*/)?");
                        } else {
                            sb.append(".*");
                        }
                    } else {
                        sb.append("[^/]*");
                    }
                }
                case '?' -> sb.append("[^/]");
                default -> sb.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return sb.toString();
    }
}
