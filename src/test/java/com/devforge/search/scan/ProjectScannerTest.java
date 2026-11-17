package com.devforge.search.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.devforge.search.scan.ProjectScanner.ScanResult;
import com.devforge.search.scan.ProjectScanner.ScannedFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    @TempDir Path tmp;

    private Path root;

    private void write(String rel, String content) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(tmp.resolve("proj"));
        write("src/Main.java", "class Main {}");
        write("README.md", "# hi");
        write(".gitignore", "*.log\nbuild-output/\n!keep.log\n");
        write("debug.log", "x");
        write("keep.log", "x");
        write("build-output/x.txt", "x");
        write("node_modules/pkg/index.js", "x");
        write(".git/config", "x");
        write(".env", "SECRET=1");
        write("config/id_rsa", "key");
        write("target/classes/A.class", "x");
        write("image.png", "png");
    }

    private static List<String> paths(ScanResult r) {
        return r.files().stream().map(ScannedFile::relativePath).toList();
    }

    @Test
    void skipsVcsDependenciesBuildOutputSecretsAndIgnoredPaths() throws IOException {
        ScanResult r = ProjectScanner.scan(root, IgnoreRules.forProject(root, List.of()));
        assertEquals(List.of(".gitignore", "README.md", "image.png", "keep.log", "src/Main.java"), paths(r));
    }

    @Test
    void extraPatternsFromConfigurationApply() throws IOException {
        ScanResult r = ProjectScanner.scan(root, IgnoreRules.forProject(root, List.of("*.md", "src/")));
        assertEquals(List.of(".gitignore", "image.png", "keep.log"), paths(r));
    }

    @Test
    void recordsSizeExtensionAndModifiedTime() throws IOException {
        ScanResult r = ProjectScanner.scan(root, IgnoreRules.empty());
        ScannedFile main = r.files().stream().filter(f -> f.relativePath().equals("src/Main.java")).findFirst().orElseThrow();
        assertEquals(13, main.size());
        assertEquals("java", main.extension());
        assertEquals(root.resolve("src/Main.java"), main.absolutePath());
        assertTrue(main.modifiedAt().toEpochMilli() > 0);
    }

    @Test
    void symbolicLinksAreNotFollowed() throws IOException {
        try {
            Files.createSymbolicLink(root.resolve("link"), root.resolve("src"));
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }
        ScanResult r = ProjectScanner.scan(root, IgnoreRules.empty());
        assertFalse(paths(r).stream().anyMatch(p -> p.startsWith("link")));
    }

    @Test
    void extensionHelper() {
        assertEquals("java", FileClassifier.extensionOf("Main.JAVA"));
        assertEquals("", FileClassifier.extensionOf("Makefile"));
        assertEquals("", FileClassifier.extensionOf(".gitignore"));
        assertEquals("", FileClassifier.extensionOf("trailing."));
        assertEquals("gz", FileClassifier.extensionOf("a.tar.gz"));
    }
}
