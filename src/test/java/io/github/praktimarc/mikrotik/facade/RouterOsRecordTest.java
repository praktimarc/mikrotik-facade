package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterOsRecordTest {

    @Test
    void copiesInputAndExposesUnmodifiableProperties() throws Exception {
        Map<String, String> source = new LinkedHashMap<>();
        source.put(".id", "*1");
        source.put("unknown-future-property", "  exact value = preserved  ");

        RouterOsRecord record = RouterOsRecord.of(source);
        source.put(".id", "changed");

        assertEquals("*1", record.require(".id"));
        assertEquals("  exact value = preserved  ", record.require("unknown-future-property"));
        assertThrows(UnsupportedOperationException.class,
                () -> record.asMap().put("new-field", "new-value"));
    }

    @Test
    void optionalAndRequiredAccessHaveDifferentAbsenceSemantics() throws Exception {
        RouterOsRecord record = RouterOsRecord.of(Map.of("present", "value"));

        assertEquals("value", record.find("present").orElseThrow());
        assertTrue(record.find("missing").isEmpty());
        assertThrows(MikrotikDataException.class, () -> record.require("missing"));
    }

    @Test
    void parsesTypedValuesWithoutHidingMalformedData() throws Exception {
        RouterOsRecord record = RouterOsRecord.of(Map.of(
                "counter", "42",
                "enabled", "true",
                "disabled", "no",
                "uptime", "2d20h12m20s",
                "rtt", "417us",
                "activation-timeout", "00:00:10"));

        assertEquals(42L, record.getLong("counter").orElseThrow());
        assertTrue(record.getBoolean("enabled").orElseThrow());
        assertFalse(record.getBoolean("disabled").orElseThrow());
        assertEquals(Duration.ofDays(2).plusHours(20).plusMinutes(12).plusSeconds(20),
                record.getDuration("uptime").orElseThrow());
        assertEquals(Duration.ofNanos(417_000), record.getDuration("rtt").orElseThrow());
        assertEquals(Duration.ofSeconds(10), record.getDuration("activation-timeout").orElseThrow());
    }

    @Test
    void missingTypedValueIsEmptyButMalformedTypedValueIsDataError() throws Exception {
        RouterOsRecord missing = RouterOsRecord.empty();
        RouterOsRecord malformed = RouterOsRecord.of(Map.of(
                "counter", "forty-two",
                "enabled", "maybe",
                "duration", "forever"));

        assertTrue(missing.getLong("counter").isEmpty());
        assertTrue(missing.getBoolean("enabled").isEmpty());
        assertTrue(missing.getDuration("duration").isEmpty());

        assertThrows(MikrotikDataException.class, () -> malformed.getLong("counter"));
        assertThrows(MikrotikDataException.class, () -> malformed.getBoolean("enabled"));
        assertThrows(MikrotikDataException.class, () -> malformed.getDuration("duration"));
    }

    @Test
    void conversionErrorsDoNotExposeRawPropertyValues() {
        String sensitiveValue = "super-secret-value";
        RouterOsRecord record = RouterOsRecord.of(Map.of("password", sensitiveValue));

        MikrotikDataException exception = assertThrows(
                MikrotikDataException.class, () -> record.getLong("password"));

        assertFalse(exception.getMessage().contains(sensitiveValue));
    }

    @Test
    void requiredTypedAccessRejectsMissingValues() {
        RouterOsRecord record = RouterOsRecord.empty();

        assertThrows(MikrotikDataException.class, () -> record.requireLong("counter"));
        assertThrows(MikrotikDataException.class, () -> record.requireBoolean("enabled"));
        assertThrows(MikrotikDataException.class, () -> record.requireDuration("uptime"));
    }
}
