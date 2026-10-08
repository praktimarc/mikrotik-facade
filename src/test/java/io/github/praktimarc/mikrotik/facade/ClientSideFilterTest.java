package io.github.praktimarc.mikrotik.facade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientSideFilterTest {

    @Test
    void regexUsesClientSideFindSemanticsAndHandlesVeryLongValuesWithoutTruncation() {
        String longValue = "prefix-" + "x".repeat(50_000) + "-TARGET-END";
        RouterOsRecord record = RouterOsRecord.of(Map.of(
                "comment", longValue,
                "interface", "cap-17-client"));

        ClientSideFilter tail = ClientSideFilter.regex("comment", "TARGET-END$");
        ClientSideFilter interfaceFilter =
                ClientSideFilter.regex("interface", "^cap-17-");

        assertTrue(tail.matches(record));
        assertTrue(interfaceFilter.matches(record));
        assertEquals(longValue, record.find("comment").orElseThrow());
    }

    @Test
    void missingPropertyDoesNotMatchAndBooleanCompositionWorks() {
        RouterOsRecord a = RouterOsRecord.of(Map.of(
                "name", "cap-alpha",
                "state", "up"));
        RouterOsRecord b = RouterOsRecord.of(Map.of(
                "name", "client-beta",
                "state", "down"));

        ClientSideFilter filter = ClientSideFilter.regex("name", "^cap-")
                .and(ClientSideFilter.regex("state", "^up$"));

        assertEquals(List.of(a), filter.apply(List.of(a, b)));
        assertFalse(ClientSideFilter.regex("missing", ".*").matches(a));
        assertTrue(ClientSideFilter.regex("name", "beta")
                .or(ClientSideFilter.regex("name", "alpha"))
                .matches(a));
    }

    @Test
    void filterToStringDoesNotExposeRegexText() {
        ClientSideFilter filter = ClientSideFilter.regex(
                "comment",
                "secret-pattern-value");

        assertTrue(filter.toString().contains("comment"));
        assertFalse(filter.toString().contains("secret-pattern-value"));
    }
}
