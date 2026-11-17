package com.devforge.environment.cli;

import com.devforge.core.AppContext;
import com.devforge.environment.VaultException;
import com.devforge.environment.engine.EncryptedArchive;
import java.nio.file.Path;
import java.util.Arrays;

/** Obtains passphrases without accepting them as command-line arguments. */
final class VaultPassphrases {

    static final String ENVIRONMENT_VARIABLE = "DEVFORGE_BACKUP_PASSPHRASE";

    private VaultPassphrases() {}

    static char[] forNewBackup(AppContext context) {
        char[] passphrase = fromEnvironmentOrPrompt(context, "Enter a new backup passphrase: ");
        if (passphrase.length < 12) {
            clear(passphrase);
            throw new VaultException("A backup passphrase must be at least 12 characters.");
        }
        if (System.getenv(ENVIRONMENT_VARIABLE) == null) {
            char[] confirmation = context.prompter().askSecret("Confirm the backup passphrase: ")
                    .orElseThrow(VaultPassphrases::unavailable);
            try {
                if (!Arrays.equals(passphrase, confirmation)) {
                    throw new VaultException("The backup passphrases did not match.");
                }
            } finally {
                clear(confirmation);
            }
        }
        return passphrase;
    }

    static char[] forArchive(AppContext context, Path archive) {
        return EncryptedArchive.isEncrypted(archive)
                ? fromEnvironmentOrPrompt(context, "Enter the backup passphrase: ")
                : null;
    }

    static void clear(char[] passphrase) {
        if (passphrase != null) {
            Arrays.fill(passphrase, '\0');
        }
    }

    private static char[] fromEnvironmentOrPrompt(AppContext context, String prompt) {
        String value = System.getenv(ENVIRONMENT_VARIABLE);
        if (value != null && !value.isEmpty()) {
            return value.toCharArray();
        }
        return context.prompter().askSecret(prompt).orElseThrow(VaultPassphrases::unavailable);
    }

    private static VaultException unavailable() {
        return new VaultException("A backup passphrase is required. Run from an interactive terminal or set "
                + ENVIRONMENT_VARIABLE + " in a protected environment.");
    }
}
