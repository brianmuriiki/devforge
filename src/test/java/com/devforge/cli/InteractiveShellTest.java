package com.devforge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.AppContext;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InteractiveShellTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);

    @TempDir Path tmp;

    @Test
    void executesMultipleCommandsAndExitsOnExit() throws Exception {
        Path projectDir = Files.createDirectories(tmp.resolve("demo project"));
        AppContext context = new AppContext(
                AppPaths.resolve(
                        OperatingSystem.LINUX,
                        Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()),
                        tmp),
                CLOCK);
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        String commands = "init \"" + projectDir + "\"\nproject list\nexit\nstatus\n";

        int exitCode = new InteractiveShell(
                        context,
                        new BufferedReader(new StringReader(commands)),
                        new PrintWriter(output, true),
                        new PrintWriter(error, true))
                .run();

        assertEquals(ExitCodes.SUCCESS, exitCode);
        assertTrue(output.toString().startsWith("\u001B[31mdevforge\u001B[0m> "));
        assertTrue(output.toString().contains("Registered project 'demo project'"));
        assertTrue(output.toString().contains("demo project"));
        assertTrue(output.toString().contains("\u001B[31mdevforge\u001B[0m> "));
        assertFalse(output.toString().contains("Goodbye."));
        assertFalse(output.toString().contains("interactive mode"));
        assertFalse(output.toString().contains("Schema version:"));
        assertEquals("", error.toString());
    }

    @Test
    void helpShowsUsageAndEndOfInputExitsCleanly() {
        AppContext context = new AppContext(
                AppPaths.resolve(
                        OperatingSystem.LINUX,
                        Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()),
                        tmp),
                CLOCK);
        StringWriter output = new StringWriter();

        int exitCode = new InteractiveShell(
                        context,
                        new BufferedReader(new StringReader("help\n")),
                        new PrintWriter(output, true),
                        new PrintWriter(new StringWriter(), true))
                .run();

        assertEquals(ExitCodes.SUCCESS, exitCode);
        assertTrue(output.toString().contains("Usage: devforge"));
        assertFalse(output.toString().contains("Goodbye."));
    }

    @Test
    void malformedCommandDoesNotEndInteractiveSession() {
        AppContext context = new AppContext(
                AppPaths.resolve(
                        OperatingSystem.LINUX,
                        Map.of("DEVFORGE_HOME", tmp.resolve("home").toString()),
                        tmp),
                CLOCK);
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();

        int exitCode = new InteractiveShell(
                        context,
                        new BufferedReader(new StringReader("task add \"unfinished\nhelp\nexit\n")),
                        new PrintWriter(output, true),
                        new PrintWriter(error, true))
                .run();

        assertEquals(ExitCodes.SUCCESS, exitCode);
        assertTrue(error.toString().contains("Unclosed quote"));
        assertTrue(output.toString().contains("Usage: devforge"));
        assertFalse(output.toString().contains("Goodbye."));
    }
}
