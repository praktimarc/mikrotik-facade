package io.github.praktimarc.mikrotik.facade;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LoggingDependencyTest {
    @Test
    void productionPomUsesSlf4jApiWithoutForcingABackend() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>slf4j-api</artifactId>"));
        assertFalse(pom.contains("<artifactId>slf4j-simple</artifactId>"));
        assertFalse(pom.contains("<artifactId>logback-classic</artifactId>"));
        assertFalse(pom.contains("<artifactId>log4j-slf4j"));
        assertFalse(pom.contains("<artifactId>log4j-core</artifactId>"));
    }
}
