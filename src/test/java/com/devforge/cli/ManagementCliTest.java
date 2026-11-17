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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ManagementCliTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Result(int code, String out, String err) {}

    @TempDir Path tmp;

    private final List<String> prompts = new ArrayList<>();
    private Optional<String> nextAnswer = Optional.empty();
    private AppContext context;
    private Path appDir;

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        context = new AppContext(paths, CLOCK, prompt -> {
            prompts.add(prompt);
            return nextAnswer;
        });
        appDir = Files.createDirectories(tmp.resolve("work/app"));
        Result init = run("init", appDir.toString(), "--name", "app");
        assertEquals(0, init.code(), init.err());
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

    private JsonNode json(String... args) {
        Result r = run(args);
        assertEquals(0, r.code(), r.err());
        try {
            return JSON.readTree(r.out());
        } catch (IOException e) {
            throw new AssertionError("Output was not valid JSON:\n" + r.out(), e);
        }
    }

    // ---- bugs ----

    @Test
    void bugLifecycle() {
        Result add = run("bug", "add", "Login fails after token expiry", "-p", "app",
                "--severity", "high", "--steps", "1. log in\n2. wait 1h");
        assertEquals(0, add.code(), add.err());
        assertTrue(add.out().contains("Recorded bug 1 in project 'app'"));

        String list = run("bug", "list").out();
        assertTrue(list.contains("Login fails after token expiry"));
        assertTrue(list.contains("HIGH"));
        assertTrue(list.contains("1 bug(s)"));

        String show = run("bug", "show", "1").out();
        assertTrue(show.contains("Reproduction steps:"));
        assertTrue(show.contains("2. wait 1h"));

        assertEquals(0, run("bug", "update", "1", "--status", "resolved").code());
        assertEquals("RESOLVED", json("bug", "show", "1", "--json").get("bug").get("status").asText());
        assertEquals(0, json("bug", "list", "--status", "open", "--json").get("count").asInt());
        assertEquals(1, json("bug", "list", "--status", "resolved", "--json").get("count").asInt());
    }

    @Test
    void bugAddValidation() {
        Result noProject = run("bug", "add", "orphan");
        assertEquals(1, noProject.code());
        assertTrue(noProject.err().startsWith("Error: No project specified"));

        assertEquals(2, run("bug", "add", "x", "-p", "app", "--severity", "huge").code());
        assertEquals(0, json("bug", "list", "--json").get("count").asInt());
    }

    @Test
    void bugUpdateNeedsAChangeAndAnExistingBug() {
        run("bug", "add", "b", "-p", "app");
        Result nothing = run("bug", "update", "1");
        assertEquals(1, nothing.code());
        assertTrue(nothing.err().contains("Nothing to update"));

        Result missing = run("bug", "update", "9", "--title", "x");
        assertEquals(1, missing.code());
        assertTrue(missing.err().contains("Bug 9 not found"));
    }

    // ---- task delete ----

    @Test
    void taskDeleteWithYesSkipsPrompt() {
        run("task", "add", "Temp", "-p", "app");
        Result r = run("task", "delete", "1", "--yes");

        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Deleted task 1: Temp"));
        assertTrue(prompts.isEmpty());
        assertEquals(1, run("task", "show", "1").code());
    }

    @Test
    void taskDeleteAsksAndAcceptsYes() {
        run("task", "add", "Temp", "-p", "app");
        nextAnswer = Optional.of("y");

        Result r = run("task", "delete", "1");
        assertEquals(0, r.code(), r.err());
        assertEquals(1, prompts.size());
        assertTrue(prompts.get(0).contains("Delete task 1 'Temp'"));
        assertEquals(0, json("task", "list", "--json").get("count").asInt());
    }

    @Test
    void taskDeleteDeclinedChangesNothing() {
        run("task", "add", "Temp", "-p", "app");
        nextAnswer = Optional.of("n");

        Result r = run("task", "delete", "1");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("Cancelled"));
        assertEquals(1, json("task", "list", "--json").get("count").asInt());
    }

    @Test
    void taskDeleteWithoutTerminalFailsSafely() {
        run("task", "add", "Temp", "-p", "app");
        nextAnswer = Optional.empty();

        Result r = run("task", "delete", "1");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("--yes"));
        assertEquals(1, json("task", "list", "--json").get("count").asInt());
    }

    @Test
    void taskDeleteOfMissingTaskFailsBeforePrompting() {
        Result r = run("task", "delete", "5");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("Task 5 not found"));
        assertTrue(prompts.isEmpty());
    }

    // ---- project management ----

    @Test
    void projectUpdateRenamesAndDescribes() {
        Result r = run("project", "update", "app", "--name", "shop", "-d", "Online store");
        assertEquals(0, r.code(), r.err());

        JsonNode p = json("project", "show", "shop", "--json").get("project");
        assertEquals("shop", p.get("name").asText());
        assertEquals("Online store", p.get("description").asText());

        Result nothing = run("project", "update", "shop");
        assertEquals(1, nothing.code());
        assertTrue(nothing.err().contains("Nothing to update"));
    }

    @Test
    void archivedProjectRejectsNewWorkUntilUnarchived() {
        assertEquals(0, run("project", "archive", "app").code());
        assertEquals("ARCHIVED", json("project", "show", "app", "--json").get("project").get("status").asText());

        Result blocked = run("task", "add", "x", "-p", "app");
        assertEquals(1, blocked.code());
        assertTrue(blocked.err().contains("archived"));
        assertEquals(1, run("bug", "add", "x", "-p", "app").code());
        assertEquals(1, run("project", "archive", "app").code()); // already archived

        assertEquals(0, run("project", "unarchive", "app").code());
        assertEquals(0, run("task", "add", "x", "-p", "app").code());
    }

    @Test
    void projectRemoveWithYesDeletesRegistryDataButNotFiles() throws IOException {
        Path file = Files.writeString(appDir.resolve("Main.java"), "class Main {}");
        run("task", "add", "t", "-p", "app");
        run("bug", "add", "b", "-p", "app");

        Result r = run("project", "remove", "app", "--yes");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Removed project 'app'"));
        assertTrue(r.out().contains("were not touched"));

        assertEquals(0, json("project", "list", "--json").get("count").asInt());
        assertEquals(0, json("task", "list", "--json").get("count").asInt());
        assertEquals(0, json("bug", "list", "--json").get("count").asInt());
        assertTrue(Files.exists(file));
        assertEquals("class Main {}", Files.readString(file));
    }

    @Test
    void projectRemoveWithoutTerminalFailsSafely() {
        Result r = run("project", "remove", "app");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("--yes"));
        assertEquals(1, json("project", "list", "--json").get("count").asInt());
    }

    @Test
    void projectRemoveAsksFirstAndExplainsConsequences() {
        nextAnswer = Optional.of("yes");

        Result r = run("project", "remove", "app");
        assertEquals(0, r.code(), r.err());
        assertEquals(1, prompts.size());
        assertTrue(prompts.get(0).contains("tasks and bugs will be deleted"));
        assertTrue(prompts.get(0).contains("NOT touched"));
        assertFalse(prompts.get(0).isBlank());
        assertEquals(0, json("project", "list", "--json").get("count").asInt());
    }
}
