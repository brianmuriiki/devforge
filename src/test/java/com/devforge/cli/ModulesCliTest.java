package com.devforge.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.AppContext;
import com.devforge.core.Prompter;
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

/** End-to-end tests of the FindX, DepGuard, DevVault and combined-report commands (no network is used). */
class ModulesCliTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Result(int code, String out, String err) {}

    @TempDir Path tmp;

    private final List<String> prompts = new ArrayList<>();
    private Optional<String> nextAnswer = Optional.empty();
    private static final String BACKUP_PASSPHRASE = "correct horse battery staple";
    private AppContext context;
    private Path home;
    private Path app;

    private void write(Path base, String rel, String content) throws IOException {
        Path p = base.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("devforge").toString()), tmp);
        home = Files.createDirectories(tmp.resolve("home"));
        context = new AppContext(paths, CLOCK, new Prompter() {
            @Override
            public Optional<String> ask(String prompt) {
                prompts.add(prompt);
                return nextAnswer;
            }

            @Override
            public Optional<char[]> askSecret(String prompt) {
                return Optional.of(BACKUP_PASSPHRASE.toCharArray());
            }
        }, home);
        app = Files.createDirectories(tmp.resolve("work/app"));
        write(app, "src/Auth.java", "class AuthService { void authentication() {} }");
        write(app, "README.md", "Database connection pool");
        assertEquals(0, run("init", app.toString(), "--name", "app").code());
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

    private JsonNode json(int expectedCode, String... args) {
        Result r = run(args);
        assertEquals(expectedCode, r.code(), r.err());
        try {
            return JSON.readTree(r.out());
        } catch (IOException e) {
            throw new AssertionError("Output was not valid JSON:\n" + r.out(), e);
        }
    }

    // ---- FindX ----

    @Test
    void searchBeforeIndexingExplainsWhatToDo() {
        Result r = run("search", "authentication");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("Nothing is indexed yet"));
    }

    @Test
    void indexThenSearchFindsFilesAndNoMatchExitsOne() {
        Result idx = run("search", "index", app.toString());
        assertEquals(0, idx.code(), idx.err());
        assertTrue(idx.out().contains("Indexed project 'app': 2 added"));

        Result hit = run("search", "authentication");
        assertEquals(0, hit.code(), hit.err());
        assertTrue(hit.out().contains("app:src/Auth.java"));
        assertTrue(hit.out().contains("1 result(s)"));

        Result miss = run("search", "kangaroo");
        assertEquals(1, miss.code());
        assertTrue(miss.err().contains("No matching files found."));
        assertEquals("", miss.out());
    }

    @Test
    void searchJsonAndFilters() {
        run("search", "index", app.toString());
        JsonNode doc = json(0, "search", "pool", "--json");
        assertEquals(1, doc.get("schemaVersion").asInt());
        assertEquals(1, doc.get("count").asInt());
        assertEquals("README.md", doc.get("results").get(0).get("path").asText());
        assertEquals("content", doc.get("results").get(0).get("matchType").asText());

        assertEquals(1, json(0, "search", "pool", "-e", "md", "-p", "app", "--json").get("results").get(0).get("projectId").asInt());
        assertEquals(0, json(1, "search", "pool", "-e", "java", "--json").get("count").asInt());
    }

    @Test
    void indexStatusRebuildAndDuplicates() throws IOException {
        write(app, "copy/one.txt", "identical data");
        write(app, "copy/two.txt", "identical data");
        run("search", "index", app.toString());

        JsonNode status = json(0, "search", "status", "--json");
        assertEquals(4, status.get("projects").get(0).get("files").asInt());

        Result dups = run("search", "duplicates", "-p", "app");
        assertEquals(0, dups.code(), dups.err());
        assertTrue(dups.out().contains("app:copy/one.txt"));
        assertTrue(dups.out().contains("No files were changed."));
        assertTrue(Files.exists(app.resolve("copy/one.txt")));
        assertEquals(1, json(0, "search", "duplicates", "--json").get("groupCount").asInt());

        Result rebuilt = run("search", "rebuild", "-p", "app");
        assertEquals(0, rebuilt.code(), rebuilt.err());
        assertTrue(rebuilt.out().contains("Rebuilt index of project 'app'"));
    }

    @Test
    void indexOutsideARegisteredProjectFailsWithAHint() throws IOException {
        Path stray = Files.createDirectories(tmp.resolve("stray"));
        Result r = run("search", "index", stray.toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("devforge init"));
    }

    @Test
    void watchStopsAfterTheRequestedDuration() {
        Result r = run("search", "watch", app.toString(), "--seconds", "1");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Initial index of 'app'"));
        assertTrue(r.out().contains("Watching 'app'"));
    }

    // ---- DepGuard (offline only) ----

    @Test
    void securityScanWithoutManifestsSucceeds() {
        Result r = run("security", "scan", app.toString(), "--offline");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("No supported dependency manifests"));
    }

    @Test
    void offlineScanWithoutCachedDataIsNeverReportedAsClean() throws IOException {
        write(app, "package.json", "{ \"dependencies\": { \"left-pad\": \"1.3.0\" } }");
        Result r = run("security", "scan", app.toString(), "--offline");
        assertEquals(4, r.code(), r.err());
        assertTrue(r.out().contains("UNAVAILABLE"));
        assertTrue(r.out().contains("NOT a clean result"));
        assertFalse(r.out().contains("No known vulnerabilities"));

        JsonNode doc = json(4, "security", "scan", app.toString(), "--offline", "--json");
        assertEquals("UNAVAILABLE", doc.get("status").asText());
        assertEquals(1, doc.get("unavailable").asInt());
        assertEquals(0, doc.get("vulnerabilities").asInt());
    }

    @Test
    void auditOutdatedLicensesAndReportReadStoredResults() throws IOException {
        assertEquals(1, run("security", "audit").code()); // no scan yet
        assertTrue(run("security", "report", "-p", "app").err().contains("No scan results yet"));

        write(app, "package.json", "{ \"dependencies\": { \"left-pad\": \"^1.0.0\" } }");
        run("security", "scan", app.toString(), "--offline");

        Result audit = run("security", "audit", "-p", "app");
        assertEquals(4, audit.code(), audit.err());
        assertTrue(audit.out().contains("INCOMPLETE"));

        Result outdated = run("security", "outdated", "-p", "app");
        assertEquals(0, outdated.code(), outdated.err());
        assertTrue(outdated.out().contains("No outdated dependencies"));

        Result licenses = run("security", "licenses", "-p", "app");
        assertEquals(0, licenses.code(), licenses.err());
        assertTrue(licenses.out().contains("left-pad"));
        assertTrue(licenses.out().contains("not legal advice"));

        JsonNode report = json(4, "security", "report", "-p", "app", "--json");
        assertEquals("INCOMPLETE", report.get("projects").get(0).get("status").asText());
        assertEquals(1, report.get("projects").get(0).get("unresolved").asInt());
    }

    // ---- DevVault ----

    private void createDefaultProfile() throws IOException {
        write(home, ".gitconfig", "[user]\nname = Dev");
        write(home, ".bashrc", "export X=1");
        write(home, "notes/todo.txt", "buy milk");
        write(home, "notes/.env", "TOKEN=abc");
        Result r = run("env", "profile", "create", "dev", "--include", "~/.gitconfig", "--include", "~/.bashrc",
                "--include", "~/notes", "--exclude", "*.bak");
        assertEquals(0, r.code(), r.err());
    }

    @Test
    void profileCommands() throws IOException {
        assertTrue(run("env", "profile", "list").out().contains("No profiles"));
        createDefaultProfile();
        assertTrue(run("env", "profile", "list").out().contains("dev"));
        String show = run("env", "profile", "show", "dev").out();
        assertTrue(show.contains("~/.gitconfig"));
        assertTrue(show.contains("*.bak"));
        assertEquals("dev", json(0, "env", "profile", "show", "dev", "--json").get("profile").get("name").asText());

        Result dup = run("env", "profile", "create", "dev");
        assertEquals(1, dup.code());
        assertTrue(dup.err().contains("already exists"));
        assertEquals(1, run("env", "profile", "create", "x", "--include", "/etc").code());

        assertEquals(1, run("env", "profile", "delete", "dev").code()); // needs confirmation
        assertEquals(0, run("env", "profile", "delete", "dev", "--yes").code());
        assertTrue(run("env", "profile", "list").out().contains("No profiles"));
    }

    @Test
    void backupVerifyRestoreFullCycle() throws IOException {
        createDefaultProfile();

        Result dry = run("env", "backup", "--dry-run");
        assertEquals(0, dry.code(), dry.err());
        assertTrue(dry.out().contains("Nothing was written"));
        assertTrue(dry.out().contains("1 sensitive file(s) excluded"));
        assertEquals(0, json(0, "env", "backups", "--json").get("count").asInt());

        Result backup = run("env", "backup");
        assertEquals(0, backup.code(), backup.err());
        assertTrue(backup.out().contains("Backup 1 created and verified"));
        assertTrue(backup.out().contains("3 file(s)"));
        assertTrue(run("env", "backups").out().contains("yes"));
        assertTrue(json(0, "env", "backups", "--json").get("backups").get(0).get("encrypted").asBoolean());

        Result verify = run("env", "verify");
        assertEquals(0, verify.code(), verify.err());
        assertTrue(verify.out().contains("OK:"));

        Path target = Files.createDirectories(tmp.resolve("new-machine"));
        Result dryRestore = run("env", "restore", "--target-home", target.toString(), "--dry-run");
        assertEquals(0, dryRestore.code(), dryRestore.err());
        assertTrue(dryRestore.out().contains("3 to create"));
        try (var files = Files.list(target)) {
            assertEquals(0, files.count());
        }

        Result restore = run("env", "restore", "--target-home", target.toString());
        assertEquals(0, restore.code(), restore.err());
        assertTrue(restore.out().contains("Restored 3 file(s)"));
        assertEquals("export X=1", Files.readString(target.resolve(".bashrc")));
        assertFalse(Files.exists(target.resolve("notes/.env")));
    }

    @Test
    void restoreAsksBeforeOverwritingAndKeepsARecoveryCopy() throws IOException {
        createDefaultProfile();
        run("env", "backup");
        Path target = Files.createDirectories(tmp.resolve("new-machine"));
        write(target, ".bashrc", "export X=OLD");

        Result noTerminal = run("env", "restore", "--target-home", target.toString());
        assertEquals(1, noTerminal.code());
        assertTrue(noTerminal.err().contains("--yes"));
        assertEquals("export X=OLD", Files.readString(target.resolve(".bashrc")));

        Result skip = run("env", "restore", "--target-home", target.toString(), "--skip-conflicts");
        assertEquals(4, skip.code(), skip.err());
        assertEquals("export X=OLD", Files.readString(target.resolve(".bashrc")));
        assertTrue(Files.exists(target.resolve(".gitconfig")));

        nextAnswer = Optional.of("y");
        Result overwrite = run("env", "restore", "--target-home", target.toString());
        assertEquals(0, overwrite.code(), overwrite.err());
        assertEquals("export X=1", Files.readString(target.resolve(".bashrc")));
        assertTrue(overwrite.out().contains("saved to"));
        assertEquals(2, prompts.size()); // the first (unanswered) attempt and this one
        assertTrue(prompts.get(1).contains(".devforge-recovery"));
    }

    @Test
    void includingSecretsNeedsExplicitConfirmation() throws IOException {
        createDefaultProfile();

        Result blocked = run("env", "backup", "--include-sensitive");
        assertEquals(1, blocked.code());
        assertTrue(blocked.err().contains("--yes"));
        assertEquals(0, json(0, "env", "backups", "--json").get("count").asInt());

        nextAnswer = Optional.of("y");
        Result ok = run("env", "backup", "--include-sensitive");
        assertEquals(0, ok.code(), ok.err());
        assertTrue(prompts.get(0).contains("file(s) that look like secrets"));
        assertTrue(json(0, "env", "backups", "--json").get("backups").get(0).get("includesSensitive").asBoolean());
    }

    @Test
    void missingPathsAreWarningsWithExitFour() throws IOException {
        write(home, ".gitconfig", "x");
        run("env", "profile", "create", "partial", "--include", "~/.gitconfig", "--include", "~/.does-not-exist");
        Result r = run("env", "backup");
        assertEquals(4, r.code(), r.err());
        assertTrue(r.out().contains("path not found"));
        assertTrue(r.out().contains("created and verified"));
    }

    @Test
    void tamperedBackupFailsVerifyAndRestore() throws IOException {
        createDefaultProfile();
        run("env", "backup");
        JsonNode backup = json(0, "env", "backups", "--json").get("backups").get(0);
        Path archive = Path.of(backup.get("archivePath").asText());
        byte[] data = Files.readAllBytes(archive);
        data[data.length / 2] ^= 0x2A;
        Files.write(archive, data);

        Result verify = run("env", "verify");
        assertEquals(1, verify.code());
        assertTrue(verify.err().contains("Backup checksum verification failed"));

        Path target = Files.createDirectories(tmp.resolve("new-machine"));
        Result restore = run("env", "restore", "--target-home", target.toString(), "--yes");
        assertEquals(1, restore.code());
        try (var files = Files.list(target)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void restoreFromACopiedArchiveUsesTheSidecarChecksum() throws IOException {
        createDefaultProfile();
        run("env", "backup");
        Path archive = Path.of(json(0, "env", "backups", "--json").get("backups").get(0).get("archivePath").asText());
        Path usb = Files.createDirectories(tmp.resolve("usb"));
        Path copy = Files.copy(archive, usb.resolve(archive.getFileName()));
        Path target = Files.createDirectories(tmp.resolve("other-pc"));

        Result noChecksum = run("env", "restore", "--archive", copy.toString(), "--target-home", target.toString(), "--yes");
        assertEquals(1, noChecksum.code());
        assertTrue(noChecksum.err().contains("No checksum available"));

        Files.copy(archive.resolveSibling(archive.getFileName() + ".sha256"), usb.resolve(archive.getFileName() + ".sha256"));
        Result ok = run("env", "restore", "--archive", copy.toString(), "--target-home", target.toString(), "--yes");
        assertEquals(0, ok.code(), ok.err());
        assertTrue(Files.exists(target.resolve(".gitconfig")));
    }

    // ---- combined report / analyze ----

    @Test
    void analyzeRegistersIndexesScansAndReports() throws IOException {
        Path fresh = Files.createDirectories(tmp.resolve("work/fresh"));
        write(fresh, "src/Main.java", "class Main {}");

        Result r = run("analyze", fresh.toString(), "--offline");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Registered project 'fresh'"));
        assertTrue(r.out().contains("Indexed: 1 added"));
        assertTrue(r.out().contains("== fresh"));
        assertTrue(r.out().contains("Security:   not scanned"));

        Result again = run("analyze", fresh.toString(), "--offline");
        assertEquals(0, again.code(), again.err());
        assertFalse(again.out().contains("Registered project"));
        assertTrue(again.out().contains("Indexed: 0 added"));
    }

    @Test
    void analyzeSurfacesIncompleteSecurityResultsInTheExitCode() throws IOException {
        write(app, "package.json", "{ \"dependencies\": { \"left-pad\": \"1.3.0\" } }");
        Result r = run("analyze", app.toString(), "--offline");
        assertEquals(4, r.code(), r.err());
        assertTrue(r.out().contains("NOT a clean result"));
    }

    @Test
    void reportCombinesAllModulesAsJson() throws IOException {
        run("task", "add", "Build authentication", "-p", "app");
        run("bug", "add", "Login fails", "-p", "app");
        run("search", "index", app.toString());

        JsonNode doc = json(0, "report", "-p", "app", "--json");
        JsonNode p = doc.get("projects").get(0);
        assertEquals("app", p.get("project").get("name").asText());
        assertEquals(1, p.get("tasks").get("total").asInt());
        assertEquals(1, p.get("bugs").get("OPEN").asInt());
        assertEquals(2, p.get("search").get("files").asInt());
        assertEquals("NOT_SCANNED", p.get("security").get("status").asText());
        assertEquals(0, p.get("environment").get("backupCount").asInt());

        String text = run("report", "-p", "app").out();
        assertTrue(text.contains("Tasks:      1 total"));
        assertTrue(text.contains("Backups:    0 registered"));
    }
}
