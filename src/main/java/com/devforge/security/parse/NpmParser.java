package com.devforge.security.parse;

import com.devforge.security.model.Dependency;
import com.devforge.security.model.Ecosystem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads direct dependencies from {@code package.json}. Resolved versions come from a sibling
 * {@code package-lock.json} (lockfile v1, v2 or v3) or from exact version pins. Never runs npm.
 */
public final class NpmParser {

    private static final Pattern EXACT = Pattern.compile("^v?\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.+-]+)?$");
    private static final String[] SECTIONS = {"dependencies", "devDependencies", "optionalDependencies"};
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private NpmParser() {}

    public static List<Dependency> parse(Path packageJson) throws ManifestException {
        JsonNode root = readJson(packageJson);
        if (root == null || !root.isObject()) {
            throw new ManifestException(packageJson.getFileName() + " is not a JSON object.");
        }
        Map<String, String> locked = lockedVersions(packageJson.resolveSibling("package-lock.json"));

        List<Dependency> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String section : SECTIONS) {
            JsonNode node = root.get(section);
            if (node == null || !node.isObject()) {
                continue;
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String name = entry.getKey();
                String spec = entry.getValue().asText("");
                if (!seen.add(name)) {
                    continue;
                }
                String resolved = locked.get(name);
                if (resolved == null && EXACT.matcher(spec).matches()) {
                    resolved = spec.startsWith("v") ? spec.substring(1) : spec;
                }
                result.add(new Dependency(name, spec, resolved, Ecosystem.NPM, true, section));
            }
        }
        return result;
    }

    private static Map<String, String> lockedVersions(Path lockFile) {
        Map<String, String> versions = new HashMap<>();
        JsonNode root;
        try {
            if (!Files.isRegularFile(lockFile)) {
                return versions;
            }
            root = readJson(lockFile);
        } catch (ManifestException e) {
            return versions; // an unreadable lockfile just means versions stay unresolved
        }
        if (root == null) {
            return versions;
        }
        JsonNode packages = root.get("packages");
        if (packages != null && packages.isObject()) {
            String prefix = "node_modules/";
            for (Iterator<Map.Entry<String, JsonNode>> it = packages.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                if (key.startsWith(prefix) && key.indexOf(prefix, prefix.length()) < 0) {
                    JsonNode version = entry.getValue().get("version");
                    if (version != null && version.isTextual()) {
                        versions.put(key.substring(prefix.length()), version.asText());
                    }
                }
            }
        }
        JsonNode legacy = root.get("dependencies");
        if (legacy != null && legacy.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = legacy.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode version = entry.getValue().get("version");
                if (version != null && version.isTextual()) {
                    versions.putIfAbsent(entry.getKey(), version.asText());
                }
            }
        }
        return versions;
    }

    private static JsonNode readJson(Path file) throws ManifestException {
        try {
            if (Files.size(file) > MAX_BYTES) {
                throw new ManifestException(file.getFileName() + " is too large to parse.");
            }
            return new ObjectMapper().readTree(Files.readString(file));
        } catch (IOException e) {
            throw new ManifestException("Could not read " + file.getFileName() + " as JSON.", e);
        }
    }
}
