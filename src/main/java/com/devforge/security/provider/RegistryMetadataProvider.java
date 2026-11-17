package com.devforge.security.provider;

import com.devforge.security.model.Dependency;
import com.devforge.security.model.PackageMetadata;
import com.devforge.security.parse.ManifestException;
import com.devforge.security.parse.PomLicenseReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Looks up the latest version and license from the public registries: npm, PyPI and Maven Central
 * (version from the search API, license from the dependency's published POM when its version is exact).
 */
public final class RegistryMetadataProvider implements MetadataProvider {

    private static final int MAX_LICENSE = 100;

    private final HttpTransport http;
    private final ObjectMapper mapper = new ObjectMapper();

    public RegistryMetadataProvider(HttpTransport http) {
        this.http = http;
    }

    @Override
    public PackageMetadata lookup(Dependency dep) throws IOException {
        return switch (dep.ecosystem()) {
            case NPM -> npm(dep);
            case PYPI -> pypi(dep);
            case MAVEN -> maven(dep);
        };
    }

    private PackageMetadata npm(Dependency dep) throws IOException {
        String url = "https://registry.npmjs.org/" + dep.name().replace("/", "%2F") + "/latest";
        JsonNode n = mapper.readTree(http.get(url));
        String license = null;
        JsonNode lic = n.get("license");
        if (lic != null) {
            license = lic.isTextual() ? lic.asText() : lic.path("type").asText(null);
        }
        return new PackageMetadata(text(n.get("version")), bound(license));
    }

    private PackageMetadata pypi(Dependency dep) throws IOException {
        JsonNode n = mapper.readTree(http.get("https://pypi.org/pypi/" + encode(dep.name()) + "/json"));
        JsonNode info = n.path("info");
        String license = info.path("license").asText("");
        if (license.isBlank() || license.length() > MAX_LICENSE) {
            license = null;
            JsonNode classifiers = info.get("classifiers");
            if (classifiers != null && classifiers.isArray()) {
                for (JsonNode c : classifiers) {
                    String s = c.asText("");
                    if (s.startsWith("License ::")) {
                        license = s.substring(s.lastIndexOf("::") + 2).strip();
                        break;
                    }
                }
            }
        }
        return new PackageMetadata(text(info.get("version")), bound(license));
    }

    private PackageMetadata maven(Dependency dep) throws IOException {
        int colon = dep.name().indexOf(':');
        if (colon < 0) {
            return new PackageMetadata(null, null);
        }
        String group = dep.name().substring(0, colon);
        String artifact = dep.name().substring(colon + 1);
        String query = "g:\"" + group + "\" AND a:\"" + artifact + "\"";
        JsonNode result = mapper.readTree(
                http.get("https://search.maven.org/solrsearch/select?q=" + encode(query) + "&rows=1&wt=json"));
        JsonNode docs = result.path("response").path("docs");
        String latest = docs.isArray() && docs.size() > 0 ? text(docs.get(0).get("latestVersion")) : null;

        String license = null;
        if (dep.resolvedVersion() != null) {
            String v = dep.resolvedVersion();
            String url = "https://repo1.maven.org/maven2/" + group.replace('.', '/') + "/" + artifact + "/" + v
                    + "/" + artifact + "-" + v + ".pom";
            try {
                license = PomLicenseReader.firstLicense(http.get(url));
            } catch (IOException | ManifestException e) {
                // License stays unknown; the version information is still useful.
            }
        }
        return new PackageMetadata(latest, bound(license));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode n) {
        return n == null || !n.isTextual() || n.asText().isBlank() ? null : n.asText();
    }

    private static String bound(String license) {
        if (license == null || license.isBlank()) {
            return null;
        }
        String l = license.strip();
        return l.length() <= MAX_LICENSE ? l : l.substring(0, MAX_LICENSE);
    }
}
