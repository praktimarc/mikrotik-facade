package io.github.praktimarc.mikrotik.facade.internal.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDiagnosticRendererTest {

    @Test
    void rendersArbitraryRawCommandThroughCentralRedaction() {
        LinkedHashMap<String, String> arguments = new LinkedHashMap<>();
        arguments.put("name", "raw-user");
        arguments.put("future-service-password", "TOP-SECRET");
        Map<String, String> queries = Map.of("snmp-community", "COMMUNITY");

        String rendered = CommandDiagnosticRenderer.render(
                "/future/service/add =password=INLINE",
                arguments,
                queries);

        assertTrue(rendered.contains("path=/future/service/add"));
        assertTrue(rendered.contains("name=raw-user"));
        assertTrue(rendered.contains("future-service-password=<redacted>"));
        assertTrue(rendered.contains("snmp-community=<redacted>"));
        assertFalse(rendered.contains("TOP-SECRET"));
        assertFalse(rendered.contains("COMMUNITY"));
        assertFalse(rendered.contains("INLINE"));
    }

    @Test
    void snmpCommunityNameIsRedactedOnlyInCommunityContext() {
        String sensitive = CommandDiagnosticRenderer.render(
                "/snmp/community/set",
                Map.of("name", "private-community"),
                Map.of());
        String ordinary = CommandDiagnosticRenderer.render(
                "/interface/set",
                Map.of("name", "ether1"),
                Map.of());

        assertFalse(sensitive.contains("private-community"));
        assertTrue(sensitive.contains("name=<redacted>"));
        assertTrue(ordinary.contains("name=ether1"));
    }

    @Test
    void snmpCommunityNameIsRemovedFromRouterOsMessages() {
        String rendered = CommandDiagnosticRenderer.sanitizeRouterOsMessage(
                "/snmp/community/set",
                "invalid community private-community",
                Map.of("name", "private-community"),
                Map.of());
        assertFalse(rendered.contains("private-community"));
        assertTrue(rendered.contains("<redacted>"));
    }

    @Test
    void commandPathNeverIncludesArgumentsOrQueries() {
        assertEquals("/ip/user/add",
                CommandDiagnosticRenderer.safeCommandPath("/ip/user/add =password=secret"));
        assertEquals("/ip/address/print",
                CommandDiagnosticRenderer.safeCommandPath("/ip/address/print?address=192.0.2.1"));
    }
}
