package com.devforge.environment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.core.db.DatabaseManager;
import com.devforge.core.hash.Sha256;
import com.devforge.core.paths.AppPaths;
import com.devforge.core.paths.OperatingSystem;
import com.devforge.environment.VaultException;
import com.devforge.environment.engine.ArchiveVerifier;
import com.devforge.environment.engine.FileSelector.Selection;
import com.devforge.environment.engine.RestoreEngine;
import com.devforge.environment.model.BackupRecord;
import com.devforge.environment.model.VaultProfile;
import com.devforge.environment.service.VaultService.BackupOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);

    @TempDir Path tmp;

    private Path home;
    private Path backupDir;
    private VaultService service;

    private void write(Path base, String rel, String content) throws IOException {
        Path p = base.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @BeforeEach
    void setUp() throws IOException {
        AppPaths paths = AppPaths.resolve(
                OperatingSystem.LINUX, Map.of("DEVFORGE_HOME", tmp.resolve("devforge").toString()), tmp);
        DatabaseManager database = new DatabaseManager(paths, CLOCK);
        database.initialize();
        home = Files.createDirectories(tmp.resolve("home"));
        backupDir = tmp.resolve("backups");
        service = new VaultService(database.connectionProvider(), CLOCK, home, backupDir, "0.1.0");
        write(home, ".gitconfig", "[user]\nname = Dev");
        write(home, ".bashrc", "export X=1");
        write(home, ".config/app/settings.json", "{}");
        write(home, ".config/app/api_token.txt", "tok");
        write(home, ".ssh/id_rsa", "PRIVATE");
    }

    private VaultProfile profile() {
        return service.createProfile("dev", "my profile", List.of("~/.gitconfig", "~/.bashrc", "~/.config/app"), List.of(), null);
    }

    @Test
    void createProfileStoresPathsAndUsesDefaultsWhenNoneGiven() {
        VaultProfile custom = service.createProfile("java-development", "d", List.of("~/.gitconfig"), List.of("*.bak"), null);
        assertEquals("java-development", custom.name());
        assertEquals(List.of("~/.gitconfig"), custom.includes());
        assertEquals(List.of("*.bak"), custom.excludes());
        assertEquals("d", custom.description());

        VaultProfile defaults = service.createProfile("defaults", "", List.of(), List.of(), null);
        assertEquals(VaultService.DEFAULT_INCLUDES, defaults.includes());
        assertEquals(List.of("defaults", "java-development"), service.listProfiles().stream().map(VaultProfile::name).toList());
        assertEquals(custom, service.getProfile("JAVA-development"));
    }

    @Test
    void profileValidation() {
        assertThrows(VaultException.class, () -> service.createProfile("bad name!", "", List.of(), List.of(), null));
        assertThrows(VaultException.class, () -> service.createProfile("", "", List.of(), List.of(), null));
        assertThrows(VaultException.class, () -> service.createProfile("x", "", List.of("/etc/passwd"), List.of(), null));
        assertThrows(VaultException.class, () -> service.createProfile("x", "", List.of("~/../x"), List.of(), null));
        assertThrows(VaultException.class, () -> service.createProfile("x", "", List.of("~/.a"), List.of("[bad"), null));
        assertThrows(VaultException.class, () -> service.createProfile("x", "", List.of("~/.a"), List.of(), "relative/dir"));
        profile();
        VaultException dup = assertThrows(VaultException.class, () -> service.createProfile("DEV", "", List.of("~/.a"), List.of(), null));
        assertTrue(dup.getMessage().contains("already exists"));
        assertThrows(VaultException.class, () -> service.getProfile("nope"));
    }

    @Test
    void resolveProfileNeedsExactlyOneUnlessNamed() {
        assertTrue(assertThrows(VaultException.class, () -> service.resolveProfile(null)).getMessage().contains("No profiles"));
        VaultProfile only = profile();
        assertEquals(only, service.resolveProfile(null));
        service.createProfile("second", "", List.of("~/.gitconfig"), List.of(), null);
        assertTrue(assertThrows(VaultException.class, () -> service.resolveProfile(null)).getMessage().contains("--profile"));
        assertEquals("second", service.resolveProfile("second").name());
    }

    @Test
    void backupCreatesAVerifiedRegisteredArchiveAndExcludesSecrets() throws IOException {
        VaultProfile p = profile();
        Selection sel = service.select(p, false);
        assertEquals(List.of(".config/app/api_token.txt"), sel.sensitiveExcluded());

        BackupOutcome o = service.backup(p, false, sel);
        BackupRecord r = o.record();
        assertTrue(r.verified());
        assertFalse(r.includesSensitive());
        assertEquals(3, r.fileCount());
        assertEquals("dev", r.profileName());
        assertEquals(Sha256.file(Path.of(r.archivePath())), r.checksum());
        assertTrue(Path.of(r.archivePath()).startsWith(backupDir));
        assertTrue(Files.exists(Path.of(r.manifestPath())));
        assertEquals(List.of(r), service.backups());
        assertEquals(r, service.getBackup(null));
        assertEquals(r, service.getBackup(r.id()));

        var manifest = ArchiveVerifier.verify(Path.of(r.archivePath()), r.checksum()).manifest().orElseThrow();
        assertTrue(manifest.entries().stream().noneMatch(e -> e.path().contains("token") || e.path().contains(".ssh")));
    }

    @Test
    void sensitiveInclusionIsExplicitAndRecorded() {
        VaultProfile p = service.createProfile("ssh", "", List.of("~/.ssh", "~/.gitconfig"), List.of(), null);
        Selection without = service.select(p, false);
        assertEquals(1, without.files().size());

        Selection with = service.select(p, true);
        BackupOutcome o = service.backup(p, true, with);
        assertTrue(o.record().includesSensitive());
        assertEquals(2, o.record().fileCount());
    }

    @Test
    void nothingToBackUpIsAnErrorWithAHint() {
        VaultProfile p = service.createProfile("empty", "", List.of("~/.missing", "~/.ssh"), List.of(), null);
        VaultException e = assertThrows(VaultException.class, () -> service.backup(p, false, service.select(p, false)));
        assertTrue(e.getMessage().contains("Nothing to back up"));
        assertTrue(e.getMessage().contains("~/.missing"));
        assertTrue(e.getMessage().contains("sensitive"));
        assertTrue(service.backups().isEmpty());
    }

    @Test
    void backupsMadeInTheSameSecondGetDistinctNames() {
        VaultProfile p = profile();
        BackupRecord first = service.backup(p, false, service.select(p, false)).record();
        BackupRecord second = service.backup(p, false, service.select(p, false)).record();
        assertFalse(first.archivePath().equals(second.archivePath()));
        assertTrue(second.name().endsWith("-2"));
        assertEquals(2, service.backups().size());
    }

    @Test
    void profileDestinationOverridesTheDefaultDirectory() {
        Path custom = tmp.resolve("custom-dest");
        VaultProfile p = service.createProfile("elsewhere", "", List.of("~/.gitconfig"), List.of(), custom.toString());
        BackupRecord r = service.backup(p, false, service.select(p, false)).record();
        assertTrue(Path.of(r.archivePath()).startsWith(custom));
    }

    @Test
    void verifyRecordsTheOutcomeAndDetectsTampering() throws IOException {
        VaultProfile p = profile();
        BackupRecord r = service.backup(p, false, service.select(p, false)).record();
        assertTrue(service.verify(r).ok());
        assertTrue(service.getBackup(r.id()).verified());

        byte[] data = Files.readAllBytes(Path.of(r.archivePath()));
        data[data.length / 3] ^= 0x7F;
        Files.write(Path.of(r.archivePath()), data);
        assertFalse(service.verify(r).ok());
        assertFalse(service.getBackup(r.id()).verified());
    }

    @Test
    void restoreByIdRecreatesFilesInAnotherHome() throws IOException {
        VaultProfile p = profile();
        BackupRecord r = service.backup(p, false, service.select(p, false)).record();

        Path newHome = Files.createDirectories(tmp.resolve("new-home"));
        RestoreEngine.Plan plan = service.prepareRestore(r.id(), null, null, newHome);
        assertEquals(3, plan.count(RestoreEngine.Action.CREATE));
        RestoreEngine.Result result = service.restore(plan, false);

        assertEquals(3, result.restored());
        assertEquals("export X=1", Files.readString(newHome.resolve(".bashrc")));
        assertEquals("{}", Files.readString(newHome.resolve(".config/app/settings.json")));
        assertFalse(Files.exists(newHome.resolve(".config/app/api_token.txt")));
    }

    @Test
    void restoreRefusesATamperedArchiveAndWritesNothing() throws IOException {
        VaultProfile p = profile();
        BackupRecord r = service.backup(p, false, service.select(p, false)).record();
        byte[] data = Files.readAllBytes(Path.of(r.archivePath()));
        data[data.length / 2] ^= 0x01;
        Files.write(Path.of(r.archivePath()), data);

        Path newHome = Files.createDirectories(tmp.resolve("new-home"));
        VaultException e = assertThrows(VaultException.class, () -> service.prepareRestore(r.id(), null, null, newHome));
        assertTrue(e.getMessage().contains("Backup checksum verification failed"));
        try (var files = Files.list(newHome)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void restoreFromACopiedArchiveNeedsAChecksum() throws IOException {
        VaultProfile p = profile();
        BackupRecord r = service.backup(p, false, service.select(p, false)).record();
        Path transferred = Files.createDirectories(tmp.resolve("usb"));
        Path archive = Files.copy(Path.of(r.archivePath()), transferred.resolve("backup.zip"));
        Path newHome = Files.createDirectories(tmp.resolve("new-home"));

        VaultException none = assertThrows(VaultException.class, () -> service.prepareRestore(null, archive, null, newHome));
        assertTrue(none.getMessage().contains("No checksum"));
        assertThrows(VaultException.class, () -> service.prepareRestore(null, archive, "not-a-hash", newHome));
        assertThrows(VaultException.class, () -> service.prepareRestore(null, archive, "0".repeat(64), newHome));

        assertEquals(3, service.prepareRestore(null, archive, r.checksum().toUpperCase(), newHome).count(RestoreEngine.Action.CREATE));

        Files.writeString(transferred.resolve("backup.zip.sha256"), r.checksum() + "  backup.zip\n");
        assertEquals(3, service.prepareRestore(null, archive, null, newHome).count(RestoreEngine.Action.CREATE));
    }

    @Test
    void verifyArchiveWorksWithoutARegisteredBackup() throws IOException {
        VaultProfile p = profile();
        BackupRecord r = service.backup(p, false, service.select(p, false)).record();
        assertTrue(service.verifyArchive(Path.of(r.archivePath()), null).ok()); // sidecar from the backup
        assertFalse(service.verifyArchive(Path.of(r.archivePath()), "1".repeat(64)).ok());
    }

    @Test
    void deletingAProfileKeepsItsBackups() {
        VaultProfile p = profile();
        BackupRecord r = service.backup(p, false, service.select(p, false)).record();
        service.deleteProfile("dev");

        assertTrue(service.listProfiles().isEmpty());
        BackupRecord after = service.getBackup(r.id());
        assertNull(after.profileName());
        assertTrue(Files.exists(Path.of(after.archivePath())));
    }

    @Test
    void unknownBackupsAreReportedClearly() {
        assertTrue(assertThrows(VaultException.class, () -> service.getBackup(null)).getMessage().contains("No backups"));
        assertTrue(assertThrows(VaultException.class, () -> service.getBackup(42L)).getMessage().contains("No backup with id 42"));
    }

    @Test
    void sourceFilesAreNeverModifiedByABackup() throws IOException {
        String before = Files.readString(home.resolve(".bashrc"));
        VaultProfile p = profile();
        service.backup(p, false, service.select(p, false));
        assertEquals(before, Files.readString(home.resolve(".bashrc")));
        assertTrue(Files.exists(home.resolve(".config/app/api_token.txt")));
    }
}
