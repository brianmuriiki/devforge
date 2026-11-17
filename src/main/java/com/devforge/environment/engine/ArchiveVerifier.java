package com.devforge.environment.engine;

import com.devforge.core.hash.Sha256;
import com.devforge.environment.VaultException;
import com.devforge.environment.model.BackupManifest;
import com.devforge.environment.model.ManifestEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Checks an archive against its checksum and its own manifest without extracting anything. */
public final class ArchiveVerifier {

    public static final int MAX_MANIFEST_BYTES = 64 * 1024 * 1024;

    /** @param manifest present when the manifest could be read and validated */
    public record Report(boolean ok, List<String> problems, int filesChecked, Optional<BackupManifest> manifest) {}

    private ArchiveVerifier() {}

    /**
     * @param expectedSha256 the archive's recorded SHA-256, or null to skip the archive-level check
     *                       (per-file hashes from the manifest are always checked)
     */
    public static Report verify(Path archive, String expectedSha256) {
        return verify(archive, expectedSha256, null);
    }

    /** Verifies a legacy ZIP or an authenticated encrypted archive. */
    public static Report verify(Path archive, String expectedSha256, char[] passphrase) {
        List<String> problems = new ArrayList<>();
        if (!Files.isRegularFile(archive)) {
            return new Report(false, List.of("Archive not found: " + archive), 0, Optional.empty());
        }
        if (expectedSha256 != null) {
            try {
                String actual = Sha256.file(archive);
                if (!actual.equalsIgnoreCase(expectedSha256.strip())) {
                    return new Report(false, List.of("The archive's SHA-256 does not match the recorded checksum."), 0, Optional.empty());
                }
            } catch (IOException e) {
                return new Report(false, List.of("Could not read the archive: " + e.getMessage()), 0, Optional.empty());
            }
        }
        BackupManifest manifest = null;
        int checked = 0;
        try (EncryptedArchive.Opened opened = EncryptedArchive.open(archive, passphrase)) {
            ZipFile zf = opened.zip();
            ZipEntry manifestEntry = zf.getEntry(BackupEngine.MANIFEST_ENTRY);
            if (manifestEntry == null) {
                return new Report(false, List.of("The archive has no manifest.json."), 0, Optional.empty());
            }
            try (InputStream in = zf.getInputStream(manifestEntry)) {
                byte[] bytes = in.readNBytes(MAX_MANIFEST_BYTES + 1);
                if (bytes.length > MAX_MANIFEST_BYTES) {
                    return new Report(false, List.of("The manifest is too large."), 0, Optional.empty());
                }
                manifest = ManifestJson.read(new String(bytes, StandardCharsets.UTF_8));
            } catch (VaultException e) {
                return new Report(false, List.of(e.getMessage()), 0, Optional.empty());
            }

            Set<String> expected = new HashSet<>();
            for (ManifestEntry entry : manifest.entries()) {
                String name = BackupEngine.FILES_PREFIX + entry.path();
                expected.add(name);
                ZipEntry ze = zf.getEntry(name);
                if (ze == null) {
                    problems.add("Missing from archive: " + entry.path());
                    continue;
                }
                String problem = checkEntry(zf, ze, entry);
                if (problem != null) {
                    problems.add(problem);
                }
                checked++;
            }
            Enumeration<? extends ZipEntry> all = zf.entries();
            while (all.hasMoreElements()) {
                String name = all.nextElement().getName();
                if (!name.equals(BackupEngine.MANIFEST_ENTRY) && !expected.contains(name)) {
                    problems.add("Unexpected entry not listed in the manifest: " + name.replaceAll("[\\p{Cntrl}]", "?"));
                }
            }
        } catch (VaultException e) {
            return new Report(false, List.of(e.getMessage()), 0, Optional.empty());
        } catch (ZipException e) {
            return new Report(false, List.of("The archive is damaged: " + e.getMessage()), 0, Optional.empty());
        } catch (IOException e) {
            return new Report(false, List.of("Could not read the archive: " + e.getMessage()), 0, Optional.empty());
        }
        return new Report(problems.isEmpty(), problems, checked, Optional.ofNullable(manifest));
    }

    private static String checkEntry(ZipFile zf, ZipEntry ze, ManifestEntry entry) throws IOException {
        MessageDigest digest = Sha256.newDigest();
        long count = 0;
        try (InputStream in = zf.getInputStream(ze)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                count += n;
                if (count > entry.size()) {
                    return "Content larger than recorded: " + entry.path();
                }
                digest.update(buffer, 0, n);
            }
        } catch (ZipException e) {
            return "Damaged entry: " + entry.path();
        }
        if (count != entry.size() || !Sha256.hex(digest.digest()).equals(entry.sha256())) {
            return "Content does not match the manifest: " + entry.path();
        }
        return null;
    }
}
