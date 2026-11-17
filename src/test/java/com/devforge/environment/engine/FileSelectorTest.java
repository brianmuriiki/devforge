package com.devforge.environment.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.devforge.environment.VaultException;
import com.devforge.environment.engine.FileSelector.SelectedFile;
import com.devforge.environment.engine.FileSelector.Selection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSelectorTest {

    @TempDir Path tmp;

    private Path home;

    private void write(String rel, String content) throws IOException {
        Path p = home.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @BeforeEach
    void setUp() throws IOException {
        home = Files.createDirectories(tmp.resolve("home"));
        write(".gitconfig", "[user]");
        write(".bashrc", "alias ll='ls -l'");
        write(".config/app/settings.json", "{}");
        write(".config/app/api_token.txt", "tok");
        write(".config/app/cache/big.bin", "bin");
        write(".ssh/id_rsa", "PRIVATE");
        write(".ssh/config", "Host *");
        write("notes/.env", "A=1");
    }

    private static List<String> rels(Selection s) {
        return s.files().stream().map(SelectedFile::relative).sorted().toList();
    }

    @Test
    void resolveIncludeAcceptsTildeRelativeAndAbsoluteInsideHome() {
        assertEquals(home.resolve(".gitconfig"), FileSelector.resolveInclude(home, "~/.gitconfig"));
        assertEquals(home.resolve(".gitconfig"), FileSelector.resolveInclude(home, ".gitconfig"));
        assertEquals(home.resolve(".gitconfig"), FileSelector.resolveInclude(home, home.resolve(".gitconfig").toString()));
        assertEquals(home.resolve("a/c"), FileSelector.resolveInclude(home, "~/a/b/../c"));
    }

    @Test
    void resolveIncludeRejectsPathsOutsideHomeOrHomeItself() {
        assertThrows(VaultException.class, () -> FileSelector.resolveInclude(home, "~/../other"));
        assertThrows(VaultException.class, () -> FileSelector.resolveInclude(home, tmp.toString()));
        assertThrows(VaultException.class, () -> FileSelector.resolveInclude(home, "/etc/passwd"));
        assertThrows(VaultException.class, () -> FileSelector.resolveInclude(home, "~"));
        assertThrows(VaultException.class, () -> FileSelector.resolveInclude(home, "  "));
    }

    @Test
    void selectsFilesAndDirectoriesWithRelativePaths() {
        Selection s = FileSelector.select(home, List.of("~/.gitconfig", "~/.bashrc", "~/.config/app"), List.of("cache"), false);
        assertEquals(List.of(".bashrc", ".config/app/settings.json", ".gitconfig"), rels(s));
        assertEquals(List.of(".config/app/api_token.txt"), s.sensitiveExcluded());
        assertTrue(s.missing().isEmpty());
    }

    @Test
    void excludeGlobsMatchNamesAndPaths() {
        Selection byName = FileSelector.select(home, List.of("~/.config/app"), List.of("*.json"), false);
        assertTrue(rels(byName).stream().noneMatch(r -> r.endsWith(".json")));
        Selection byDir = FileSelector.select(home, List.of("~/.config/app"), List.of("cache"), false);
        assertTrue(rels(byDir).stream().noneMatch(r -> r.contains("cache")));
    }

    @Test
    void secretDirectoriesAndCredentialNamesAreExcludedByDefault() {
        Selection s = FileSelector.select(home, List.of("~/.ssh", "~/notes"), List.of(), false);
        assertTrue(s.files().isEmpty());
        assertTrue(s.sensitiveExcluded().contains(".ssh/"));
        assertTrue(s.sensitiveExcluded().contains("notes/.env"));
        assertTrue(s.sensitiveIncluded().isEmpty());
    }

    @Test
    void explicitSensitiveInclusionIsReported() {
        Selection s = FileSelector.select(home, List.of("~/.ssh", "~/notes"), List.of(), true);
        assertEquals(List.of(".ssh/config", ".ssh/id_rsa", "notes/.env"), rels(s));
        assertTrue(s.sensitiveIncluded().contains(".ssh/"));
        assertTrue(s.sensitiveIncluded().contains("notes/.env"));
        assertTrue(s.sensitiveExcluded().isEmpty());
    }

    @Test
    void missingIncludesAreReportedNotFatal() {
        Selection s = FileSelector.select(home, List.of("~/.gitconfig", "~/.nope"), List.of(), false);
        assertEquals(List.of("~/.nope"), s.missing());
        assertEquals(1, s.files().size());
    }

    @Test
    void symbolicLinksAreSkippedAndCounted() throws IOException {
        try {
            Files.createSymbolicLink(home.resolve(".config/app/link.txt"), home.resolve(".gitconfig"));
            Files.createSymbolicLink(home.resolve("dirlink"), home.resolve(".config"));
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        Selection s = FileSelector.select(home, List.of("~/.config/app", "~/dirlink"), List.of(), false);
        assertTrue(rels(s).stream().noneMatch(r -> r.contains("link")));
        assertEquals(2, s.symlinksSkipped());
    }

    @Test
    void overlappingIncludesDoNotDuplicateFiles() {
        Selection s = FileSelector.select(home, List.of("~/.config", "~/.config/app/settings.json"), List.of(), false);
        assertEquals(1, rels(s).stream().filter(r -> r.equals(".config/app/settings.json")).count());
    }

    @Test
    void invalidExcludePatternsAreRejected() {
        assertThrows(VaultException.class, () -> FileSelector.validateExcludePattern("[unclosed"));
        assertThrows(VaultException.class, () -> FileSelector.validateExcludePattern(" "));
        FileSelector.validateExcludePattern("*.log");
    }
}
