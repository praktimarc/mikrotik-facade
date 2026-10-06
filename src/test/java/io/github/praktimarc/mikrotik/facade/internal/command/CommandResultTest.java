package io.github.praktimarc.mikrotik.facade.internal.command;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandResultTest {

    @Test
    void recordsAndCompletionMetadataRemainSeparate() throws Exception {
        CommandResult result = CommandResult.ofRaw(
                List.of(
                        Map.of("address", "192.0.2.1"),
                        Map.of("address", "192.0.2.2")),
                Map.of("ret", "*A", "future", "kept"));

        assertEquals(2, result.records().size());
        assertEquals(
                "192.0.2.1",
                result.records().get(0).require("address"));
        assertEquals("*A", result.completion().require("ret"));
        assertEquals("kept", result.completion().require("future"));
        assertFalse(result.records().get(0).find("ret").isPresent());
    }

    @Test
    void resultCollectionsAreImmutableSnapshots() {
        RouterOsRecord record = RouterOsRecord.of(Map.of("x", "1"));
        CommandResult result = new CommandResult(
                List.of(record),
                RouterOsRecord.empty());

        assertThrows(
                UnsupportedOperationException.class,
                () -> result.records().add(record));
    }
}
