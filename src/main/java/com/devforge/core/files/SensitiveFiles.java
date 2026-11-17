package com.devforge.core.files;

import java.util.Locale;
import java.util.Set;

/**
 * Recognizes files and directories that commonly hold secrets. Shared by FindX (never index them)
 * and DevVault (never back them up by default). Detection is best-effort and name-based.
 */
public final class SensitiveFiles {

    private static final Set<String> EXACT_NAMES = Set.of(
            ".env", ".netrc", "_netrc", ".npmrc", ".pypirc", ".git-credentials", ".pgpass", ".my.cnf",
            "credentials", "credentials.json", "secrets.json", "secrets.yml", "secrets.yaml",
            "service-account.json", "known_hosts.old", "key4.db", "logins.json", "cert9.db",
            "login data", "cookies", "web data");
    private static final String[] PREFIXES = {"id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", ".env."};
    private static final String[] SUFFIXES = {
        ".pem", ".key", ".p12", ".pfx", ".pkcs12", ".jks", ".keystore", ".kdbx", ".gpg", ".asc", ".ppk"
    };
    private static final Set<String> SECRET_DIRECTORIES = Set.of(".ssh", ".gnupg", ".aws", ".azure");

    private SensitiveFiles() {}

    /** True if a file with this name is treated as secret material. */
    public static boolean isSecretFileName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (EXACT_NAMES.contains(name)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        for (String suffix : SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /** True if everything inside a directory with this name is treated as secret material. */
    public static boolean isSecretDirectory(String directoryName) {
        return SECRET_DIRECTORIES.contains(directoryName.toLowerCase(Locale.ROOT));
    }

    /** Stricter check used for backups: also flags names that merely suggest credentials. */
    public static boolean looksLikeCredentialName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return isSecretFileName(fileName)
                || name.contains("token")
                || name.contains("secret")
                || name.contains("password")
                || name.contains("passwd");
    }
}
