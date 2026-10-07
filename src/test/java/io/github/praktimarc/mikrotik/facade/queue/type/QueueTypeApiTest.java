package io.github.praktimarc.mikrotik.facade.queue.type;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.queue.type.internal.QueueTypeMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueueTypeApiTest {
    @Test
    void legacyNonDefaultPcqRowMapsFieldsAndKeepsRaw() throws Exception {
        var operation = QueueTypeApi.findOperation(
                RouterOsProperties.builder().set("default", "no").build(),
                new QueueTypeMapper());

        assertEquals("/queue/type/print", operation.command().path());
        assertEquals("no", operation.command().queries().get("default"));

        QueueType type = operation.map(CommandResult.ofRaw(List.of(Map.ofEntries(
                Map.entry(".id", "*6"),
                Map.entry("name", "PCQ_download"),
                Map.entry("kind", "pcq"),
                Map.entry("pcq-rate", "50000000"),
                Map.entry("pcq-limit", "50"),
                Map.entry("pcq-classifier", "dst-address"),
                Map.entry("pcq-total-limit", "20000"),
                Map.entry("pcq-burst-rate", "60000000"),
                Map.entry("pcq-burst-threshold", "45000000"),
                Map.entry("pcq-burst-time", "10s"),
                Map.entry("pcq-src-address-mask", "32"),
                Map.entry("pcq-dst-address-mask", "32"),
                Map.entry("pcq-src-address6-mask", "128"),
                Map.entry("pcq-dst-address6-mask", "128"),
                Map.entry("default", "false"),
                Map.entry("future-pcq-field", "kept"))), Map.of())).get(0);

        assertEquals("PCQ_download", type.name());
        assertEquals("pcq", type.kind().orElseThrow());
        assertEquals("50000000", type.pcqRate().orElseThrow());
        assertEquals(Duration.ofSeconds(10), type.pcqBurstTime().orElseThrow());
        assertFalse(type.defaultType().orElseThrow());
        assertEquals("kept", type.raw().find("future-pcq-field").orElseThrow());
    }

    @Test
    void unitDecoratedQueueLimitsRemainLossless() throws Exception {
        QueueType type = new QueueTypeMapper().map(
                io.github.praktimarc.mikrotik.facade.RouterOsRecord.of(
                        Map.of("name", "pcq-download-default",
                                "kind", "pcq",
                                "pcq-limit", "50KiB",
                                "pcq-total-limit", "2000KiB")));
        assertEquals("50KiB", type.pcqLimit().orElseThrow());
        assertEquals("2000KiB", type.pcqTotalLimit().orElseThrow());
    }

    @Test
    void nonPcqQueueMayOmitEveryPcqProperty() throws Exception {
        QueueType type = new QueueTypeMapper().map(
                io.github.praktimarc.mikrotik.facade.RouterOsRecord.of(
                        Map.of("name", "default-small", "kind", "pfifo", "default", "true")));
        assertEquals("pfifo", type.kind().orElseThrow());
        assertTrue(type.pcqRate().isEmpty());
        assertTrue(type.defaultType().orElseThrow());
    }

    @Test
    void malformedPresentStableMaskIsDataError() {
        assertThrows(io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException.class,
                () -> new QueueTypeMapper().map(
                        io.github.praktimarc.mikrotik.facade.RouterOsRecord.of(
                                Map.of("name", "bad", "pcq-src-address-mask", "not-a-number"))));
    }
}
