package com.devforge.cli;

import com.devforge.core.DevForgeException;
import com.devforge.core.project.Project;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the JSON contract. Every document starts with {@code "schemaVersion"}; increase
 * {@link #SCHEMA_VERSION} only for breaking changes. Values are plain strings and numbers so the
 * output never depends on how Jackson would serialize library types such as Path.
 */
public final class JsonOutput {

    public static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonOutput() {}

    /** A new document containing only the schema version; add fields with {@code put}. */
    public static Map<String, Object> document() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", SCHEMA_VERSION);
        return doc;
    }

    public static Map<String, Object> project(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("name", p.name());
        m.put("rootPath", p.rootPath());
        m.put("description", p.description());
        m.put("status", p.status().name());
        m.put("createdAt", p.createdAt().toString());
        m.put("updatedAt", p.updatedAt().toString());
        return m;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new DevForgeException("Could not produce JSON output", e);
        }
    }
}
