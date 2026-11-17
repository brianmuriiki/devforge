package com.devforge.security.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.devforge.security.model.Dependency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PythonParserTest {

    @TempDir Path tmp;

    private Dependency byName(List<Dependency> deps, String name) {
        return deps.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void parsesRequirementsTxt() throws IOException {
        Files.writeString(tmp.resolve("requirements.txt"), """
                # comment
                Django==4.2.1  # trailing comment
                requests[security]>=2.28,<3
                Flask_SQLAlchemy == 3.0.5 ; python_version >= "3.8"
                numpy==1.26.0 --hash=sha256:abc
                -r other.txt
                git+https://github.com/x/y.git#egg=y
                ./local/path
                wildcard==1.*
                bare-name
                """);
        List<Dependency> deps = PythonParser.parse(tmp.resolve("requirements.txt"));

        assertEquals(6, deps.size());
        assertEquals("4.2.1", byName(deps, "django").resolvedVersion());
        assertNull(byName(deps, "requests").resolvedVersion());
        assertEquals(">=2.28,<3", byName(deps, "requests").declaredVersion());
        assertEquals("3.0.5", byName(deps, "flask-sqlalchemy").resolvedVersion());
        assertEquals("1.26.0", byName(deps, "numpy").resolvedVersion());
        assertNull(byName(deps, "wildcard").resolvedVersion());
        assertNull(byName(deps, "bare-name").resolvedVersion());
    }

    @Test
    void parsesPyprojectProjectDependenciesAcrossLines() throws IOException {
        Files.writeString(tmp.resolve("pyproject.toml"), """
                [build-system]
                requires = ["setuptools"]

                [project]
                name = "demo"
                dependencies = [
                    "requests==2.31.0",
                    "click>=8.0",   # comment
                    "rich[jupyter]==13.7.0",
                ]

                [project.optional-dependencies]
                dev = ["pytest==8.0.0", "black"]
                """);
        List<Dependency> deps = PythonParser.parse(tmp.resolve("pyproject.toml"));

        assertEquals(5, deps.size());
        assertEquals("2.31.0", byName(deps, "requests").resolvedVersion());
        assertNull(byName(deps, "click").resolvedVersion());
        assertEquals("13.7.0", byName(deps, "rich").resolvedVersion());
        assertEquals("8.0.0", byName(deps, "pytest").resolvedVersion());
        assertEquals("optional", byName(deps, "pytest").scope());
        assertNull(byName(deps, "black").resolvedVersion());
    }

    @Test
    void parsesPoetryTables() throws IOException {
        Files.writeString(tmp.resolve("pyproject.toml"), """
                [tool.poetry.dependencies]
                python = "^3.11"
                fastapi = "0.110.0"
                uvicorn = { version = "^0.29.0", extras = ["standard"] }
                pinned = "==1.2.3"
                local = { path = "../local" }

                [tool.poetry.group.dev.dependencies]
                pytest = "7.4.0"
                """);
        List<Dependency> deps = PythonParser.parse(tmp.resolve("pyproject.toml"));

        assertEquals(5, deps.size());
        assertEquals("0.110.0", byName(deps, "fastapi").resolvedVersion());
        assertNull(byName(deps, "uvicorn").resolvedVersion());
        assertEquals("^0.29.0", byName(deps, "uvicorn").declaredVersion());
        assertEquals("1.2.3", byName(deps, "pinned").resolvedVersion());
        assertNull(byName(deps, "local").resolvedVersion());
        assertEquals("7.4.0", byName(deps, "pytest").resolvedVersion());
    }

    @Test
    void normalizesPackageNames() {
        assertEquals("flask-sqlalchemy", PythonParser.normalize("Flask_SQLAlchemy"));
        assertEquals("zope-interface", PythonParser.normalize("zope.interface"));
    }
}
