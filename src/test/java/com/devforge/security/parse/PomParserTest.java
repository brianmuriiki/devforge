package com.devforge.security.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devforge.security.model.Dependency;
import com.devforge.security.model.Ecosystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomParserTest {

    @TempDir Path tmp;

    private List<Dependency> parse(String xml) throws IOException {
        Path pom = tmp.resolve("pom.xml");
        Files.writeString(pom, xml);
        return PomParser.parse(pom);
    }

    @Test
    void readsDirectDependenciesAndResolvesProperties() throws IOException {
        List<Dependency> deps = parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.parent</groupId><artifactId>p</artifactId><version>9.9</version></parent>
                  <artifactId>app</artifactId>
                  <properties><junit.version>5.10.2</junit.version><nested>${junit.version}</nested></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>x</groupId><artifactId>managed-only</artifactId><version>1.0</version></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>
                      <version>${junit.version}</version><scope>test</scope></dependency>
                    <dependency><groupId>com.acme</groupId><artifactId>lib</artifactId><version>${nested}</version></dependency>
                    <dependency><groupId>${project.groupId}</groupId><artifactId>self</artifactId><version>${project.version}</version></dependency>
                    <dependency><groupId>g</groupId><artifactId>inherited</artifactId></dependency>
                    <dependency><groupId>g</groupId><artifactId>ranged</artifactId><version>[1.0,2.0)</version></dependency>
                    <dependency><groupId>g</groupId><artifactId>unknown-prop</artifactId><version>${nope}</version></dependency>
                  </dependencies>
                </project>""");

        assertEquals(6, deps.size());
        Dependency junit = deps.get(0);
        assertEquals("org.junit.jupiter:junit-jupiter", junit.name());
        assertEquals("5.10.2", junit.resolvedVersion());
        assertEquals("${junit.version}", junit.declaredVersion());
        assertEquals("test", junit.scope());
        assertEquals(Ecosystem.MAVEN, junit.ecosystem());
        assertEquals("5.10.2", deps.get(1).resolvedVersion());
        assertEquals("org.parent:self", deps.get(2).name());
        assertEquals("9.9", deps.get(2).resolvedVersion());
        assertNull(deps.get(3).resolvedVersion());
        assertEquals("compile", deps.get(3).scope());
        assertNull(deps.get(4).resolvedVersion());
        assertNull(deps.get(5).resolvedVersion());
    }

    @Test
    void noDependenciesYieldsEmptyList() throws IOException {
        assertTrue(parse("<project><modelVersion>4.0.0</modelVersion></project>").isEmpty());
    }

    @Test
    void doctypeDeclarationsAreRejectedToBlockXxe() {
        assertThrows(ManifestException.class, () -> parse("""
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <project><dependencies><dependency><groupId>&xxe;</groupId><artifactId>a</artifactId></dependency></dependencies></project>"""));
    }

    @Test
    void malformedXmlIsReportedCleanly() {
        ManifestException e = assertThrows(ManifestException.class, () -> parse("<project><unclosed></project>"));
        assertTrue(e.getMessage().contains("pom.xml"));
    }
}
