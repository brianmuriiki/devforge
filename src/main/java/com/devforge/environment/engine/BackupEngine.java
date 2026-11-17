package com.devforge.environment.engine;

import com.devforge.core.hash.Sha256;
import com.devforge.environment.VaultException;
import com.devforge.environment.engine.FileSelector.SelectedFile;
import com.devforge.environment.model.BackupManifest;
import com.devforge.environment.model.ManifestEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a compressed ZIP archive containing {@code files/<relative path>} entries plus a
 * {@code manifest.json} with SHA-256 per file, then a sidecar {@code .sha256} checksum file and a
 * standalone manifest copy for legacy archives. Encrypted archives keep the manifest inside the
 * authenticated envelope, so file names and metadata are not exposed beside the archive.
 */
public final class BackupEngine {

    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String FILES_PREFIX = "files/";

    public record BackupResult(
            Path archive, Path manifestFile, Path checksumFile, String sha256, long sizeBytes,
            BackupManifest manifest, List<String> skipped) {}

    private BackupEngine() {}

    public static BackupResult create(
            List<SelectedFile> files, String profile, String appVersion, Instant createdAt, Path destinationDir,
            String baseName) {
        return create(files, profile, appVersion, createdAt, destinationDir, baseName, null);
    }

    /** Creates a legacy ZIP when {@code passphrase} is null, otherwise an AES-GCM encrypted archive. */
    public static BackupResult create(
            List<SelectedFile> files, String profile, String appVersion, Instant createdAt, Path destinationDir,
            String baseName, char[] passphrase) {
        boolean encrypted = passphrase != null;
        Path destination = destinationDir.toAbsolutePath().normalize();
        String suffix = encrypted ? ".zip.enc" : ".zip";
        Path archive = destination.resolve(baseName + suffix);
        Path part = destination.resolve(baseName + suffix + ".part");
        Path manifestFile = destination.resolve(baseName + ".manifest.json");
        Path checksumFile = destination.resolve(baseName + suffix + ".sha256");
        try {
            Files.createDirectories(destination);
        } catch (IOException e) {
            throw new VaultException("Could not create the backup directory " + destination + ": " + e.getMessage(), e);
        }
        if (Files.exists(archive)) {
            throw new VaultException("A backup named " + archive.getFileName() + " already exists; nothing was overwritten.");
        }

        List<ManifestEntry> entries = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        BackupManifest manifest;
        try (OutputStream raw = encrypted
                        ? EncryptedArchive.encryptingStream(part, passphrase)
                        : Files.newOutputStream(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                ZipOutputStream zip = new ZipOutputStream(raw)) {
            for (SelectedFile f : files) {
                if (f.absolute().startsWith(destination)) {
                    continue; // never archive the backup directory itself
                }
                try (InputStream in = Files.newInputStream(f.absolute())) {
                    ZipEntry ze = new ZipEntry(FILES_PREFIX + f.relative());
                    ze.setTime(Files.getLastModifiedTime(f.absolute()).toMillis());
                    zip.putNextEntry(ze);
                    MessageDigest digest = Sha256.newDigest();
                    long count = copy(in, zip, digest);
                    zip.closeEntry();
                    entries.add(new ManifestEntry(f.relative(), count, Sha256.hex(digest.digest()), mode(f.absolute())));
                } catch (java.nio.file.NoSuchFileException | java.nio.file.AccessDeniedException e) {
                    skipped.add(f.relative());
                }
            }
            if (entries.isEmpty()) {
                throw new VaultException("None of the selected files could be read; no backup was created.");
            }
            manifest = new BackupManifest(BackupManifest.CURRENT_FORMAT, appVersion, profile, createdAt,
                    System.getProperty("os.name", "unknown"), List.copyOf(entries));
            ZipEntry me = new ZipEntry(MANIFEST_ENTRY);
            zip.putNextEntry(me);
            zip.write(ManifestJson.write(manifest).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
        } catch (IOException | RuntimeException e) {
            deleteQuietly(part);
            if (e instanceof VaultException ve) {
                throw ve;
            }
            throw new VaultException("Could not write the backup archive: " + e.getMessage(), e);
        }

        try {
            String sha = Sha256.file(part);
            moveIntoPlace(part, archive);
            long size = Files.size(archive);
            Files.writeString(checksumFile, sha + "  " + archive.getFileName() + "\n", StandardCharsets.UTF_8);
            if (!encrypted) {
                Files.writeString(manifestFile, ManifestJson.write(manifest), StandardCharsets.UTF_8);
            }
            return new BackupResult(archive, encrypted ? null : manifestFile, checksumFile, sha, size, manifest, skipped);
        } catch (IOException e) {
            deleteQuietly(part);
            deleteQuietly(archive);
            throw new VaultException("Could not finish the backup: " + e.getMessage(), e);
        }
    }

    static String readManifestText(Path archive) throws IOException {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(archive.toFile())) {
            ZipEntry e = zf.getEntry(MANIFEST_ENTRY);
            if (e == null) {
                throw new IOException("manifest missing");
            }
            try (InputStream in = zf.getInputStream(e)) {
                return new String(in.readNBytes(ArchiveVerifier.MAX_MANIFEST_BYTES), StandardCharsets.UTF_8);
            }
        }
    }

    private static long copy(InputStream in, OutputStream out, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) > 0) {
            digest.update(buffer, 0, n);
            out.write(buffer, 0, n);
            total += n;
        }
        return total;
    }

    private static String mode(Path file) {
        try {
            return PosixFilePermissions.toString(Files.getPosixFilePermissions(file));
        } catch (UnsupportedOperationException | IOException e) {
            return null;
        }
    }

    private static void moveIntoPlace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
