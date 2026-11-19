package com.devforge.security.parse;

import com.devforge.security.model.Dependency;
import com.devforge.security.model.Ecosystem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads dependencies from {@code requirements.txt} and {@code pyproject.toml} ({@code [project]}
 * and Poetry tables) with a small purpose-built reader. Only exact pins ({@code ==1.2.3}) count as
 * resolved. Never runs pip.
 */
public final class PythonParser {

    private static final Pattern REQUIREMENT =
            Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)\\s*(?:\\[[^]]*])?\\s*(.*)$");
    private static final Pattern EXACT_PIN = Pattern.compile("^==\\s*([A-Za-z0-9][A-Za-z0-9._+!-]*)$");
    private static final Pattern BARE_VERSION = Pattern.compile("^\\d[A-Za-z0-9._+!-]*$");
    private static final Pattern POETRY_KEY = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)\\s*=\\s*(.+)$");
    private static final Pattern POETRY_TABLE =
            Pattern.compile("^tool\\.poetry\\.(?:dependencies|dev-dependencies|group\\.[^.]+\\.dependencies)$");
    private static final Pattern REQUIREMENT_INCLUDE =
            Pattern.compile("^(?:-r|--requirement)(?:\\s+|=)(.+)$", Pattern.CASE_INSENSITIVE);
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_REQUIREMENT_INCLUDE_DEPTH = 16;

    private PythonParser() {}

    public static List<Dependency> parse(Path file) throws ManifestException {
        if (file.getFileName().toString().equals("pyproject.toml")) {
            return parsePyproject(readLines(file));
        }
        Path normalized = file.toAbsolutePath().normalize();
        Path root = normalized.getParent();
        if (root == null) {
            throw new ManifestException("Could not determine the directory for " + file.getFileName() + ".");
        }
        return parseRequirementsFile(normalized, root, new HashSet<>(), new HashSet<>(), 0);
    }

    private static List<String> readLines(Path file) throws ManifestException {
        List<String> lines;
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new ManifestException(file.getFileName() + " is too large to parse.");
            }
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ManifestException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
        }
        return lines;
    }

    private static List<Dependency> parseRequirementsFile(
            Path file, Path root, Set<Path> active, Set<Path> parsed, int depth) throws ManifestException {
        if (depth > MAX_REQUIREMENT_INCLUDE_DEPTH) {
            throw new ManifestException("Requirements include nesting exceeds " + MAX_REQUIREMENT_INCLUDE_DEPTH + " files.");
        }
        if (!file.startsWith(root)) {
            throw new ManifestException("Requirements include escapes the project directory: " + file + ".");
        }
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
            throw new ManifestException("Requirements include is not a regular file: " + file + ".");
        }
        if (parsed.contains(file)) {
            return List.of();
        }
        if (!active.add(file)) {
            throw new ManifestException("Circular requirements include: " + file + ".");
        }
        try {
            List<Dependency> result = new ArrayList<>();
            for (String line : readLines(file)) {
                String included = requirementInclude(line);
                if (included == null) {
                    parseRequirement(line, "requirements").ifPresent(result::add);
                    continue;
                }
                Path target;
                try {
                    target = file.getParent().resolve(included).normalize();
                } catch (RuntimeException e) {
                    throw new ManifestException("Invalid requirements include in " + file.getFileName() + ": " + included, e);
                }
                result.addAll(parseRequirementsFile(target, root, active, parsed, depth + 1));
            }
            parsed.add(file);
            return result;
        } finally {
            active.remove(file);
        }
    }

    private static String requirementInclude(String raw) {
        String line = raw.strip();
        int comment = line.indexOf(" #");
        if (comment >= 0) {
            line = line.substring(0, comment).strip();
        }
        Matcher include = REQUIREMENT_INCLUDE.matcher(line);
        if (!include.matches()) {
            return null;
        }
        String value = include.group(1).strip();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    public static List<Dependency> parseRequirements(List<String> lines) {
        List<Dependency> result = new ArrayList<>();
        for (String line : lines) {
            parseRequirement(line, "requirements").ifPresent(result::add);
        }
        return result;
    }

    /** Parses one PEP 508 style requirement such as {@code requests[security]>=2.0; python_version>"3"}. */
    static Optional<Dependency> parseRequirement(String raw, String scope) {
        String line = raw.strip();
        int comment = line.indexOf(" #");
        if (comment >= 0) {
            line = line.substring(0, comment).strip();
        }
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("-") || line.startsWith(".")
                || line.startsWith("/") || line.contains("://")) {
            return Optional.empty();
        }
        int marker = line.indexOf(';');
        if (marker >= 0) {
            line = line.substring(0, marker).strip();
        }
        int options = line.indexOf(" --");
        if (options >= 0) {
            line = line.substring(0, options).strip();
        }
        Matcher m = REQUIREMENT.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        String name = normalize(m.group(1));
        String spec = m.group(2).strip();
        if (spec.startsWith("(") && spec.endsWith(")")) {
            spec = spec.substring(1, spec.length() - 1).strip();
        }
        String resolved = null;
        Matcher pin = EXACT_PIN.matcher(spec);
        if (pin.matches() && !pin.group(1).contains("*")) {
            resolved = pin.group(1);
        }
        return Optional.of(new Dependency(name, spec, resolved, Ecosystem.PYPI, true, scope));
    }

    static List<Dependency> parsePyproject(List<String> lines) {
        List<Dependency> result = new ArrayList<>();
        String section = "";
        StringBuilder array = null;
        for (String raw : lines) {
            String t = raw.strip();
            if (array != null) {
                array.append(' ').append(t);
                if (arrayClosed(array.toString())) {
                    for (String s : quotedStrings(array.toString())) {
                        parseRequirement(s, "project").ifPresent(result::add);
                    }
                    array = null;
                }
                continue;
            }
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.startsWith("[") && t.endsWith("]") && !t.contains("=")) {
                section = t.replaceAll("^\\[+|]+$", "").strip();
                continue;
            }
            boolean projectDeps = section.equals("project") && t.matches("^dependencies\\s*=\\s*\\[.*");
            boolean optionalDeps = section.equals("project.optional-dependencies") && t.matches("^[A-Za-z0-9._-]+\\s*=\\s*\\[.*");
            if (projectDeps || optionalDeps) {
                array = new StringBuilder(t.substring(t.indexOf('[')));
                if (arrayClosed(array.toString())) {
                    for (String s : quotedStrings(array.toString())) {
                        parseRequirement(s, projectDeps ? "project" : "optional").ifPresent(result::add);
                    }
                    array = null;
                }
            } else if (POETRY_TABLE.matcher(section).matches()) {
                Matcher m = POETRY_KEY.matcher(t);
                if (m.matches() && !m.group(1).equalsIgnoreCase("python")) {
                    result.add(poetryDependency(m.group(1), m.group(2).strip()));
                }
            }
        }
        return result;
    }

    private static Dependency poetryDependency(String name, String rhs) {
        String spec = "";
        if (rhs.startsWith("\"") || rhs.startsWith("'")) {
            List<String> s = quotedStrings(rhs);
            spec = s.isEmpty() ? "" : s.get(0);
        } else if (rhs.startsWith("{")) {
            Matcher v = Pattern.compile("version\\s*=\\s*[\"']([^\"']+)[\"']").matcher(rhs);
            spec = v.find() ? v.group(1) : "";
        }
        String resolved = null;
        String candidate = spec.startsWith("==") ? spec.substring(2).strip() : spec;
        if (BARE_VERSION.matcher(candidate).matches() && !candidate.contains("*")) {
            resolved = candidate;
        }
        return new Dependency(normalize(name), spec, resolved, Ecosystem.PYPI, true, "poetry");
    }

    /** PyPI name normalization (PEP 503): lower case, runs of {@code - _ .} become a single {@code -}. */
    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[-_.]+", "-");
    }

    /** True once the array's closing bracket appears outside any string. */
    private static boolean arrayClosed(String text) {
        char quote = 0;
        int depth = 0;
        for (char ch : text.toCharArray()) {
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '[') {
                depth++;
            } else if (ch == ']' && --depth == 0) {
                return true;
            }
        }
        return false;
    }

    private static List<String> quotedStrings(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = null;
        char quote = 0;
        for (char ch : text.toCharArray()) {
            if (quote != 0) {
                if (ch == quote) {
                    out.add(current.toString());
                    quote = 0;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
                current = new StringBuilder();
            }
        }
        return out;
    }
}
