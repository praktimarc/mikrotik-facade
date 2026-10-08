package io.github.praktimarc.mikrotik.facade.dhcp.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.dhcp.DhcpLease;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhcpLeaseMapperTest {

    private final DhcpLeaseMapper mapper = new DhcpLeaseMapper();

    @Test
    void mapsExactLegacyLeaseFieldsAndFixesActiveClientIdRegression() throws Exception {
        DhcpLease lease = mapper.map(fixture("legacy-lease.properties"));

        assertEquals("192.0.2.44", lease.address().orElseThrow());
        assertEquals("02:00:00:00:01:44", lease.macAddress().orElseThrow());
        assertEquals("1:02:00:00:00:01:44", lease.clientId().orElseThrow());
        assertEquals("1:02:00:00:00:01:44", lease.activeClientId().orElseThrow());
        assertEquals("example-switch eth 0/14:1", lease.agentCircuitId().orElseThrow());
        assertEquals("ether1", lease.agentRemoteId().orElseThrow());
        assertEquals(330L, lease.expiresAfter().orElseThrow().getSeconds());
        assertEquals(12L, lease.lastSeen().orElseThrow().getSeconds());
        assertTrue(lease.dynamic().orElseThrow());
        assertFalse(lease.radius().orElseThrow());
        assertFalse(lease.blocked().orElseThrow());
        assertFalse(lease.disabled().orElseThrow());
        assertEquals("kept", lease.raw().find("future-property").orElseThrow());
        assertEquals("*D1", lease.raw().find(".id").orElseThrow());
    }

    @Test
    void activeAgentSchemaTakesPrecedenceOverLegacyAgentSchema() throws Exception {
        DhcpLease lease = mapper.map(fixture("active-agent-lease.properties"));
        assertEquals("ether2:1", lease.agentCircuitId().orElseThrow());
        assertEquals(
                "30:32:3a:30:30:3a:30:30:3a:30:31",
                lease.agentRemoteId().orElseThrow());
    }

    @Test
    void unknownCircuitRepresentationDoesNotUseNameHeuristicsAndRawRemainsComplete() throws Exception {
        DhcpLease lease = mapper.map(fixture("unknown-agent-lease.properties"));
        assertTrue(lease.agentCircuitId().isEmpty());
        assertTrue(lease.agentRemoteId().isEmpty());
        assertEquals(
                "must-not-be-guessed",
                lease.raw().find("lease-agent-circuit-id").orElseThrow());
        assertEquals("still-kept", lease.raw().find("future-property").orElseThrow());
    }

    @Test
    void malformedKnownBooleanOrDurationIsDataError() {
        assertThrows(
                MikrotikDataException.class,
                () -> mapper.map(RouterOsRecord.of(Map.of("dynamic", "maybe"))));
        assertThrows(
                MikrotikDataException.class,
                () -> mapper.map(RouterOsRecord.of(Map.of("expires-after", "not-a-duration"))));
    }

    private static RouterOsRecord fixture(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = DhcpLeaseMapperTest.class.getResourceAsStream("/fixtures/dhcp/" + name)) {
            if (input == null) {
                throw new IOException("Missing fixture: " + name);
            }
            properties.load(input);
        }
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        properties.stringPropertyNames().stream()
                .sorted()
                .forEach(key -> ordered.put(key, properties.getProperty(key)));
        return RouterOsRecord.of(ordered);
    }
}
