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
 * Reads direct dependencies from {@code package.json} plus every resolved package in a sibling
 * {@code package-lock.json} (lockfile v1, v2 or v3). Never runs npm.
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
        LockedPackages locked = lockedPackages(packageJson.resolveSibling("package-lock.json"));

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
                String resolved = locked.directVersions().get(name);
                if (resolved == null && EXACT.matcher(spec).matches()) {
                    resolved = spec.startsWith("v") ? spec.substring(1) : spec;
                }
                result.add(new Dependency(name, spec, resolved, Ecosystem.NPM, true, section));
            }
        }
        Set<String> included = new HashSet<>();
        for (Dependency dependency : result) {
            if (dependency.resolvedVersion() != null) {
                included.add(dependency.name() + "\u0000" + dependency.resolvedVersion());
            }
        }
        for (LockedPackage dependency : locked.all()) {
            String key = dependency.name() + "\u0000" + dependency.version();
            if (included.add(key)) {
                result.add(new Dependency(dependency.name(), dependency.version(), dependency.version(), Ecosystem.NPM,
                        false, "transitive"));
            }
        }
        return result;
    }

    private record LockedPackage(String name, String version, boolean topLevel) {}

    private record LockedPackages(Map<String, String> directVersions, List<LockedPackage> all) {}

    private static LockedPackages lockedPackages(Path lockFile) {
        Map<String, String> directVersions = new HashMap<>();
        List<LockedPackage> packages = new ArrayList<>();
        JsonNode root;
        try {
            if (!Files.isRegularFile(lockFile)) {
                return new LockedPackages(directVersions, packages);
            }
            root = readJson(lockFile);
        } catch (ManifestException e) {
            return new LockedPackages(directVersions, packages); // unreadable lockfile means versions stay unresolved
        }
        if (root == null) {
            return new LockedPackages(directVersions, packages);
        }
        JsonNode packageEntries = root.get("packages");
        if (packageEntries != null && packageEntries.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = packageEntries.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                String name = packageName(key);
                JsonNode version = entry.getValue().get("version");
                if (name != null && version != null && version.isTextual() && EXACT.matcher(version.asText()).matches()) {
                    boolean topLevel = key.equals("node_modules/" + name);
                    packages.add(new LockedPackage(name, version.asText(), topLevel));
                    if (topLevel) {
                        directVersions.put(name, version.asText());
                    }
                }
            }
        } else {
            collectLegacy(root.get("dependencies"), packages, directVersions, true);
        }
        return new LockedPackages(Map.copyOf(directVersions), List.copyOf(packages));
    }

    private static void collectLegacy(
            JsonNode entries, List<LockedPackage> packages, Map<String, String> directVersions, boolean topLevel) {
        if (entries == null || !entries.isObject()) {
            return;
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = entries.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            JsonNode value = entry.getValue();
            JsonNode version = value.get("version");
            if (version != null && version.isTextual() && EXACT.matcher(version.asText()).matches()) {
                packages.add(new LockedPackage(entry.getKey(), version.asText(), topLevel));
                if (topLevel) {
                    directVersions.put(entry.getKey(), version.asText());
                }
            }
            collectLegacy(value.get("dependencies"), packages, directVersions, false);
        }
    }

    private static String packageName(String packagePath) {
        int index = packagePath.lastIndexOf("node_modules/");
        if (index < 0) {
            return null;
        }
        String name = packagePath.substring(index + "node_modules/".length());
        return name.isEmpty() || name.contains("/node_modules/") ? null : name;
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
