package com.devforge.security.provider;

import com.devforge.security.model.Advisory;
import com.devforge.security.model.Severity;
import com.devforge.security.parse.PythonParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Converts an OSV vulnerability record into DevForge's {@link Advisory}, validating and bounding
 * every field because the data comes from the network. Severity uses the record's
 * {@code database_specific.severity} when present; CVSS vectors are not scored, so severity may be UNKNOWN.
 */
public final class OsvNormalizer {

    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");
    private static final int MAX_TEXT = 2000;
    private static final int MAX_URL = 500;
    private static final int MAX_LIST = 10;

    private OsvNormalizer() {}

    public static Optional<Advisory> normalize(JsonNode vuln, String packageName) {
        if (vuln == null || !vuln.isObject()) {
            return Optional.empty();
        }
        String id = vuln.path("id").asText("");
        if (!VALID_ID.matcher(id).matches()) {
            return Optional.empty();
        }
        String summary = vuln.path("summary").asText("");
        if (summary.isBlank()) {
            summary = vuln.path("details").asText("");
        }
        summary = truncate(summary.strip().replaceAll("\\s+", " "), MAX_TEXT);

        Severity severity = Severity.parse(vuln.path("database_specific").path("severity").asText(null));

        List<String> fixed = new ArrayList<>();
        List<String> ranges = new ArrayList<>();
        JsonNode affected = vuln.get("affected");
        if (affected != null && affected.isArray()) {
            for (JsonNode a : affected) {
                String affectedName = a.path("package").path("name").asText("");
                if (!affectedName.isEmpty() && !sameName(affectedName, packageName)) {
                    continue;
                }
                JsonNode rangeList = a.get("ranges");
                if (rangeList == null || !rangeList.isArray()) {
                    continue;
                }
                for (JsonNode range : rangeList) {
                    List<String> parts = new ArrayList<>();
                    JsonNode events = range.get("events");
                    if (events != null && events.isArray()) {
                        for (JsonNode event : events) {
                            for (String key : new String[] {"introduced", "fixed", "last_affected"}) {
                                String value = event.path(key).asText("");
                                if (!value.isEmpty() && value.length() <= 100) {
                                    parts.add(key.replace('_', ' ') + " " + value);
                                    if (key.equals("fixed") && !fixed.contains(value) && fixed.size() < MAX_LIST) {
                                        fixed.add(value);
                                    }
                                }
                            }
                        }
                    }
                    if (!parts.isEmpty() && ranges.size() < MAX_LIST) {
                        ranges.add(String.join(", ", parts));
                    }
                }
            }
        }

        List<String> references = new ArrayList<>();
        JsonNode refs = vuln.get("references");
        if (refs != null && refs.isArray()) {
            for (JsonNode ref : refs) {
                String url = ref.path("url").asText("");
                if ((url.startsWith("https://") || url.startsWith("http://")) && url.length() <= MAX_URL
                        && references.size() < MAX_LIST) {
                    references.add(url);
                }
            }
        }
        List<String> aliases = new ArrayList<>();
        JsonNode aliasNode = vuln.get("aliases");
        if (aliasNode != null && aliasNode.isArray()) {
            for (JsonNode alias : aliasNode) {
                String a = alias.asText("");
                if (VALID_ID.matcher(a).matches() && aliases.size() < MAX_LIST) {
                    aliases.add(a);
                }
            }
        }
        return Optional.of(new Advisory(id, summary, severity, List.copyOf(fixed), String.join("; ", ranges),
                List.copyOf(references), List.copyOf(aliases), "osv.dev"));
    }

    private static boolean sameName(String a, String b) {
        return a.equalsIgnoreCase(b) || PythonParser.normalize(a).equals(PythonParser.normalize(b));
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
