package com.devforge.security.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.security.model.Advisory;
import com.devforge.security.model.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OsvNormalizerTest {

    private Optional<Advisory> normalize(String json, String pkg) throws IOException {
        return OsvNormalizer.normalize(new ObjectMapper().readTree(json), pkg);
    }

    private static final String LOG4J = """
            { "id": "GHSA-jfh8-c2jp-5v3q", "summary": "Remote code injection in Log4j",
              "aliases": ["CVE-2021-44228"],
              "database_specific": { "severity": "CRITICAL" },
              "affected": [
                { "package": { "name": "org.apache.logging.log4j:log4j-core", "ecosystem": "Maven" },
                  "ranges": [ { "type": "ECOSYSTEM", "events": [ { "introduced": "2.0-beta9" }, { "fixed": "2.15.0" } ] } ] },
                { "package": { "name": "other:pkg", "ecosystem": "Maven" },
                  "ranges": [ { "type": "ECOSYSTEM", "events": [ { "introduced": "0" }, { "fixed": "9.9.9" } ] } ] } ],
              "references": [ { "url": "https://example.com/advisory" }, { "url": "javascript:alert(1)" }, { "url": "ftp://x" } ] }""";

    @Test
    void normalizesAllFieldsForTheRequestedPackageOnly() throws IOException {
        Advisory a = normalize(LOG4J, "org.apache.logging.log4j:log4j-core").orElseThrow();
        assertEquals("GHSA-jfh8-c2jp-5v3q", a.id());
        assertEquals("Remote code injection in Log4j", a.summary());
        assertEquals(Severity.CRITICAL, a.severity());
        assertEquals(java.util.List.of("2.15.0"), a.fixedVersions());
        assertEquals("introduced 2.0-beta9, fixed 2.15.0", a.affectedRange());
        assertEquals(java.util.List.of("CVE-2021-44228"), a.aliases());
        assertEquals("osv.dev", a.source());
    }

    @Test
    void dropsNonHttpReferences() throws IOException {
        Advisory a = normalize(LOG4J, "org.apache.logging.log4j:log4j-core").orElseThrow();
        assertEquals(java.util.List.of("https://example.com/advisory"), a.references());
    }

    @Test
    void severityWordsAreMapped() {
        assertEquals(Severity.MEDIUM, Severity.parse("MODERATE"));
        assertEquals(Severity.HIGH, Severity.parse(" high "));
        assertEquals(Severity.UNKNOWN, Severity.parse(null));
        assertEquals(Severity.UNKNOWN, Severity.parse("banana"));
    }

    @Test
    void missingSeverityIsUnknownNotLow() throws IOException {
        Advisory a = normalize("{ \"id\": \"OSV-1\", \"summary\": \"s\" }", "x").orElseThrow();
        assertEquals(Severity.UNKNOWN, a.severity());
    }

    @Test
    void scoresCvssV3WhenTheAdvisoryDoesNotProvideASeverityWord() throws IOException {
        Advisory critical = normalize("""
                { "id": "OSV-CVSS", "severity": [
                  { "type": "CVSS_V3", "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H" }
                ] }""", "x").orElseThrow();
        Advisory medium = normalize("""
                { "id": "OSV-CVSS-2", "severity": [
                  { "type": "CVSS_V3", "score": "CVSS:3.1/AV:L/AC:H/PR:H/UI:R/S:U/C:L/I:L/A:N" }
                ] }""", "x").orElseThrow();

        assertEquals(Severity.CRITICAL, critical.severity());
        assertEquals(Severity.LOW, medium.severity());
    }

    @Test
    void ignoresMalformedCvssVectors() throws IOException {
        Advisory advisory = normalize("""
                { "id": "OSV-BAD-CVSS", "severity": [
                  { "type": "CVSS_V3", "score": "CVSS:3.1/AV:N/AC:BOGUS" }
                ] }""", "x").orElseThrow();
        assertEquals(Severity.UNKNOWN, advisory.severity());
    }

    @Test
    void recordsWithInvalidIdsAreRejected() throws IOException {
        assertTrue(normalize("{ \"id\": \"bad id with spaces\" }", "x").isEmpty());
        assertTrue(normalize("{ \"id\": \"\" }", "x").isEmpty());
        assertTrue(normalize("{ \"summary\": \"no id\" }", "x").isEmpty());
        assertTrue(normalize("[1]", "x").isEmpty());
    }

    @Test
    void longTextIsBounded() throws IOException {
        String json = "{ \"id\": \"OSV-2\", \"details\": \"" + "word ".repeat(2000) + "\" }";
        Advisory a = normalize(json, "x").orElseThrow();
        assertTrue(a.summary().length() <= 2000);
        assertTrue(a.summary().endsWith("..."));
    }

    @Test
    void pypiNamesMatchAfterNormalization() throws IOException {
        String json = "{ \"id\": \"PYSEC-1\", \"affected\": [ { \"package\": { \"name\": \"Flask_Cors\" },"
                + " \"ranges\": [ { \"events\": [ { \"fixed\": \"4.0.1\" } ] } ] } ] }";
        assertEquals(java.util.List.of("4.0.1"), normalize(json, "flask-cors").orElseThrow().fixedVersions());
    }
}
