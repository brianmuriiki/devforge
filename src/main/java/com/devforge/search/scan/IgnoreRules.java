package com.devforge.search.scan;

import com.devforge.core.files.SensitiveFiles;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Git-style ignore rules for the project tree: comments, blank lines, {@code *}, {@code ?},
 * {@code **}, leading {@code /}, directory-only trailing {@code /}, and {@code !} negation.
 * Rules from nested {@code .gitignore} files are scoped to their directory; later matching rules
 * win. Rules deliberately do not enter VCS, dependency, build, or secret directories.
 */
public final class IgnoreRules {

    private record Rule(Pattern pattern, boolean negated, boolean dirOnly) {
        boolean matches(String relativePath, boolean isDirectory) {
            if (!dirOnly) {
                return pattern.matcher(relativePath).matches();
            }
            if (isDirectory && pattern.matcher(relativePath).matches()) {
                return true;
            }
            int slash = relativePath.length();
            while ((slash = relativePath.lastIndexOf('/', slash - 1)) >= 0) {
                if (pattern.matcher(relativePath.substring(0, slash)).matches()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final long MAX_GITIGNORE_BYTES = 1024 * 1024;

    private final List<Rule> rules;

    private IgnoreRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static IgnoreRules empty() {
        return new IgnoreRules(List.of());
    }

    public static IgnoreRules parse(List<String> lines) {
        return new IgnoreRules(parse(lines, ""));
    }

    /** Reads the root and nested {@code .gitignore} files, then applies extra patterns last. */
    public static IgnoreRules forProject(Path root, List<String> extraPatterns) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<Path> gitignores = new ArrayList<>();
        try {
            Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                    if (!directory.equals(normalizedRoot)) {
                        String name = directory.getFileName().toString();
                        if (FileClassifier.isExcludedDirectory(name) || SensitiveFiles.isSecretDirectory(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isSymbolicLink() && attrs.isRegularFile()
                            && file.getFileName().toString().equals(".gitignore")) {
                        gitignores.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // A partial set of rules is still safer than failing a read-only index operation.
        }

        gitignores.sort(Comparator
                .comparingInt((Path file) -> normalizedRoot.relativize(file.getParent()).getNameCount())
                .thenComparing(Path::toString));
        List<Rule> parsed = new ArrayList<>();
        for (Path file : gitignores) {
            try {
                if (Files.size(file) > MAX_GITIGNORE_BYTES) {
                    continue;
                }
                parsed.addAll(parse(Files.readAllLines(file, StandardCharsets.UTF_8), relative(normalizedRoot, file.getParent())));
            } catch (IOException e) {
                // Unreadable or non-UTF-8 rule files are skipped; built-in exclusions still apply.
            }
        }
        parsed.addAll(parse(extraPatterns, ""));
        return new IgnoreRules(List.copyOf(parsed));
    }

    /** @param relativePath path relative to the project root, using {@code /} separators */
    public boolean isIgnored(String relativePath, boolean isDirectory) {
        boolean ignored = false;
        for (Rule rule : rules) {
            if (rule.matches(relativePath, isDirectory)) {
                ignored = !rule.negated();
            }
        }
        return ignored;
    }

    /**
     * Returning true keeps an ignored directory traversable when negated rules may re-include a
     * descendant. This is intentionally conservative: correctness takes priority over a small
     * amount of extra directory walking in projects that use negation rules.
     */
    public boolean mayReincludeDescendant(String relativeDirectory) {
        return rules.stream().anyMatch(Rule::negated);
    }

    private static List<Rule> parse(List<String> lines, String scope) {
        List<Rule> parsed = new ArrayList<>();
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
            boolean anchored = line.startsWith("/") || line.contains("/");
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            if (line.isEmpty()) {
                continue;
            }
            String prefix = scope.isEmpty() ? "" : Pattern.quote(scope + "/");
            String regex = "^" + prefix + (anchored ? "" : "(?:.*/)?") + globToRegex(line) + "$";
            parsed.add(new Rule(Pattern.compile(regex), negated, dirOnly));
        }
        return parsed;
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

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }
}
