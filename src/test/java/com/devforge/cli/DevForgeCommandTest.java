package com.devforge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class DevForgeCommandTest {

    private record Result(int exitCode, String out, String err) {}

    private Result execute(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = new CommandLine(new DevForgeCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(err, true));
        int code = cmd.execute(args);
        return new Result(code, out.toString(), err.toString());
    }

    @Test
    void helpExitsZeroAndShowsUsage() {
        Result r = execute("--help");
        assertEquals(0, r.exitCode());
        assertTrue(r.out().contains("devforge"));
    }

    @Test
    void versionExitsZeroAndShowsProductName() {
        Result r = execute("--version");
        assertEquals(0, r.exitCode());
        assertTrue(r.out().startsWith("DevForge "));
    }

    @Test
    void noArgumentsShowsHelpAndSucceeds() {
        Result r = execute();
        assertEquals(0, r.exitCode());
        assertTrue(r.out().contains("Usage"));
    }

    @Test
    void unknownOptionReturnsUsageErrorCodeTwo() {
        Result r = execute("--nonsense");
        assertEquals(2, r.exitCode());
        assertTrue(r.err().contains("Unknown option"));
    }
}
