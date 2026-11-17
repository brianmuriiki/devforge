package com.devforge.security.provider;

import com.devforge.security.model.Advisory;
import com.devforge.security.model.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores normalized advisories as JSON text in the advisory cache. */
public final class AdvisoryJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AdvisoryJson() {}

    public static String toJson(List<Advisory> advisories) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Advisory a : advisories) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("summary", a.summary());
            m.put("severity", a.severity().name());
            m.put("fixedVersions", a.fixedVersions());
            m.put("affectedRange", a.affectedRange());
            m.put("references", a.references());
            m.put("aliases", a.aliases());
            m.put("source", a.source());
            items.add(m);
        }
        try {
            return MAPPER.writeValueAsString(items);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize advisories", e);
        }
    }

    /**
     * Returns empty if the cached text is damaged, so the caller treats the cache entry as missing.
     * A damaged entry must never be mistaken for "no advisories".
     */
    public static Optional<List<Advisory>> fromJson(String json) {
        List<Advisory> out = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isArray()) {
                return Optional.empty();
            }
            for (JsonNode n : root) {
                out.add(new Advisory(
                        n.path("id").asText(""),
                        n.path("summary").asText(""),
                        Severity.parse(n.path("severity").asText("")),
                        strings(n.get("fixedVersions")),
                        n.path("affectedRange").asText(""),
                        strings(n.get("references")),
                        strings(n.get("aliases")),
                        n.path("source").asText("")));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(out);
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                out.add(item.asText(""));
            }
        }
        return out;
    }
}
