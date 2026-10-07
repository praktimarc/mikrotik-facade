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
        assertTrue(rendered.contains("argumentKeys=[name, future-service-password]"));
        assertTrue(rendered.contains("queryKeys=[snmp-community]"));
        assertFalse(rendered.contains("raw-user"));
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
        assertFalse(ordinary.contains("ether1"));
        assertTrue(sensitive.contains("argumentKeys=[name]"));
        assertTrue(ordinary.contains("argumentKeys=[name]"));
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
    void reflectedUnknownArgumentAndQueryValuesAreAlwaysRemoved() {
        String rendered = CommandDiagnosticRenderer.sanitizeRouterOsMessage(
                "/future/service/set",
                "failed opaque-secret-value at 192.0.2.77",
                Map.of("future-opaque-field", "opaque-secret-value"),
                Map.of("address", "192.0.2.77"));

        assertFalse(rendered.contains("opaque-secret-value"));
        assertFalse(rendered.contains("192.0.2.77"));
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
