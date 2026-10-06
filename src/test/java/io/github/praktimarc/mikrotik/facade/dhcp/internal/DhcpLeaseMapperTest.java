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

        assertEquals("192.168.250.44", lease.address().orElseThrow());
        assertEquals("2C:C8:1B:C6:5C:C5", lease.macAddress().orElseThrow());
        assertEquals("1:2c:c8:1b:c6:5c:c5", lease.clientId().orElseThrow());
        assertEquals("1:2c:c8:1b:c6:5c:c5", lease.activeClientId().orElseThrow());
        assertEquals("sw-fr-41-131 eth 0/14:1", lease.agentCircuitId().orElseThrow());
        assertEquals("ether1", lease.agentRemoteId().orElseThrow());
        assertEquals(579L, lease.expiresAfter().orElseThrow().getSeconds());
        assertEquals(21L, lease.lastSeen().orElseThrow().getSeconds());
        assertTrue(lease.dynamic().orElseThrow());
        assertFalse(lease.radius().orElseThrow());
        assertFalse(lease.blocked().orElseThrow());
        assertFalse(lease.disabled().orElseThrow());
        assertEquals("kept", lease.raw().find("future-property").orElseThrow());
        assertEquals("*64", lease.raw().find(".id").orElseThrow());
    }

    @Test
    void activeAgentSchemaTakesPrecedenceOverLegacyAgentSchema() throws Exception {
        DhcpLease lease = mapper.map(fixture("active-agent-lease.properties"));
        assertEquals("ether2:1", lease.agentCircuitId().orElseThrow());
        assertEquals(
                "31:38:3a:66:64:3a:37:34:3a:61:65:3a:37:66:3a:38:37",
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
