package io.github.praktimarc.mikrotik.facade.files;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.files.internal.FileOperations;
import io.github.praktimarc.mikrotik.facade.files.internal.RouterFileMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RouterFileApiTest {
    @Test
    void metadataMappingIsTypedAndRawComplete() throws Exception {
        RouterFile file = new RouterFileMapper().map(
                io.github.praktimarc.mikrotik.facade.RouterOsRecord.of(Map.ofEntries(
                        Map.entry(".id", "*1"),
                        Map.entry("name", "flash/test.bin"),
                        Map.entry("type", "file"),
                        Map.entry("size", "70013"),
                        Map.entry("creation-time", "2026-10-07 12:00:00"),
                        Map.entry("last-modified", "2026-10-07 12:01:00"),
                        Map.entry("package", "routeros"),
                        Map.entry("package-version", "7.20.4"),
                        Map.entry("future-field", "kept"))));

        assertEquals("flash/test.bin", file.name());
        assertEquals(70013L, file.sizeBytes().orElseThrow());
        assertEquals("2026-10-07 12:01:00", file.lastModified().orElseThrow());
        assertEquals("kept", file.raw().find("future-field").orElseThrow());
    }

    @Test
    void listAndFindUseNormalFilePrintOperation() {
        var all = FileOperations.find(RouterOsProperties.builder().build(), new RouterFileMapper());
        var named = FileOperations.findByName("flash/test.bin", new RouterFileMapper());

        assertEquals("/file/print", all.command().path());
        assertEquals("/file/print", named.command().path());
        assertEquals("flash/test.bin", named.command().queries().get("name"));
    }

    @Test
    void exactNameAbsenceIsEmptyAndDuplicatesAreDataError() throws Exception {
        var operation = FileOperations.findByName("flash/test.bin", new RouterFileMapper());

        assertTrue(operation.map(CommandResult.ofRaw(List.of(), Map.of())).isEmpty());

        assertThrows(MikrotikDataException.class, () -> operation.map(CommandResult.ofRaw(
                List.of(
                        Map.of("name", "flash/test.bin"),
                        Map.of("name", "flash/test.bin")),
                Map.of())));
    }

    @Test
    void malformedPresentSizeIsDataError() {
        assertThrows(MikrotikDataException.class, () -> new RouterFileMapper().map(
                io.github.praktimarc.mikrotik.facade.RouterOsRecord.of(
                        Map.of("name", "bad.bin", "size", "not-a-number"))));
    }
}
