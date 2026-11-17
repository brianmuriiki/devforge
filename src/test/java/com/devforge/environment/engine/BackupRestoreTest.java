package com.devforge.environment.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.devforge.core.hash.Sha256;
import com.devforge.environment.VaultException;
import com.devforge.environment.engine.BackupEngine.BackupResult;
import com.devforge.environment.engine.RestoreEngine.Action;
import com.devforge.environment.engine.RestoreEngine.Plan;
import com.devforge.environment.engine.RestoreEngine.Result;
import com.devforge.environment.model.BackupManifest;
import com.devforge.environment.model.ManifestEntry;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupRestoreTest {

    private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");

    @TempDir Path tmp;

    private Path home;
    private Path dest;

    private void write(Path base, String rel, String content) throws IOException {
        Path p = base.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @BeforeEach
    void setUp() throws IOException {
        home = Files.createDirectories(tmp.resolve("home"));
        dest = tmp.resolve("backups");
        write(home, ".gitconfig", "[user]\nname = Dev");
        write(home, ".config/app/settings.json", "{\"a\":1}");
        write(home, ".bashrc", "export X=1");
    }

    private BackupResult backup() {
        var sel = FileSelector.select(home, List.of("~/.gitconfig", "~/.config/app", "~/.bashrc"), List.of(), false);
        return BackupEngine.create(sel.files(), "dev", "0.1.0", NOW, dest, "dev-20261113-000000");
    }

    private BackupResult encryptedBackup() {
        var sel = FileSelector.select(home, List.of("~/.gitconfig", "~/.config/app", "~/.bashrc"), List.of(), false);
        return BackupEngine.create(sel.files(), "dev", "0.1.0", NOW, dest, "encrypted", "correct horse battery staple".toCharArray());
    }

    private static void writeZip(Path file, Map<String, byte[]> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void backupCreatesArchiveChecksumAndManifestFiles() throws IOException {
        BackupResult r = backup();
        assertTrue(Files.isRegularFile(r.archive()));
        assertTrue(Files.isRegularFile(r.manifestFile()));
        assertEquals(r.sha256(), Files.readString(r.checksumFile()).split("\\s+")[0]);
        assertEquals(Sha256.file(r.archive()), r.sha256());
        assertEquals(3, r.manifest().entries().size());
        assertTrue(r.manifest().entries().stream().anyMatch(e -> e.path().equals(".config/app/settings.json")));
        assertEquals("dev", r.manifest().profile());
        assertFalse(Files.exists(dest.resolve("dev-20261113-000000.zip.part")));
    }

    @Test
    void freshBackupVerifiesClean() {
        BackupResult r = backup();
        ArchiveVerifier.Report v = ArchiveVerifier.verify(r.archive(), r.sha256());
        assertTrue(v.ok(), String.valueOf(v.problems()));
        assertEquals(3, v.filesChecked());
        assertEquals(3, v.manifest().orElseThrow().entries().size());
    }

    @Test
    void encryptedBackupHidesMetadataAndRoundTrips() throws IOException {
        BackupResult r = encryptedBackup();
        assertTrue(r.archive().getFileName().toString().endsWith(".zip.enc"));
        assertFalse(Files.exists(dest.resolve("encrypted.manifest.json")));
        assertTrue(ArchiveVerifier.verify(r.archive(), r.sha256(), "correct horse battery staple".toCharArray()).ok());
        assertFalse(ArchiveVerifier.verify(r.archive(), r.sha256(), "wrong passphrase".toCharArray()).ok());

        Path newHome = Files.createDirectories(tmp.resolve("encrypted-home"));
        Plan plan = RestoreEngine.plan(r.archive(), newHome, r.manifest());
        Result restored = RestoreEngine.execute(plan, false, NOW, "correct horse battery staple".toCharArray());
        assertEquals(3, restored.restored());
        assertEquals("export X=1", Files.readString(newHome.resolve(".bashrc")));
    }

    @Test
    void existingArchiveIsNeverOverwritten() {
        backup();
        VaultException e = assertThrows(VaultException.class, this::backup);
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void backupDirectoryInsideTheSelectionIsNotArchived() throws IOException {
        Path inside = home.resolve(".config/app/backups");
        var sel = FileSelector.select(home, List.of("~/.config/app", "~/.gitconfig"), List.of(), false);
        BackupEngine.create(sel.files(), "dev", "0.1.0", NOW, inside, "first");
        var again = FileSelector.select(home, List.of("~/.config/app", "~/.gitconfig"), List.of(), false);
        BackupResult second = BackupEngine.create(again.files(), "dev", "0.1.0", NOW, inside, "second");
        assertTrue(second.manifest().entries().stream().noneMatch(e -> e.path().contains("backups/")));
    }

    @Test
    void wrongChecksumFailsVerification() {
        BackupResult r = backup();
        ArchiveVerifier.Report v = ArchiveVerifier.verify(r.archive(), "0".repeat(64));
        assertFalse(v.ok());
        assertTrue(v.problems().get(0).contains("SHA-256"));
    }

    @Test
    void corruptedArchiveBytesFailVerification() throws IOException {
        BackupResult r = backup();
        byte[] data = Files.readAllBytes(r.archive());
        data[data.length / 2] ^= 0x55;
        Files.write(r.archive(), data);
        assertFalse(ArchiveVerifier.verify(r.archive(), r.sha256()).ok());
        assertFalse(ArchiveVerifier.verify(r.archive(), null).ok());
    }

    @Test
    void entryContentThatDiffersFromManifestIsDetectedEvenWithoutChecksum() throws IOException {
        Path zip = tmp.resolve("tampered.zip");
        var manifest = new BackupManifest(1, "0.1.0", "p", NOW, "test",
                List.of(new ManifestEntry(".bashrc", 4, Sha256.bytes(bytes("good")), null)));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("files/.bashrc", bytes("evil"));
        entries.put("manifest.json", bytes(ManifestJson.write(manifest)));
        writeZip(zip, entries);
        ArchiveVerifier.Report v = ArchiveVerifier.verify(zip, null);
        assertFalse(v.ok());
        assertTrue(v.problems().get(0).contains("does not match"));
    }

    @Test
    void entriesMissingOrNotInTheManifestAreReported() throws IOException {
        Path zip = tmp.resolve("odd.zip");
        var manifest = new BackupManifest(1, "0.1.0", "p", NOW, "test",
                List.of(new ManifestEntry("a.txt", 1, Sha256.bytes(bytes("a")), null)));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("files/extra.txt", bytes("x"));
        entries.put("manifest.json", bytes(ManifestJson.write(manifest)));
        writeZip(zip, entries);
        ArchiveVerifier.Report v = ArchiveVerifier.verify(zip, null);
        assertFalse(v.ok());
        assertTrue(v.problems().stream().anyMatch(p -> p.contains("Missing from archive: a.txt")));
        assertTrue(v.problems().stream().anyMatch(p -> p.contains("Unexpected entry")));
    }

    @Test
    void manifestWithPathTraversalIsRejected() throws IOException {
        Path zip = tmp.resolve("evil.zip");
        String json = "{\"formatVersion\":1,\"appVersion\":\"x\",\"profile\":\"p\",\"createdAt\":\"2026-11-13T00:00:00Z\","
                + "\"os\":\"t\",\"entries\":[{\"path\":\"../../evil.txt\",\"size\":1,\"sha256\":\"" + Sha256.bytes(bytes("x"))
                + "\",\"mode\":null}]}";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("files/../../evil.txt", bytes("x"));
        entries.put("manifest.json", bytes(json));
        writeZip(zip, entries);
        ArchiveVerifier.Report v = ArchiveVerifier.verify(zip, null);
        assertFalse(v.ok());
        assertTrue(v.problems().get(0).contains("Unsafe path"));
    }

    @Test
    void archiveWithoutManifestOrNotAZipIsRejected() throws IOException {
        Path noManifest = tmp.resolve("nm.zip");
        writeZip(noManifest, Map.of("files/a", bytes("a")));
        assertFalse(ArchiveVerifier.verify(noManifest, null).ok());
        Path junk = Files.writeString(tmp.resolve("junk.zip"), "this is not a zip");
        assertFalse(ArchiveVerifier.verify(junk, null).ok());
        assertFalse(ArchiveVerifier.verify(tmp.resolve("missing.zip"), null).ok());
    }

    @Test
    void roundTripRestoresIdenticalContentOnAnotherMachine() throws IOException {
        BackupResult r = backup();
        Path newHome = Files.createDirectories(tmp.resolve("new-home"));
        Plan plan = RestoreEngine.plan(r.archive(), newHome, r.manifest());
        assertEquals(3, plan.count(Action.CREATE));

        Result result = RestoreEngine.execute(plan, false, NOW);
        assertEquals(3, result.restored());
        assertTrue(result.failures().isEmpty(), String.valueOf(result.failures()));
        for (ManifestEntry e : r.manifest().entries()) {
            assertEquals(Sha256.file(home.resolve(e.path())), Sha256.file(newHome.resolve(e.path())));
        }
        assertEquals(0, Files.list(newHome).filter(p -> p.getFileName().toString().startsWith(".devforge-restore")).count());
    }

    @Test
    void restoreKeepsPosixPermissions() throws IOException {
        try {
            Files.setPosixFilePermissions(home.resolve(".bashrc"), PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "POSIX permissions are not supported here");
        }
        BackupResult r = backup();
        Path newHome = Files.createDirectories(tmp.resolve("new-home"));
        RestoreEngine.execute(RestoreEngine.plan(r.archive(), newHome, r.manifest()), false, NOW);
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(newHome.resolve(".bashrc"))));
    }

    @Test
    void dryRunPlanNeverTouchesTheDestination() throws IOException {
        BackupResult r = backup();
        Path newHome = Files.createDirectories(tmp.resolve("new-home"));
        RestoreEngine.plan(r.archive(), newHome, r.manifest());
        try (var s = Files.list(newHome)) {
            assertEquals(0, s.count());
        }
    }

    @Test
    void identicalFilesAreSkippedAndConflictsAreProtected() throws IOException {
        BackupResult r = backup();
        Path target = Files.createDirectories(tmp.resolve("target"));
        write(target, ".gitconfig", "[user]\nname = Dev");   // identical
        write(target, ".bashrc", "export X=CHANGED");        // conflict

        Plan plan = RestoreEngine.plan(r.archive(), target, r.manifest());
        assertEquals(1, plan.count(Action.IDENTICAL));
        assertEquals(1, plan.count(Action.CONFLICT));
        assertEquals(1, plan.count(Action.CREATE));

        Result result = RestoreEngine.execute(plan, false, NOW);
        assertEquals(1, result.restored());
        assertEquals(1, result.skippedIdentical());
        assertEquals(1, result.conflictsSkipped());
        assertEquals("export X=CHANGED", Files.readString(target.resolve(".bashrc")));
        assertEquals(null, result.recoveryDir());
    }

    @Test
    void overwritingSavesTheOriginalToTheRecoveryDirectory() throws IOException {
        BackupResult r = backup();
        Path target = Files.createDirectories(tmp.resolve("target"));
        write(target, ".bashrc", "export X=CHANGED");

        Plan plan = RestoreEngine.plan(r.archive(), target, r.manifest());
        Result result = RestoreEngine.execute(plan, true, NOW);

        assertEquals("export X=1", Files.readString(target.resolve(".bashrc")));
        assertEquals(1, result.recovered());
        Path recovered = result.recoveryDir().resolve(".bashrc");
        assertEquals("export X=CHANGED", Files.readString(recovered));
        assertTrue(result.recoveryDir().startsWith(target.resolve(RestoreEngine.RECOVERY_DIR)));
    }

    @Test
    void restoreRefusesToWriteThroughSymbolicLinks() throws IOException {
        BackupResult r = backup();
        Path target = Files.createDirectories(tmp.resolve("target"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        try {
            Files.createSymbolicLink(target.resolve(".config"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        Plan plan = RestoreEngine.plan(r.archive(), target, r.manifest());
        assertEquals(1, plan.count(Action.BLOCKED));
        Result result = RestoreEngine.execute(plan, true, NOW);
        assertEquals(1, result.failures().size());
        assertFalse(Files.exists(outside.resolve("app/settings.json")));
        assertTrue(Files.exists(target.resolve(".bashrc")));
    }

    @Test
    void aSymlinkAtTheTargetFileIsNotOverwritten() throws IOException {
        BackupResult r = backup();
        Path target = Files.createDirectories(tmp.resolve("target"));
        Path victim = Files.writeString(tmp.resolve("victim.txt"), "do not touch");
        try {
            Files.createSymbolicLink(target.resolve(".bashrc"), victim);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        Plan plan = RestoreEngine.plan(r.archive(), target, r.manifest());
        assertEquals(Action.BLOCKED, plan.entries().stream()
                .filter(e -> e.entry().path().equals(".bashrc")).findFirst().orElseThrow().action());
        RestoreEngine.execute(plan, true, NOW);
        assertEquals("do not touch", Files.readString(victim));
    }

    @Test
    void aDirectoryAtTheTargetFileIsBlocked() throws IOException {
        BackupResult r = backup();
        Path target = Files.createDirectories(tmp.resolve("target"));
        Files.createDirectories(target.resolve(".bashrc"));
        Plan plan = RestoreEngine.plan(r.archive(), target, r.manifest());
        assertEquals(1, plan.count(Action.BLOCKED));
    }

    @Test
    void manifestJsonRoundTripsAndValidates() {
        BackupManifest m = new BackupManifest(1, "0.1.0", "dev", NOW, "Linux",
                List.of(new ManifestEntry("a/b.txt", 3, Sha256.bytes(bytes("abc")), "rw-r--r--"),
                        new ManifestEntry("c.txt", 0, Sha256.bytes(new byte[0]), null)));
        BackupManifest back = ManifestJson.read(ManifestJson.write(m));
        assertEquals(m, back);
        assertEquals(3, back.totalBytes());

        assertThrows(VaultException.class, () -> ManifestJson.read("not json"));
        assertThrows(VaultException.class, () -> ManifestJson.read("{\"formatVersion\":99,\"entries\":[]}"));
        assertThrows(VaultException.class, () -> ManifestJson.read(
                "{\"formatVersion\":1,\"createdAt\":\"2026-11-13T00:00:00Z\",\"entries\":[{\"path\":\"a\",\"size\":1,\"sha256\":\"zz\"}]}"));
        String dup = "{\"path\":\"a\",\"size\":1,\"sha256\":\"" + Sha256.bytes(bytes("x")) + "\"}";
        assertThrows(VaultException.class, () -> ManifestJson.read(
                "{\"formatVersion\":1,\"createdAt\":\"2026-11-13T00:00:00Z\",\"entries\":[" + dup + "," + dup + "]}"));
    }
}
