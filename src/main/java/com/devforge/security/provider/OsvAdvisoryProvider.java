package com.devforge.security.provider;

import com.devforge.security.model.Advisory;
import com.devforge.security.model.Dependency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Queries OSV.dev (https://osv.dev) for advisories affecting an exact package version. */
public final class OsvAdvisoryProvider implements AdvisoryProvider {

    static final String QUERY_URL = "https://api.osv.dev/v1/query";
    private static final int MAX_PAGES = 5;

    private final HttpTransport http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OsvAdvisoryProvider(HttpTransport http) {
        this.http = http;
    }

    @Override
    public String name() {
        return "osv.dev";
    }

    @Override
    public List<Advisory> advisoriesFor(Dependency dependency) throws IOException {
        List<Advisory> out = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("name", dependency.name());
            pkg.put("ecosystem", dependency.ecosystem().osvName());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("version", dependency.resolvedVersion());
            body.put("package", pkg);
            if (pageToken != null) {
                body.put("page_token", pageToken);
            }
            JsonNode response = mapper.readTree(http.post(QUERY_URL, mapper.writeValueAsString(body)));
            JsonNode vulns = response == null ? null : response.get("vulns");
            if (vulns != null && vulns.isArray()) {
                for (JsonNode vuln : vulns) {
                    OsvNormalizer.normalize(vuln, dependency.name()).ifPresent(out::add);
                }
            }
            JsonNode next = response == null ? null : response.get("next_page_token");
            if (next == null || !next.isTextual() || next.asText().isEmpty()) {
                break;
            }
            pageToken = next.asText();
        }
        return out;
    }
}
