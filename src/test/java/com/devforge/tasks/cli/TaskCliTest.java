package com.devforge.tasks.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.cli.CommandFactory;
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

class TaskCliTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Result(int code, String out, String err) {}

    @TempDir Path tmp;

    private AppContext context;

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()), tmp);
        context = new AppContext(paths, CLOCK);
        Result init = run("init", Files.createDirectories(tmp.resolve("work/app")).toString(), "--name", "app");
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

    @Test
    void taskWithoutSubcommandShowsHelp() {
        Result r = run("task");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("Usage"));
        assertTrue(r.out().contains("complete"));
    }

    @Test
    void addCreatesTaskInNamedProject() {
        Result r = run("task", "add", "Build authentication", "--project", "app");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Added task 1 to project 'app': Build authentication"));
        assertEquals("", r.err());
    }

    @Test
    void addAcceptsLowercaseEnumsAndDueDate() {
        assertEquals(0, run("task", "add", "Ship", "-p", "1", "--priority", "high", "--due", "2030-01-01").code());

        JsonNode t = json("task", "show", "1", "--json").get("task");
        assertEquals("HIGH", t.get("priority").asText());
        assertEquals("2030-01-01", t.get("dueDate").asText());
        assertEquals("TODO", t.get("status").asText());
        assertFalse(t.get("overdue").asBoolean());
    }

    @Test
    void addWithoutProjectOutsideRegisteredDirectoryFails() {
        Result r = run("task", "add", "orphan");
        assertEquals(1, r.code());
        assertTrue(r.err().startsWith("Error: No project specified"));
        assertEquals("", r.out());
    }

    @Test
    void addRejectsInvalidDateAndPriority() {
        Result badDate = run("task", "add", "x", "-p", "app", "--due", "soon");
        assertEquals(1, badDate.code());
        assertTrue(badDate.err().contains("Invalid date"));

        Result badPriority = run("task", "add", "x", "-p", "app", "--priority", "urgent");
        assertEquals(2, badPriority.code());
    }

    @Test
    void listWhenEmpty() {
        Result r = run("task", "list");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("No tasks found."));
    }

    @Test
    void listFiltersByStatusPriorityAndProject() {
        run("task", "add", "A", "-p", "app", "--priority", "low");
        run("task", "add", "B", "-p", "app", "--priority", "critical");
        run("task", "add", "C", "-p", "app");
        run("task", "complete", "2");

        assertEquals(3, json("task", "list", "--json").get("count").asInt());
        assertEquals(3, json("task", "list", "--project", "app", "--json").get("count").asInt());

        JsonNode done = json("task", "list", "--status", "done", "--json");
        assertEquals(1, done.get("count").asInt());
        assertEquals("B", done.get("tasks").get(0).get("title").asText());

        assertEquals(1, json("task", "list", "--priority", "critical", "--json").get("count").asInt());
        assertEquals(2, json("task", "list", "--status", "todo", "--json").get("count").asInt());
    }

    @Test
    void listSortsByDueDateWithUndatedLast() {
        run("task", "add", "later", "-p", "app", "--due", "2031-01-01");
        run("task", "add", "undated", "-p", "app");
        run("task", "add", "sooner", "-p", "app", "--due", "2030-01-01");

        String out = run("task", "list", "--sort", "due").out();
        assertTrue(out.indexOf("sooner") < out.indexOf("later"));
        assertTrue(out.indexOf("later") < out.indexOf("undated"));
        assertTrue(out.contains("3 task(s)"));
    }

    @Test
    void listMarksOverdueTasks() {
        run("task", "add", "stale", "-p", "app", "--due", "2020-01-01");
        assertTrue(run("task", "list").out().contains("2020-01-01 (overdue)"));
        assertTrue(json("task", "show", "1", "--json").get("task").get("overdue").asBoolean());
    }

    @Test
    void listStripsTerminalControlCharacters() {
        run("task", "add", "evil\u001b[31mred", "-p", "app");
        String out = run("task", "list").out();
        assertFalse(out.contains("\u001b"));
        assertTrue(out.contains("evil"));
    }

    @Test
    void showPrintsDetailsAndJson() {
        run("task", "add", "Build authentication", "-p", "app", "-d", "Use JWT");

        String text = run("task", "show", "1").out();
        assertTrue(text.contains("Task 1: Build authentication"));
        assertTrue(text.contains("app (id 1)"));
        assertTrue(text.contains("Use JWT"));

        JsonNode doc = json("task", "show", "1", "--json");
        assertEquals(1, doc.get("schemaVersion").asInt());
        assertEquals("Use JWT", doc.get("task").get("description").asText());
    }

    @Test
    void showUnknownTaskFails() {
        Result r = run("task", "show", "99");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("Task 99 not found"));
        assertFalse(r.err().contains("\tat "));
    }

    @Test
    void updateChangesPriorityAndDueDate() {
        run("task", "add", "Original", "-p", "app");

        Result r = run("task", "update", "1", "--priority", "high", "--due", "2030-05-01", "--title", "Renamed");
        assertEquals(0, r.code(), r.err());
        JsonNode t = json("task", "show", "1", "--json").get("task");
        assertEquals("Renamed", t.get("title").asText());
        assertEquals("HIGH", t.get("priority").asText());
        assertEquals("2030-05-01", t.get("dueDate").asText());

        assertEquals(0, run("task", "update", "1", "--clear-due").code());
        assertTrue(json("task", "show", "1", "--json").get("task").get("dueDate").isNull());
    }

    @Test
    void updateRejectsConflictingDueOptionsAndEmptyUpdate() {
        run("task", "add", "x", "-p", "app");

        assertEquals(2, run("task", "update", "1", "--due", "2030-01-01", "--clear-due").code());

        Result empty = run("task", "update", "1");
        assertEquals(1, empty.code());
        assertTrue(empty.err().contains("Nothing to update"));
    }

    @Test
    void completeAndReopenCycle() {
        run("task", "add", "Ship", "-p", "app");

        assertTrue(run("task", "complete", "1").out().contains("Completed task 1: Ship"));
        JsonNode done = json("task", "show", "1", "--json").get("task");
        assertEquals("DONE", done.get("status").asText());
        assertEquals("2026-11-13T00:00:00Z", done.get("completedAt").asText());

        Result again = run("task", "complete", "1");
        assertEquals(1, again.code());
        assertTrue(again.err().contains("already completed"));

        assertTrue(run("task", "reopen", "1").out().contains("Reopened task 1: Ship"));
        assertTrue(json("task", "show", "1", "--json").get("task").get("completedAt").isNull());
        assertEquals(1, run("task", "reopen", "1").code());
    }

    @Test
    void projectShowWorksByIdAndName() {
        Result byId = run("project", "show", "1");
        assertEquals(0, byId.code(), byId.err());
        assertTrue(byId.out().contains("Project 1: app"));
        assertTrue(byId.out().contains("Status:      ACTIVE"));

        JsonNode p = json("project", "show", "APP", "--json").get("project");
        assertEquals("app", p.get("name").asText());
        assertEquals(1, p.get("id").asInt());

        Result missing = run("project", "show", "nope");
        assertEquals(1, missing.code());
        assertTrue(missing.err().contains("No project found"));
    }

    @Test
    void statsReportsProgressAndBugCounts() {
        run("task", "add", "A", "-p", "app", "--priority", "high");
        run("task", "add", "B", "-p", "app");
        run("task", "complete", "2");
        run("bug", "add", "x", "-p", "app");

        JsonNode d = json("task", "stats", "--json");
        assertEquals(1, d.get("schemaVersion").asInt());
        assertEquals(2, d.get("tasks").get("total").asInt());
        assertEquals(1, d.get("tasks").get("done").asInt());
        assertEquals("50.0", d.get("tasks").get("completionPercent").asText());
        assertEquals(1, d.get("tasks").get("byPriority").get("HIGH").asInt());
        assertEquals(1, d.get("bugs").get("OPEN").asInt());

        String text = run("task", "stats", "-p", "app").out();
        assertTrue(text.contains("Completed:    1 (50.0%)"));
        assertTrue(text.contains("Bugs:           OPEN 1"));
    }
}
