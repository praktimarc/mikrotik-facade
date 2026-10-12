package io.github.praktimarc.mikrotik.facade.internal.compat;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EqualityQueryVerifierTest {
    @Test
    void matchesAllActualValuesWithoutCaseSensitivity() {
        RouterOsProperties criteria = RouterOsProperties.builder()
                .set("list", "Allowed")
                .set("address", "192.0.2.10")
                .build();

        assertTrue(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("list", "ALLOWED", "address", "192.0.2.10")),
                criteria));
        assertFalse(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("list", "ALLOWED", "address", "192.0.2.11")),
                criteria));
        assertFalse(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("list", "ALLOWED")),
                criteria));
    }

    @Test
    void actualAndRequestedBooleanRepresentationsAreEquivalent() {
        assertTrue(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("default", "false")),
                Map.of("default", "NO")));
        assertTrue(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("disabled", "YES")),
                Map.of("disabled", "true")));
        assertFalse(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("disabled", "false")),
                Map.of("disabled", "yes")));
    }

    @Test
    void noCriteriaMatchEveryRecordAndValuesAreNotTrimmedOrFuzzilyMatched() {
        assertTrue(EqualityQueryVerifier.matches(RouterOsRecord.empty(), Map.of()));
        assertFalse(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("comment", "important ")),
                Map.of("comment", "important")));
        assertFalse(EqualityQueryVerifier.matches(
                RouterOsRecord.of(Map.of("comment", "more important")),
                Map.of("comment", "important")));
        assertFalse(EqualityQueryVerifier.matches(RouterOsRecord.empty(),
                Map.of("comment", "")));
    }
}
