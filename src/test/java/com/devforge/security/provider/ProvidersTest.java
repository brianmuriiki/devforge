package com.devforge.security.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.security.model.Advisory;
import com.devforge.security.model.Dependency;
import com.devforge.security.model.Ecosystem;
import com.devforge.security.model.PackageMetadata;
import com.devforge.security.model.Severity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ProvidersTest {

    /** Scripted transport: records requests and answers from a function. */
    private static final class FakeTransport implements HttpTransport {
        final List<String> urls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final Function<String, String> responder;

        FakeTransport(Function<String, String> responder) {
            this.responder = responder;
        }

        @Override
        public String get(String url) throws IOException {
            urls.add(url);
            String r = responder.apply(url);
            if (r == null) {
                throw new IOException("HTTP 404");
            }
            return r;
        }

        @Override
        public String post(String url, String body) throws IOException {
            urls.add(url);
            bodies.add(body);
            String r = responder.apply(body);
            if (r == null) {
                throw new IOException("HTTP 500");
            }
            return r;
        }
    }

    private static Dependency dep(Ecosystem e, String name, String version) {
        return new Dependency(name, version == null ? "" : version, version, e, true, "compile");
    }

    @Test
    void osvQueryContainsPackageEcosystemAndExactVersion() throws IOException {
        FakeTransport t = new FakeTransport(b -> "{}");
        new OsvAdvisoryProvider(t).advisoriesFor(dep(Ecosystem.MAVEN, "g:a", "1.2.3"));
        assertEquals("https://api.osv.dev/v1/query", t.urls.get(0));
        String body = t.bodies.get(0);
        assertTrue(body.contains("\"version\":\"1.2.3\""));
        assertTrue(body.contains("\"name\":\"g:a\""));
        assertTrue(body.contains("\"ecosystem\":\"Maven\""));
    }

    @Test
    void osvResultsAreNormalizedAndInvalidRecordsDropped() throws IOException {
        FakeTransport t = new FakeTransport(b -> """
                { "vulns": [ { "id": "GHSA-1", "summary": "bad", "database_specific": { "severity": "HIGH" } },
                             { "id": "no good" } ] }""");
        List<Advisory> found = new OsvAdvisoryProvider(t).advisoriesFor(dep(Ecosystem.NPM, "x", "1.0.0"));
        assertEquals(1, found.size());
        assertEquals(Severity.HIGH, found.get(0).severity());
    }

    @Test
    void osvFollowsPaginationTokens() throws IOException {
        FakeTransport t = new FakeTransport(b -> b.contains("page_token")
                ? "{ \"vulns\": [ { \"id\": \"B-2\" } ] }"
                : "{ \"vulns\": [ { \"id\": \"A-1\" } ], \"next_page_token\": \"tok\" }");
        List<Advisory> found = new OsvAdvisoryProvider(t).advisoriesFor(dep(Ecosystem.PYPI, "p", "1"));
        assertEquals(List.of("A-1", "B-2"), found.stream().map(Advisory::id).toList());
        assertEquals(2, t.bodies.size());
    }

    @Test
    void osvNetworkFailurePropagatesInsteadOfLookingClean() {
        FakeTransport t = new FakeTransport(b -> null);
        assertThrows(IOException.class, () -> new OsvAdvisoryProvider(t).advisoriesFor(dep(Ecosystem.NPM, "x", "1.0.0")));
    }

    @Test
    void osvGarbageResponseIsAnError() {
        FakeTransport t = new FakeTransport(b -> "<html>not json</html>");
        assertThrows(IOException.class, () -> new OsvAdvisoryProvider(t).advisoriesFor(dep(Ecosystem.NPM, "x", "1.0.0")));
    }

    @Test
    void advisoryJsonRoundTripsAndDamageIsDetected() {
        Advisory a = new Advisory("GHSA-1", "summary", Severity.MEDIUM, List.of("2.0"), "fixed 2.0",
                List.of("https://e.com"), List.of("CVE-1"), "osv.dev");
        Optional<List<Advisory>> back = AdvisoryJson.fromJson(AdvisoryJson.toJson(List.of(a)));
        assertEquals(List.of(a), back.orElseThrow());
        assertEquals(List.of(), AdvisoryJson.fromJson("[]").orElseThrow());
        assertFalse(AdvisoryJson.fromJson("{ damaged").isPresent());
        assertFalse(AdvisoryJson.fromJson("{\"not\":\"an array\"}").isPresent());
    }

    @Test
    void npmMetadata() throws IOException {
        FakeTransport t = new FakeTransport(u -> "{ \"version\": \"4.19.2\", \"license\": \"MIT\" }");
        PackageMetadata m = new RegistryMetadataProvider(t).lookup(dep(Ecosystem.NPM, "@scope/pkg", "1.0.0"));
        assertEquals("https://registry.npmjs.org/@scope%2Fpkg/latest", t.urls.get(0));
        assertEquals("4.19.2", m.latestVersion());
        assertEquals("MIT", m.license());
    }

    @Test
    void pypiMetadataPrefersShortLicenseThenClassifier() throws IOException {
        FakeTransport t = new FakeTransport(u -> "{ \"info\": { \"version\": \"2.32.0\", \"license\": \""
                + "x".repeat(500) + "\", \"classifiers\": [\"Programming Language :: Python\","
                + " \"License :: OSI Approved :: Apache Software License\"] } }");
        PackageMetadata m = new RegistryMetadataProvider(t).lookup(dep(Ecosystem.PYPI, "requests", "2.0.0"));
        assertEquals("2.32.0", m.latestVersion());
        assertEquals("Apache Software License", m.license());
    }

    @Test
    void mavenMetadataReadsLatestVersionAndPomLicense() throws IOException {
        FakeTransport t = new FakeTransport(u -> {
            if (u.startsWith("https://search.maven.org/")) {
                return "{ \"response\": { \"docs\": [ { \"latestVersion\": \"2.17.1\" } ] } }";
            }
            if (u.equals("https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.14.0/log4j-core-2.14.0.pom")) {
                return "<project><licenses><license><name>Apache License, Version 2.0</name></license></licenses></project>";
            }
            return null;
        });
        PackageMetadata m = new RegistryMetadataProvider(t)
                .lookup(dep(Ecosystem.MAVEN, "org.apache.logging.log4j:log4j-core", "2.14.0"));
        assertEquals("2.17.1", m.latestVersion());
        assertEquals("Apache License, Version 2.0", m.license());
        assertTrue(t.urls.get(0).contains("q=g%3A%22org.apache.logging.log4j%22"));
    }

    @Test
    void mavenLicenseStaysUnknownWhenPomCannotBeFetched() throws IOException {
        FakeTransport t = new FakeTransport(u -> u.startsWith("https://search.maven.org/")
                ? "{ \"response\": { \"docs\": [ { \"latestVersion\": \"1.1\" } ] } }" : null);
        PackageMetadata m = new RegistryMetadataProvider(t).lookup(dep(Ecosystem.MAVEN, "g:a", "1.0"));
        assertEquals("1.1", m.latestVersion());
        assertNull(m.license());
    }
}
