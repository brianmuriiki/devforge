package com.devforge.security.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devforge.security.model.Dependency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpmParserTest {

    @TempDir Path tmp;

    private Dependency byName(List<Dependency> deps, String name) {
        return deps.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void usesLockfileForResolvedVersionsAndExactPinsOtherwise() throws IOException {
        Files.writeString(tmp.resolve("package.json"), """
                { "name": "app",
                  "dependencies": { "express": "^4.18.0", "lodash": "4.17.21", "@scope/pkg": "~1.2.0", "local": "file:../local",
                                    "nolock": "^2.0.0" },
                  "devDependencies": { "jest": "^29.0.0" } }""");
        Files.writeString(tmp.resolve("package-lock.json"), """
                { "lockfileVersion": 3, "packages": {
                    "": { "name": "app" },
                    "node_modules/express": { "version": "4.18.2" },
                    "node_modules/@scope/pkg": { "version": "1.2.9" },
                    "node_modules/jest": { "version": "29.7.0" },
                    "node_modules/express/node_modules/nested": { "version": "0.0.1" } } }""");

        List<Dependency> deps = NpmParser.parse(tmp.resolve("package.json"));
        assertEquals(7, deps.size());
        assertEquals("4.18.2", byName(deps, "express").resolvedVersion());
        assertEquals("^4.18.0", byName(deps, "express").declaredVersion());
        assertEquals("4.17.21", byName(deps, "lodash").resolvedVersion()); // exact pin
        assertEquals("1.2.9", byName(deps, "@scope/pkg").resolvedVersion());
        assertEquals("29.7.0", byName(deps, "jest").resolvedVersion());
        assertEquals("devDependencies", byName(deps, "jest").scope());
        assertNull(byName(deps, "local").resolvedVersion());
        assertNull(byName(deps, "nolock").resolvedVersion());
        Dependency nested = byName(deps, "nested");
        assertEquals("0.0.1", nested.resolvedVersion());
        assertEquals("transitive", nested.scope());
    }

    @Test
    void supportsLegacyLockfileFormat() throws IOException {
        Files.writeString(tmp.resolve("package.json"), "{ \"dependencies\": { \"left-pad\": \"^1.0.0\" } }");
        Files.writeString(tmp.resolve("package-lock.json"),
                "{ \"lockfileVersion\": 1, \"dependencies\": { \"left-pad\": { \"version\": \"1.3.0\" } } }");
        assertEquals("1.3.0", NpmParser.parse(tmp.resolve("package.json")).get(0).resolvedVersion());
    }

    @Test
    void includesNestedLegacyLockfileDependencies() throws IOException {
        Files.writeString(tmp.resolve("package.json"), "{ \"dependencies\": { \"a\": \"^1.0.0\" } }");
        Files.writeString(tmp.resolve("package-lock.json"), """
                { "lockfileVersion": 1, "dependencies": {
                  "a": { "version": "1.0.0", "dependencies": {
                    "b": { "version": "2.0.0" }
                  } }
                } }""");
        List<Dependency> dependencies = NpmParser.parse(tmp.resolve("package.json"));
        assertEquals(2, dependencies.size());
        assertEquals("1.0.0", byName(dependencies, "a").resolvedVersion());
        assertEquals("2.0.0", byName(dependencies, "b").resolvedVersion());
        assertEquals("transitive", byName(dependencies, "b").scope());
    }

    @Test
    void damagedLockfileJustLeavesVersionsUnresolved() throws IOException {
        Files.writeString(tmp.resolve("package.json"), "{ \"dependencies\": { \"a\": \"^1.0.0\" } }");
        Files.writeString(tmp.resolve("package-lock.json"), "{ broken");
        assertNull(NpmParser.parse(tmp.resolve("package.json")).get(0).resolvedVersion());
    }

    @Test
    void invalidPackageJsonIsReported() throws IOException {
        Files.writeString(tmp.resolve("package.json"), "{ nope");
        assertThrows(ManifestException.class, () -> NpmParser.parse(tmp.resolve("package.json")));
        Files.writeString(tmp.resolve("package.json"), "[1]");
        assertThrows(ManifestException.class, () -> NpmParser.parse(tmp.resolve("package.json")));
    }
}
