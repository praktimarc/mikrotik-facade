package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WifiRemoteCapMapperTest {
    private final WifiRemoteCapMapper mapper = new WifiRemoteCapMapper();

    @Test
    void modernFieldsMapWithSourceAndRawPreserved() throws Exception {
        var cap = mapper.map(RouterOsRecord.of(Map.ofEntries(
                Map.entry(".id", "*1"),
                Map.entry("address", "192.0.2.10"),
                Map.entry("identity", "cap-1"),
                Map.entry("board-name", "C52iG-5HaxD2HaxD"),
                Map.entry("serial", "ABC123"),
                Map.entry("version", "7.20.4"),
                Map.entry("base-mac", "AA:BB:CC:DD:EE:FF"),
                Map.entry("common-name", "CAP-AABBCCDDEEFF"),
                Map.entry("state", "Ok"),
                Map.entry("connected-time", "1h2m3s"),
                Map.entry("uptime", "2d3h"),
                Map.entry("future-field", "kept"))),
                RemoteCapsSourceResolver.MODERN_SOURCE);

        assertEquals(RemoteCapsSourceResolver.MODERN_SOURCE, cap.source());
        assertEquals("C52iG-5HaxD2HaxD", cap.boardName().orElseThrow());
        assertEquals(Duration.ofHours(1).plusMinutes(2).plusSeconds(3),
                cap.connectedTime().orElseThrow());
        assertEquals("kept", cap.raw().find("future-field").orElseThrow());
    }

    @Test
    void legacyBoardAliasIsAccepted() throws Exception {
        var cap = mapper.map(
                RouterOsRecord.of(Map.of("identity", "cap-old", "board", "RBcAPGi-5acD2nD")),
                RemoteCapsSourceResolver.LEGACY_SOURCE);
        assertEquals("RBcAPGi-5acD2nD", cap.boardName().orElseThrow());
    }

    @Test
    void malformedPresentDurationIsDataError() {
        assertThrows(MikrotikDataException.class, () -> mapper.map(
                RouterOsRecord.of(Map.of("uptime", "not-a-duration")),
                RemoteCapsSourceResolver.MODERN_SOURCE));
    }
}
