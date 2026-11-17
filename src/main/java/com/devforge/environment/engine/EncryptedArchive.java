package com.devforge.environment.engine;

import com.devforge.environment.VaultException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.zip.ZipFile;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM envelope for DevVault ZIP archives. */
public final class EncryptedArchive {

    private static final byte[] MAGIC = "DFVAULT1".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 600_000;

    /** A ZIP file and, for encrypted input, its short-lived decrypted copy. */
    public record Opened(ZipFile zip, Path temporaryFile) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                zip.close();
            } finally {
                if (temporaryFile != null) {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        }
    }

    private EncryptedArchive() {}

    public static boolean isEncrypted(Path archive) {
        if (!Files.isRegularFile(archive)) {
            return false;
        }
        try (InputStream input = Files.newInputStream(archive)) {
            return Arrays.equals(input.readNBytes(MAGIC.length), MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    /** Opens a legacy ZIP directly or decrypts an authenticated DevVault archive to a temporary ZIP. */
    public static Opened open(Path archive, char[] passphrase) throws IOException {
        if (!isEncrypted(archive)) {
            return new Opened(new ZipFile(archive.toFile()), null);
        }
        requirePassphrase(passphrase);
        Path temporary = Files.createTempFile("devforge-decrypt-", ".zip");
        try {
            try (InputStream raw = Files.newInputStream(archive); InputStream clear = decryptingStream(raw, passphrase);
                    OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                clear.transferTo(output);
            }
            return new Opened(new ZipFile(temporary.toFile()), temporary);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            if (isAuthenticationFailure(e)) {
                throw new VaultException("The backup passphrase is incorrect or the encrypted archive was modified.", e);
            }
            throw e;
        }
    }

    /** Creates an output stream that writes an encrypted DevVault archive. */
    public static OutputStream encryptingStream(Path destination, char[] passphrase) throws IOException {
        requirePassphrase(passphrase);
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(salt);
        new SecureRandom().nextBytes(iv);
        try {
            OutputStream raw = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            byte[] header = header(salt, iv);
            raw.write(header);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, iv, header);
            return new CipherOutputStream(raw, cipher);
        } catch (GeneralSecurityException e) {
            throw new VaultException("Could not initialize backup encryption.", e);
        }
    }

    private static InputStream decryptingStream(InputStream raw, char[] passphrase) throws IOException {
        byte[] header = raw.readNBytes(MAGIC.length + 1 + SALT_BYTES + IV_BYTES);
        if (header.length != MAGIC.length + 1 + SALT_BYTES + IV_BYTES
                || !Arrays.equals(Arrays.copyOf(header, MAGIC.length), MAGIC)
                || header[MAGIC.length] != VERSION) {
            throw new VaultException("The encrypted backup header is invalid or unsupported.");
        }
        byte[] salt = Arrays.copyOfRange(header, MAGIC.length + 1, MAGIC.length + 1 + SALT_BYTES);
        byte[] iv = Arrays.copyOfRange(header, MAGIC.length + 1 + SALT_BYTES, header.length);
        try {
            return new CipherInputStream(raw, cipher(Cipher.DECRYPT_MODE, passphrase, salt, iv, header));
        } catch (GeneralSecurityException e) {
            throw new VaultException("Could not initialize backup decryption.", e);
        }
    }

    private static Cipher cipher(int mode, char[] passphrase, byte[] salt, byte[] iv, byte[] header)
            throws GeneralSecurityException {
        KeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(header);
            return cipher;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] header(byte[] salt, byte[] iv) {
        byte[] header = new byte[MAGIC.length + 1 + SALT_BYTES + IV_BYTES];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[MAGIC.length] = VERSION;
        System.arraycopy(salt, 0, header, MAGIC.length + 1, SALT_BYTES);
        System.arraycopy(iv, 0, header, MAGIC.length + 1 + SALT_BYTES, IV_BYTES);
        return header;
    }

    private static void requirePassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length < 12) {
            throw new VaultException("An encrypted backup requires a passphrase of at least 12 characters.");
        }
    }

    private static boolean isAuthenticationFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof AEADBadTagException) {
                return true;
            }
        }
        return false;
    }
}
