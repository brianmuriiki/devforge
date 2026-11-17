package com.devforge.core.files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveFilesTest {

    @Test
    void recognizesSecretFileNames() {
        for (String n : new String[] {".env", ".env.local", "id_rsa", "id_rsa.pub", "id_ed25519", "server.pem", "my.KEY",
            "store.p12", "vault.kdbx", ".npmrc", ".netrc", "credentials", ".git-credentials", "Cookies"}) {
            assertTrue(SensitiveFiles.isSecretFileName(n), n);
        }
    }

    @Test
    void ordinaryFilesAreNotSecret() {
        for (String n : new String[] {"Main.java", "README.md", "environment.txt", ".gitconfig", "keyboard.txt", "monkey.js"}) {
            assertFalse(SensitiveFiles.isSecretFileName(n), n);
        }
    }

    @Test
    void secretDirectories() {
        assertTrue(SensitiveFiles.isSecretDirectory(".ssh"));
        assertTrue(SensitiveFiles.isSecretDirectory(".GNUPG"));
        assertTrue(SensitiveFiles.isSecretDirectory(".aws"));
        assertFalse(SensitiveFiles.isSecretDirectory("src"));
    }

    @Test
    void credentialLookingNamesAreFlaggedForBackups() {
        assertTrue(SensitiveFiles.looksLikeCredentialName("github_token.txt"));
        assertTrue(SensitiveFiles.looksLikeCredentialName("my-secrets.yaml"));
        assertTrue(SensitiveFiles.looksLikeCredentialName("passwords.csv"));
        assertFalse(SensitiveFiles.looksLikeCredentialName("settings.json"));
    }
}
