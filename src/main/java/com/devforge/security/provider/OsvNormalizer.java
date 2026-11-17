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
 * every field because the data comes from the network. Severity prefers the record's
 * {@code database_specific.severity}, then scores a valid CVSS v3 base vector when available.
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
        if (severity == Severity.UNKNOWN) {
            severity = cvssSeverity(vuln.get("severity"));
        }

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

    private static Severity cvssSeverity(JsonNode severities) {
        if (severities == null || !severities.isArray()) {
            return Severity.UNKNOWN;
        }
        for (JsonNode item : severities) {
            String type = item.path("type").asText("");
            String vector = item.path("score").asText("");
            if (type.startsWith("CVSS_V3") || vector.startsWith("CVSS:3.")) {
                Severity scored = CvssV3.severity(vector);
                if (scored != Severity.UNKNOWN) {
                    return scored;
                }
            }
        }
        return Severity.UNKNOWN;
    }

    /** Minimal, strict CVSS v3.0/v3.1 base-score calculator. */
    private static final class CvssV3 {

        private CvssV3() {}

        static Severity severity(String vector) {
            try {
                String[] parts = vector.split("/");
                if (parts.length < 9 || !(parts[0].equals("CVSS:3.0") || parts[0].equals("CVSS:3.1"))) {
                    return Severity.UNKNOWN;
                }
                java.util.Map<String, String> values = new java.util.HashMap<>();
                for (int index = 1; index < parts.length; index++) {
                    String[] metric = parts[index].split(":", 2);
                    if (metric.length == 2) {
                        values.put(metric[0], metric[1]);
                    }
                }
                double av = metric(values, "AV", "N", .85, "A", .62, "L", .55, "P", .2);
                double ac = metric(values, "AC", "L", .77, "H", .44);
                double ui = metric(values, "UI", "N", .85, "R", .62);
                boolean changedScope = "C".equals(values.get("S"));
                if (!changedScope && !"U".equals(values.get("S"))) {
                    return Severity.UNKNOWN;
                }
                double pr = changedScope
                        ? metric(values, "PR", "N", .85, "L", .68, "H", .5)
                        : metric(values, "PR", "N", .85, "L", .62, "H", .27);
                double confidentiality = metric(values, "C", "H", .56, "L", .22, "N", 0);
                double integrity = metric(values, "I", "H", .56, "L", .22, "N", 0);
                double availability = metric(values, "A", "H", .56, "L", .22, "N", 0);
                double impactSubscore = 1 - (1 - confidentiality) * (1 - integrity) * (1 - availability);
                if (impactSubscore <= 0) {
                    return Severity.UNKNOWN;
                }
                double impact = changedScope
                        ? 7.52 * (impactSubscore - .029) - 3.25 * Math.pow(impactSubscore - .02, 15)
                        : 6.42 * impactSubscore;
                double exploitability = 8.22 * av * ac * pr * ui;
                double score = changedScope ? Math.min(1.08 * (impact + exploitability), 10) : Math.min(impact + exploitability, 10);
                score = Math.ceil(score * 10 - 1e-9) / 10;
                if (score >= 9) {
                    return Severity.CRITICAL;
                }
                if (score >= 7) {
                    return Severity.HIGH;
                }
                if (score >= 4) {
                    return Severity.MEDIUM;
                }
                return Severity.LOW;
            } catch (IllegalArgumentException e) {
                return Severity.UNKNOWN;
            }
        }

        private static double metric(java.util.Map<String, String> values, String key, Object... pairs) {
            String value = values.get(key);
            for (int index = 0; index < pairs.length; index += 2) {
                if (pairs[index].equals(value)) {
                    return ((Number) pairs[index + 1]).doubleValue();
                }
            }
            throw new IllegalArgumentException("invalid CVSS metric");
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
