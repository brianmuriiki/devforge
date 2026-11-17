package com.devforge.environment.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devforge.environment.VaultException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafePathsTest {

    @TempDir Path tmp;

    @Test
    void acceptsOrdinaryRelativePaths() {
        SafePaths.validateRelative(".gitconfig");
        SafePaths.validateRelative(".config/app/settings.json");
        SafePaths.validateRelative("dir with spaces/file name.txt");
    }

    @Test
    void rejectsTraversalAbsoluteAndPlatformTricks() {
        for (String bad : new String[] {"", "../evil", "a/../../evil", "a/./b", "/etc/passwd", "a//b", "a/", "C:/Windows/x",
            "C:evil", "a\\b", "..\\evil", "a/b:stream", "a\u0000b", "a\nb"}) {
            assertThrows(VaultException.class, () -> SafePaths.validateRelative(bad), "should reject: " + bad.replace("\n", "\\n"));
        }
        assertThrows(VaultException.class, () -> SafePaths.validateRelative(null));
        assertThrows(VaultException.class, () -> SafePaths.validateRelative("x".repeat(2000)));
    }

    @Test
    void resolveUnderStaysInsideBase() {
        assertEquals(tmp.resolve("a/b.txt").toAbsolutePath().normalize(), SafePaths.resolveUnder(tmp, "a/b.txt"));
        assertThrows(VaultException.class, () -> SafePaths.resolveUnder(tmp, "../outside.txt"));
    }
}
