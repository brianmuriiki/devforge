package com.devforge.security.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.devforge.security.parse.ManifestFinder.Manifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestFinderTest {

    @TempDir Path tmp;

    private void touch(String rel) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "x");
    }

    @Test
    void findsSupportedManifestsAndSkipsDependencyAndBuildDirectories() throws IOException {
        touch("pom.xml");
        touch("web/package.json");
        touch("api/requirements.txt");
        touch("api/pyproject.toml");
        touch("node_modules/dep/package.json");
        touch("target/pom.xml");
        touch(".git/pom.xml");
        touch("venv/requirements.txt");
        touch("notes.txt");

        List<String> found = ManifestFinder.find(tmp).stream().map(Manifest::relativePath).toList();
        assertEquals(List.of("api/pyproject.toml", "api/requirements.txt", "pom.xml", "web/package.json"), found);
    }

    @Test
    void mapsFileNamesToEcosystems() throws IOException {
        touch("pom.xml");
        touch("package.json");
        touch("requirements.txt");
        List<String> ecosystems = ManifestFinder.find(tmp).stream().map(m -> m.ecosystem().name()).toList();
        assertEquals(List.of("NPM", "MAVEN", "PYPI"), ecosystems);
    }
}
