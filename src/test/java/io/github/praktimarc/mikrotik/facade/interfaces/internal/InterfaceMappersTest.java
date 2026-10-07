package io.github.praktimarc.mikrotik.facade.interfaces.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceMappersTest {
    @Test
    void genericInterfaceMapsStableFieldsAndKeepsUnknownProperties() throws Exception {
        var mapped = new InterfaceInfoMapper().map(RouterOsRecord.of(Map.ofEntries(
                Map.entry(".id", "*1"),
                Map.entry("name", "ether1"),
                Map.entry("default-name", "ether1"),
                Map.entry("type", "ether"),
                Map.entry("mtu", "1500"),
                Map.entry("actual-mtu", "1500"),
                Map.entry("l2mtu", "1598"),
                Map.entry("max-l2mtu", "10218"),
                Map.entry("mac-address", "00:11:22:33:44:55"),
                Map.entry("running", "true"),
                Map.entry("dynamic", "false"),
                Map.entry("disabled", "false"),
                Map.entry("future-counter", "17"))));

        assertEquals("ether1", mapped.name());
        assertEquals(1598L, mapped.l2Mtu().orElseThrow());
        assertTrue(mapped.running().orElseThrow());
        assertEquals("17", mapped.raw().find("future-counter").orElseThrow());
    }

    @Test
    void hardwareDependentInterfaceCountersMayBeAbsent() throws Exception {
        var mapped = new InterfaceInfoMapper().map(RouterOsRecord.of(Map.of("name", "bridge1")));
        assertTrue(mapped.l2Mtu().isEmpty());
        assertTrue(mapped.maxL2Mtu().isEmpty());
        assertTrue(mapped.macAddress().isEmpty());
    }

    @Test
    void addressMapperSupportsParityCommentLookupWithoutEmbeddingCmtsPolicy() throws Exception {
        var mapped = new InterfaceAddressMapper().map(RouterOsRecord.of(Map.of(
                "address", "192.0.2.5/30",
                "network", "192.0.2.4",
                "interface", "vlan123",
                "comment", "cmts-internal",
                "dynamic", "no")));
        assertEquals("192.0.2.5/30", mapped.address());
        assertEquals("cmts-internal", mapped.comment().orElseThrow());
        assertFalse(mapped.dynamic().orElseThrow());
    }

    @Test
    void monitorMapperTreatsDriverDependentCountersAsOptionalAndKeepsRaw() throws Exception {
        var mapped = new InterfaceMonitorMapper().map(RouterOsRecord.of(Map.of(
                "name", "ether1",
                "rx-bits-per-second", "12345",
                "tx-bits-per-second", "54321",
                "tx-queue-drops-per-second", "2",
                "driver-extra", "kept")));
        assertEquals(12345L, mapped.rxBitsPerSecond().orElseThrow());
        assertEquals(54321L, mapped.txBitsPerSecond().orElseThrow());
        assertTrue(mapped.rxErrorsPerSecond().isEmpty());
        assertEquals("kept", mapped.raw().find("driver-extra").orElseThrow());
    }

    @Test
    void malformedPresentTypedCounterIsDataError() {
        assertThrows(MikrotikDataException.class, () -> new InterfaceMonitorMapper().map(
                RouterOsRecord.of(Map.of("rx-bits-per-second", "not-a-number"))));
    }
}
