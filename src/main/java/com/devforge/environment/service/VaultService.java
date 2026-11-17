package com.devforge.environment.service;

import com.devforge.core.db.ConnectionProvider;
import com.devforge.core.db.Transactions;
import com.devforge.core.project.ActivityLogRepository;
import com.devforge.environment.VaultException;
import com.devforge.environment.engine.ArchiveVerifier;
import com.devforge.environment.engine.BackupEngine;
import com.devforge.environment.engine.BackupEngine.BackupResult;
import com.devforge.environment.engine.FileSelector;
import com.devforge.environment.engine.FileSelector.Selection;
import com.devforge.environment.engine.RestoreEngine;
import com.devforge.environment.model.BackupRecord;
import com.devforge.environment.model.VaultProfile;
import com.devforge.environment.repo.VaultRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * DevVault business logic: profiles, backups, verification and restore. Only files below the
 * user's home directory are backed up or restored. Secrets are excluded unless explicitly requested.
 */
public final class VaultService {

    public static final List<String> DEFAULT_INCLUDES = List.of(
            "~/.gitconfig", "~/.gitignore_global", "~/.bashrc", "~/.zshrc", "~/.profile", "~/.vimrc", "~/.editorconfig");

    private static final Pattern PROFILE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final String MODULE = "environment";

    /** Everything about a finished backup. */
    public record BackupOutcome(BackupRecord record, Selection selection, List<String> skippedUnreadable) {}

    private final Transactions tx;
    private final Clock clock;
    private final Path home;
    private final Path defaultBackupDir;
    private final String appVersion;
    private final VaultRepository repo = new VaultRepository();
    private final ActivityLogRepository activity = new ActivityLogRepository();

    public VaultService(ConnectionProvider provider, Clock clock, Path home, Path defaultBackupDir, String appVersion) {
        this.tx = new Transactions(Objects.requireNonNull(provider, "provider"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
        this.defaultBackupDir = Objects.requireNonNull(defaultBackupDir, "defaultBackupDir");
        this.appVersion = Objects.requireNonNull(appVersion, "appVersion");
    }

    public Path home() {
        return home;
    }

    // ---- profiles ----

    public VaultProfile createProfile(
            String name, String description, List<String> includes, List<String> excludes, String destination) {
        String cleanName = name == null ? "" : name.strip();
        if (!PROFILE_NAME.matcher(cleanName).matches()) {
            throw new VaultException("Invalid profile name. Use 1-64 letters, digits, '.', '_' or '-', starting with a letter or digit.");
        }
        List<String> inc = new ArrayList<>();
        for (String raw : includes.isEmpty() ? DEFAULT_INCLUDES : includes) {
            FileSelector.resolveInclude(home, raw); // validates: inside the home directory
            inc.add(raw.strip());
        }
        List<String> exc = new ArrayList<>();
        for (String pattern : excludes) {
            FileSelector.validateExcludePattern(pattern);
            exc.add(pattern.strip());
        }
        String dest = destination == null ? null : resolveDestination(destination);
        String desc = description == null ? "" : description.strip();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        return tx.inTransaction("Could not create the profile", c -> {
            if (repo.findProfile(c, cleanName).isPresent()) {
                throw new VaultException("A profile named '" + cleanName + "' already exists.");
            }
            long id;
            try {
                id = repo.insertProfile(c, cleanName, desc, dest, now);
            } catch (SQLException e) {
                if (String.valueOf(e.getMessage()).contains("vault_profiles.name")) {
                    throw new VaultException("A profile named '" + cleanName + "' already exists.");
                }
                throw e;
            }
            for (String p : inc) {
                repo.insertPath(c, id, "INCLUDE", p);
            }
            for (String p : exc) {
                repo.insertPath(c, id, "EXCLUDE", p);
            }
            activity.record(c, null, MODULE, "profile.create", cleanName, now);
            return repo.findProfile(c, cleanName).orElseThrow();
        });
    }

    public List<VaultProfile> listProfiles() {
        return tx.read("Could not read the profiles", repo::listProfiles);
    }

    public VaultProfile getProfile(String name) {
        return tx.read("Could not read the profile", c -> repo.findProfile(c, name == null ? "" : name.strip()))
                .orElseThrow(() -> new VaultException(
                        "No profile named '" + name + "'. Run 'devforge env profile list' to see profiles."));
    }

    /** The named profile, or the only profile if no name is given. */
    public VaultProfile resolveProfile(String name) {
        if (name != null) {
            return getProfile(name);
        }
        List<VaultProfile> all = listProfiles();
        if (all.isEmpty()) {
            throw new VaultException("No profiles exist. Create one with 'devforge env profile create <name>'.");
        }
        if (all.size() > 1) {
            List<String> names = new ArrayList<>();
            all.forEach(p -> names.add(p.name()));
            throw new VaultException("Several profiles exist (" + String.join(", ", names) + "). Choose one with --profile.");
        }
        return all.get(0);
    }

    /** Removes the profile definition. Existing backups and their archives are kept. */
    public void deleteProfile(String name) {
        VaultProfile p = getProfile(name);
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        tx.inTransaction("Could not delete the profile", c -> {
            repo.deleteProfile(c, p.id());
            activity.record(c, null, MODULE, "profile.delete", p.name(), now);
            return null;
        });
    }

    private String resolveDestination(String destination) {
        String text = destination.strip();
        if (text.isEmpty()) {
            throw new VaultException("The backup destination must not be empty.");
        }
        try {
            Path p = text.equals("~") ? home : text.startsWith("~/") ? home.resolve(text.substring(2)) : Path.of(text);
            if (!p.isAbsolute()) {
                throw new VaultException("The backup destination must be an absolute path: " + text);
            }
            return p.normalize().toString();
        } catch (InvalidPathException e) {
            throw new VaultException("Invalid backup destination: " + text);
        }
    }

    // ---- backup ----

    public Selection select(VaultProfile profile, boolean includeSensitive) {
        return FileSelector.select(home, profile.includes(), profile.excludes(), includeSensitive);
    }

    public BackupOutcome backup(VaultProfile profile, boolean includeSensitive, Selection selection) {
        if (selection.files().isEmpty()) {
            throw new VaultException("Nothing to back up: the profile's paths contain no eligible files"
                    + (selection.missing().isEmpty() ? "" : " (missing: " + String.join(", ", selection.missing()) + ")")
                    + (selection.sensitiveExcluded().isEmpty() ? "" : "; " + selection.sensitiveExcluded().size()
                            + " sensitive file(s) were excluded") + ".");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Path destDir = profile.destination() != null ? Path.of(profile.destination()) : defaultBackupDir;
        String base = uniqueBaseName(destDir, profile.name() + "-" + STAMP.format(now));

        BackupResult result = BackupEngine.create(selection.files(), profile.name(), appVersion, now, destDir, base);
        ArchiveVerifier.Report report = ArchiveVerifier.verify(result.archive(), result.sha256());
        if (!report.ok()) {
            deleteQuietly(result.archive());
            deleteQuietly(result.manifestFile());
            deleteQuietly(result.checksumFile());
            throw new VaultException("Backup verification failed; the archive was discarded. " + firstProblems(report));
        }
        boolean sensitive = includeSensitive && !selection.sensitiveIncluded().isEmpty();
        BackupRecord record = tx.inTransaction("Could not register the backup", c -> {
            long id = repo.insertBackup(c, profile.id(), base, result.archive().toString(), result.manifestFile().toString(),
                    result.sha256(), now, true, result.sizeBytes(), result.manifest().entries().size(), appVersion, sensitive);
            activity.record(c, null, MODULE, "backup.create", base + " files=" + result.manifest().entries().size(), now);
            return repo.findBackup(c, id).orElseThrow();
        });
        return new BackupOutcome(record, selection, result.skipped());
    }

    private static String uniqueBaseName(Path dir, String base) {
        String candidate = base;
        for (int n = 2; Files.exists(dir.resolve(candidate + ".zip")); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    public List<BackupRecord> backups() {
        return tx.read("Could not read the backups", repo::listBackups);
    }

    public Optional<BackupRecord> latestBackup() {
        return tx.read("Could not read the backups", repo::latestBackup);
    }

    /** The backup with this id, or the most recent one when {@code id} is null. */
    public BackupRecord getBackup(Long id) {
        Optional<BackupRecord> found = id == null
                ? tx.read("Could not read the backups", repo::latestBackup)
                : tx.read("Could not read the backup", c -> repo.findBackup(c, id));
        return found.orElseThrow(() -> new VaultException(id == null
                ? "No backups exist yet. Create one with 'devforge env backup'."
                : "No backup with id " + id + ". Run 'devforge env backups' to see backups."));
    }

    // ---- verify ----

    /** Verifies a registered backup and records the outcome. */
    public ArchiveVerifier.Report verify(BackupRecord record) {
        ArchiveVerifier.Report report = ArchiveVerifier.verify(Path.of(record.archivePath()), record.checksum());
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        tx.inTransaction("Could not record the verification", c -> {
            repo.markVerified(c, record.id(), report.ok(), now);
            return null;
        });
        return report;
    }

    /** Verifies an archive that is not registered (for example, one copied from another computer). */
    public ArchiveVerifier.Report verifyArchive(Path archive, String checksumOrNull) {
        return ArchiveVerifier.verify(archive, resolveChecksum(archive, checksumOrNull));
    }

    // ---- restore ----

    /**
     * Verifies the archive, then plans the restore without touching the destination.
     *
     * @param backupId registered backup (null = latest); ignored when {@code archive} is given
     * @param archive  an archive file not registered here; needs {@code checksum} or a {@code .sha256} file beside it
     */
    public RestoreEngine.Plan prepareRestore(Long backupId, Path archive, String checksum, Path targetHome) {
        Path file;
        String expected;
        if (archive != null) {
            file = archive.toAbsolutePath().normalize();
            expected = resolveChecksum(file, checksum);
        } else {
            BackupRecord record = getBackup(backupId);
            file = Path.of(record.archivePath());
            expected = record.checksum();
        }
        ArchiveVerifier.Report report = ArchiveVerifier.verify(file, expected);
        if (!report.ok() || report.manifest().isEmpty()) {
            throw new VaultException("Backup checksum verification failed. Nothing was restored. " + firstProblems(report));
        }
        return RestoreEngine.plan(file, targetHome, report.manifest().get());
    }

    public RestoreEngine.Result restore(RestoreEngine.Plan plan, boolean overwriteConflicts) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        RestoreEngine.Result result = RestoreEngine.execute(plan, overwriteConflicts, now);
        tx.inTransaction("Could not log the restore", c -> {
            activity.record(c, null, MODULE, "restore", "restored=" + result.restored() + " failures=" + result.failures().size(), now);
            return null;
        });
        return result;
    }

    private static String resolveChecksum(Path archive, String explicit) {
        if (explicit != null) {
            if (!SHA256.matcher(explicit.strip()).matches()) {
                throw new VaultException("The checksum must be 64 hexadecimal characters (SHA-256).");
            }
            return explicit.strip().toLowerCase(java.util.Locale.ROOT);
        }
        Path sidecar = archive.resolveSibling(archive.getFileName() + ".sha256");
        if (Files.isRegularFile(sidecar)) {
            try {
                String first = Files.readString(sidecar, StandardCharsets.UTF_8).strip().split("\\s+")[0];
                if (SHA256.matcher(first).matches()) {
                    return first.toLowerCase(java.util.Locale.ROOT);
                }
            } catch (IOException e) {
                // fall through to the error below
            }
        }
        throw new VaultException("No checksum available for " + archive.getFileName()
                + ". Keep the .sha256 file beside the archive or pass --checksum <sha256>.");
    }

    private static String firstProblems(ArchiveVerifier.Report report) {
        List<String> shown = report.problems().subList(0, Math.min(3, report.problems().size()));
        return String.join(" ", shown);
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
