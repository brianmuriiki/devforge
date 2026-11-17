package com.devforge.environment.engine;

import com.devforge.environment.VaultException;
import com.devforge.environment.model.BackupManifest;
import com.devforge.environment.model.ManifestEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads and writes {@code manifest.json}. Reading treats the text as untrusted and validates everything. */
public final class ManifestJson {

    public static final int MAX_ENTRIES = 200_000;
    public static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern MODE = Pattern.compile("^[r-][w-][x-][r-][w-][x-][r-][w-][x-]$");

    private ManifestJson() {}

    public static String write(BackupManifest m) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", m.formatVersion());
        root.put("appVersion", m.appVersion());
        root.put("profile", m.profile());
        root.put("createdAt", m.createdAt().toString());
        root.put("os", m.os());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ManifestEntry e : m.entries()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", e.path());
            item.put("size", e.size());
            item.put("sha256", e.sha256());
            item.put("mode", e.mode());
            entries.add(item);
        }
        root.put("entries", entries);
        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize the manifest", e);
        }
    }

    public static BackupManifest read(String json) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new VaultException("The backup manifest is not valid JSON.", e);
        }
        if (root == null || !root.isObject()) {
            throw new VaultException("The backup manifest is malformed.");
        }
        int format = root.path("formatVersion").asInt(-1);
        if (format != BackupManifest.CURRENT_FORMAT) {
            throw new VaultException("Unsupported backup format version " + format
                    + ". This DevForge supports version " + BackupManifest.CURRENT_FORMAT + ".");
        }
        JsonNode list = root.get("entries");
        if (list == null || !list.isArray() || list.size() > MAX_ENTRIES) {
            throw new VaultException("The backup manifest is malformed or too large.");
        }
        Instant createdAt;
        try {
            createdAt = Instant.parse(root.path("createdAt").asText(""));
        } catch (DateTimeParseException e) {
            throw new VaultException("The backup manifest has an invalid creation time.");
        }
        List<ManifestEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (JsonNode n : list) {
            String path = n.path("path").asText("");
            SafePaths.validateRelative(path);
            if (!seen.add(path)) {
                throw new VaultException("The backup manifest lists a path twice.");
            }
            long size = n.path("size").asLong(-1);
            String sha = n.path("sha256").asText("");
            if (size < 0 || !SHA256.matcher(sha).matches()) {
                throw new VaultException("The backup manifest has an invalid entry.");
            }
            total += size;
            if (total > MAX_TOTAL_BYTES) {
                throw new VaultException("The backup is larger than the supported maximum.");
            }
            JsonNode modeNode = n.get("mode");
            String mode = modeNode == null || modeNode.isNull() ? null : modeNode.asText("");
            if (mode != null && !MODE.matcher(mode).matches()) {
                mode = null;
            }
            entries.add(new ManifestEntry(path, size, sha, mode));
        }
        return new BackupManifest(format, root.path("appVersion").asText(""), root.path("profile").asText(""),
                createdAt, root.path("os").asText(""), List.copyOf(entries));
    }
}
