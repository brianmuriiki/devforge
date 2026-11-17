package com.devforge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.AppContext;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CliTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Result(int code, String out, String err) {}

    @TempDir Path tmp;

    private AppContext context;

    @BeforeEach
    void setUp() {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        context = new AppContext(paths, CLOCK);
    }

    private Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = CommandFactory.create(context);
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(err, true));
        int code = cmd.execute(args);
        return new Result(code, out.toString(), err.toString());
    }

    private Path projectDir(String name) throws IOException {
        return Files.createDirectories(tmp.resolve("work").resolve(name));
    }

    @Test
    void initRegistersProject() throws IOException {
        Result r = run("init", projectDir("demo-app").toString());
        assertEquals(0, r.code());
        assertTrue(r.out().contains("Registered project 'demo-app' (id 1)"));
        assertEquals("", r.err());
    }

    @Test
    void initWithJsonReturnsProjectDocument() throws IOException {
        Result r = run("init", projectDir("demo").toString(), "--name", "Demo", "--json");
        assertEquals(0, r.code());
        JsonNode doc = parse(r.out());
        assertEquals(1, doc.get("schemaVersion").asInt());
        assertEquals("Demo", doc.get("project").get("name").asText());
        assertEquals("ACTIVE", doc.get("project").get("status").asText());
    }

    @Test
    void initTwiceFailsCleanlyWithExitCodeOne() throws IOException {
        String dir = projectDir("demo-app").toString();
        run("init", dir);
        Result r = run("init", dir);

        assertEquals(1, r.code());
        assertEquals("", r.out());
        assertTrue(r.err().startsWith("Error: Project is already registered"));
        assertFalse(r.err().contains("\tat "), "no stack trace without --verbose");
    }

    @Test
    void initOnMissingDirectoryFails() {
        Result r = run("init", tmp.resolve("missing").toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("does not exist"));
    }

    @Test
    void verboseShowsStackTrace() throws IOException {
        String dir = projectDir("demo-app").toString();
        run("init", dir);
        Result r = run("init", dir, "--verbose");

        assertEquals(1, r.code());
        assertTrue(r.err().contains("at com.devforge"));
    }

    @Test
    void projectListWhenEmptyExplainsNextStep() {
        Result r = run("project", "list");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("No projects registered"));
    }

    @Test
    void projectListShowsRegisteredProjects() throws IOException {
        run("init", projectDir("alpha").toString());
        run("init", projectDir("beta").toString());

        Result r = run("project", "list");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("NAME"));
        assertTrue(r.out().contains("alpha"));
        assertTrue(r.out().contains("beta"));
    }

    @Test
    void projectListJsonHasStableShape() throws IOException {
        run("init", projectDir("alpha").toString());

        JsonNode doc = parse(run("project", "list", "--json").out());
        assertEquals(1, doc.get("schemaVersion").asInt());
        assertEquals(1, doc.get("count").asInt());
        JsonNode first = doc.get("projects").get(0);
        assertEquals("alpha", first.get("name").asText());
        assertTrue(first.get("rootPath").asText().endsWith("alpha"));
        assertEquals("2026-11-13T00:00:00Z", first.get("createdAt").asText());
    }

    @Test
    void statusPrintsDatabaseSummary() {
        Result r = run("status");
        assertEquals(0, r.code());
        assertTrue(r.out().startsWith("DevForge "));
        assertTrue(r.out().contains("Schema version: 6"));
        assertTrue(r.out().contains("Projects:       0"));
    }

    @Test
    void statusJsonReportsCountsAndSchema() throws IOException {
        run("init", projectDir("alpha").toString());

        JsonNode doc = parse(run("status", "--json").out());
        assertEquals(1, doc.get("schemaVersion").asInt());
        assertEquals(6, doc.get("databaseSchemaVersion").asInt());
        assertEquals(1, doc.get("projectCount").asInt());
        assertTrue(doc.get("foreignKeysEnabled").asBoolean());
    }

    @Test
    void unknownCommandIsUsageError() {
        Result r = run("frobnicate");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("Unmatched argument"));
    }

    private static JsonNode parse(String text) {
        try {
            return JSON.readTree(text);
        } catch (IOException e) {
            throw new AssertionError("Output was not valid JSON:\n" + text, e);
        }
    }
}
