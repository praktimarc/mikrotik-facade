package io.github.praktimarc.mikrotik.facade;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the developer-maintained documentation inventory against exported entry points.
 * The inventory is not a substitute for reviewing examples or domain getters.
 */
class PublicApiDocumentationCoverageTest {

    private static final String PREFIX = "io.github.praktimarc.mikrotik.facade.";
    private static final Path INDEX = Path.of("docs/api-coverage.tsv");
    private static final Path SOURCE_ROOT =
            Path.of("src/main/java/io/github/praktimarc/mikrotik/facade");

    @Test
    void documentationInventoryTracksActualPublicApiMethodsAndGuides() throws Exception {
        List<String> lines = Files.readAllLines(INDEX);
        Map<String, Set<String>> indexed = new HashMap<>();

        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            assertEquals(3, fields.length, "Expected class, methods, guide: " + line);

            String className = fields[0];
            assertNull(indexed.get(className), "Duplicate inventory entry: " + className);
            Set<String> expected = Arrays.stream(fields[1].split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            assertFalse(expected.isEmpty(), "No methods inventoried: " + className);

            Path guide = Path.of(fields[2]).normalize();
            assertTrue(guide.startsWith(Path.of("docs")), "Guide must be a local docs/ file: " + guide);
            assertTrue(Files.isRegularFile(guide), "Missing documentation guide: " + guide);

            Class<?> api = Class.forName(PREFIX + className);
            Set<String> actual = Arrays.stream(api.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .filter(m -> !m.isBridge() && !m.isSynthetic())
                    .map(Method::getName)
                    .filter(name -> !Set.of("toString", "equals", "hashCode").contains(name))
                    .collect(Collectors.toSet());
            assertEquals(expected, actual, "Public API changed without updating docs inventory: " + className);
            indexed.put(className, actual);
        }

        // Catch *new* public facade classes, not just method changes in existing classes.
        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            Set<String> publicFacadeEntryPoints = sources
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("Api.java"))
                    .filter(p -> !p.toString().contains("/internal/"))
                    .map(SOURCE_ROOT::relativize)
                    .map(p -> p.toString().replace('\\', '.').replace('/', '.'))
                    .map(s -> s.substring(0, s.length() - ".java".length()))
                    .collect(Collectors.toSet());

            assertTrue(indexed.keySet().containsAll(publicFacadeEntryPoints),
                    "New public API classes need documentation inventory entries: "
                            + difference(publicFacadeEntryPoints, indexed.keySet()));
        }

        assertTrue(indexed.containsKey("MikrotikRtrApiBuilder"));
        assertTrue(indexed.containsKey("raw.RawCommandBuilder$Async"));
    }

    private static Set<String> difference(Set<String> values, Set<String> accountedFor) {
        Set<String> missing = new HashSet<>(values);
        missing.removeAll(accountedFor);
        return missing;
    }
}
