package io.github.praktimarc.mikrotik.facade.firewall.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.firewall.FirewallRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FirewallRuleMapperTest {
    private final FirewallRuleMapper mapper = new FirewallRuleMapper();

    @Test
    void mapsProvenLegacyFieldsAndPreservesUnknownRawProperties() throws Exception {
        RouterOsRecord raw = RouterOsRecord.of(Map.ofEntries(
                Map.entry(".id", "*A"),
                Map.entry("chain", "forward"),
                Map.entry("protocol", "tcp"),
                Map.entry("src-address", "192.0.2.1"),
                Map.entry("dst-address", "198.51.100.4"),
                Map.entry("src-port", "1000-2000"),
                Map.entry("dst-port", "443"),
                Map.entry("in-interface", "ether1"),
                Map.entry("out-interface", "ether2"),
                Map.entry("in-interface-list", "LAN"),
                Map.entry("out-interface-list", "WAN"),
                Map.entry("bytes", "1234"),
                Map.entry("packets", "12"),
                Map.entry("invalid", "false"),
                Map.entry("dynamic", "true"),
                Map.entry("disabled", "false"),
                Map.entry("comment", "example"),
                Map.entry("future-property", "kept")));
        FirewallRule rule = mapper.map(raw);
        assertEquals("*A", rule.id().orElseThrow());
        assertEquals("forward", rule.chain().orElseThrow());
        assertEquals(1234L, rule.bytes().orElseThrow());
        assertEquals(12L, rule.packets().orElseThrow());
        assertTrue(rule.dynamic().orElseThrow());
        assertFalse(rule.disabled().orElseThrow());
        assertEquals("kept", rule.raw().find("future-property").orElseThrow());
    }

    @Test
    void malformedTypedCounterIsDataError() {
        assertThrows(MikrotikDataException.class,
                () -> mapper.map(RouterOsRecord.of(Map.of("bytes", "not-a-number"))));
    }

    @Test
    void absentOptionalFieldsRemainEmpty() throws Exception {
        FirewallRule rule = mapper.map(RouterOsRecord.of(Map.of("comment", "")));
        assertTrue(rule.id().isEmpty());
        assertTrue(rule.bytes().isEmpty());
        assertEquals("", rule.comment().orElseThrow());
    }
}
