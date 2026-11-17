package com.devforge.environment.engine;

import com.devforge.core.hash.Sha256;
import com.devforge.environment.VaultException;
import com.devforge.environment.model.BackupManifest;
import com.devforge.environment.model.ManifestEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Plans and performs restores. Planning never touches the destination. Execution never follows
 * symbolic links, never overwrites without permission, saves every replaced file to a recovery
 * directory first, writes through temporary files, and verifies each restored file's SHA-256.
 */
public final class RestoreEngine {

    public static final String RECOVERY_DIR = ".devforge-recovery";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** What restoring one file would do. */
    public enum Action {
        /** The target does not exist and will be created. */
        CREATE,
        /** The target already has exactly this content. */
        IDENTICAL,
        /** The target exists with different content; overwriting needs permission. */
        CONFLICT,
        /** The target cannot be written safely (symbolic link, directory in the way). */
        BLOCKED
    }

    public record PlanEntry(ManifestEntry entry, Path target, Action action, String reason) {}

    public record Plan(Path archive, Path targetHome, BackupManifest manifest, List<PlanEntry> entries) {

        public long count(Action action) {
            return entries.stream().filter(e -> e.action() == action).count();
        }
    }

    public record Result(int restored, int skippedIdentical, int conflictsSkipped, int recovered, Path recoveryDir,
            List<String> failures) {}

    private RestoreEngine() {}

    public static Plan plan(Path archive, Path targetHome, BackupManifest manifest) {
        Path home = targetHome.toAbsolutePath().normalize();
        List<PlanEntry> entries = new ArrayList<>();
        for (ManifestEntry entry : manifest.entries()) {
            Path target = SafePaths.resolveUnder(home, entry.path());
            String blocked = blockedReason(home, target);
            if (blocked != null) {
                entries.add(new PlanEntry(entry, target, Action.BLOCKED, blocked));
            } else if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                entries.add(new PlanEntry(entry, target, Action.CREATE, ""));
            } else {
                boolean same = false;
                try {
                    same = Files.size(target) == entry.size() && Sha256.file(target).equals(entry.sha256());
                } catch (IOException e) {
                    // unreadable target counts as a conflict
                }
                entries.add(same
                        ? new PlanEntry(entry, target, Action.IDENTICAL, "")
                        : new PlanEntry(entry, target, Action.CONFLICT, "a different file already exists"));
            }
        }
        return new Plan(archive.toAbsolutePath().normalize(), home, manifest, List.copyOf(entries));
    }

    private static String blockedReason(Path home, Path target) {
        Path p = target.getParent();
        while (p != null && p.startsWith(home) && !p.equals(home)) {
            if (Files.isSymbolicLink(p)) {
                return "the path passes through a symbolic link";
            }
            p = p.getParent();
        }
        if (Files.isSymbolicLink(target)) {
            return "a symbolic link exists at the target";
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            return "a directory exists at the target";
        }
        return null;
    }

    /**
     * @param overwriteConflicts if false, CONFLICT entries are left untouched and counted
     */
    public static Result execute(Plan plan, boolean overwriteConflicts, Instant now) {
        Path recoveryDir = plan.targetHome().resolve(RECOVERY_DIR).resolve(STAMP.format(now));
        int restored = 0;
        int identical = 0;
        int skippedConflicts = 0;
        int recovered = 0;
        List<String> failures = new ArrayList<>();

        try (ZipFile zf = new ZipFile(plan.archive().toFile())) {
            for (PlanEntry pe : plan.entries()) {
                String label = pe.entry().path();
                switch (pe.action()) {
                    case IDENTICAL -> identical++;
                    case BLOCKED -> failures.add(label + ": " + pe.reason());
                    case CONFLICT, CREATE -> {
                        if (pe.action() == Action.CONFLICT && !overwriteConflicts) {
                            skippedConflicts++;
                            continue;
                        }
                        try {
                            String again = blockedReason(plan.targetHome(), pe.target());
                            if (again != null && pe.action() == Action.CREATE) {
                                throw new IOException(again);
                            }
                            if (pe.action() == Action.CONFLICT) {
                                saveRecoveryCopy(pe, recoveryDir);
                                recovered++;
                            }
                            extract(zf, pe);
                            restored++;
                        } catch (IOException | VaultException e) {
                            failures.add(label + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new VaultException("Could not read the archive: " + e.getMessage(), e);
        }
        return new Result(restored, identical, skippedConflicts, recovered, recovered > 0 ? recoveryDir : null, failures);
    }

    private static void saveRecoveryCopy(PlanEntry pe, Path recoveryDir) throws IOException {
        Path copy = SafePaths.resolveUnder(recoveryDir, pe.entry().path());
        Files.createDirectories(copy.getParent());
        Files.copy(pe.target(), copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES,
                LinkOption.NOFOLLOW_LINKS);
    }

    private static void extract(ZipFile zf, PlanEntry pe) throws IOException {
        ManifestEntry entry = pe.entry();
        ZipEntry ze = zf.getEntry(BackupEngine.FILES_PREFIX + entry.path());
        if (ze == null) {
            throw new IOException("missing from the archive");
        }
        Path target = pe.target();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".devforge-restore-", ".tmp");
        try {
            MessageDigest digest = Sha256.newDigest();
            long count = 0;
            try (InputStream in = zf.getInputStream(ze); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    count += n;
                    if (count > entry.size()) {
                        throw new IOException("content is larger than recorded");
                    }
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
            }
            if (count != entry.size() || !Sha256.hex(digest.digest()).equals(entry.sha256())) {
                throw new IOException("content does not match the manifest");
            }
            if (entry.mode() != null) {
                try {
                    Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString(entry.mode()));
                } catch (UnsupportedOperationException | IllegalArgumentException e) {
                    // permissions are best effort and platform dependent
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!Sha256.file(target).equals(entry.sha256())) {
                throw new IOException("verification after restore failed");
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
