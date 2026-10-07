package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.wifi.WifiRegistration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class WifiRegistrationMapperTest {
    private final WifiRegistrationMapper mapper = new WifiRegistrationMapper();

    @Test
    void legacyRxSignalAndModernSignalNormalizeToSameTypedConcept() throws Exception {
        WifiRegistration legacy = mapper.map(
                fixture("/fixtures/wifi/legacy-registration.properties"),
                RegisteredClientsSourceResolver.LEGACY_SOURCE);
        WifiRegistration modern = mapper.map(
                fixture("/fixtures/wifi/modern-registration.properties"),
                RegisteredClientsSourceResolver.MODERN_SOURCE);

        assertEquals(-53L, legacy.signalDbm().orElseThrow());
        assertEquals(-54L, modern.signalDbm().orElseThrow());
        assertEquals("4E:D8:49:B8:73:4E", legacy.macAddress().orElseThrow());
        assertEquals("4E:D8:49:B8:73:4E", modern.macAddress().orElseThrow());
        assertTrue(modern.authorized().orElseThrow());
    }

    @Test
    void sourceSpecificSignalFieldWinsWhenBothKnownAliasesArePresent() throws Exception {
        RouterOsRecord row = RouterOsRecord.of(Map.of(
                "signal", "-40",
                "rx-signal", "-60"));

        assertEquals(-60L, mapper.map(row, RegisteredClientsSourceResolver.LEGACY_SOURCE)
                .signalDbm().orElseThrow());
        assertEquals(-40L, mapper.map(row, RegisteredClientsSourceResolver.MODERN_SOURCE)
                .signalDbm().orElseThrow());
    }

    @Test
    void listShapedCountersRatesAndUnknownPropertiesRemainLossless() throws Exception {
        WifiRegistration legacy = mapper.map(
                fixture("/fixtures/wifi/legacy-registration.properties"),
                RegisteredClientsSourceResolver.LEGACY_SOURCE);
        WifiRegistration modern = mapper.map(
                fixture("/fixtures/wifi/modern-registration.properties"),
                RegisteredClientsSourceResolver.MODERN_SOURCE);

        assertEquals("6558776,1754538", legacy.packets().orElseThrow());
        assertEquals("8324668190,187580746", legacy.bytes().orElseThrow());
        assertEquals("866.6Mbps-80MHz/2S/SGI", modern.txRate().orElseThrow());
        assertEquals("kept", legacy.raw().find("legacy-extra").orElseThrow());
        assertEquals("kept", modern.raw().find("modern-extra").orElseThrow());
    }

    @Test
    void malformedKnownTypedFieldIsDataError() {
        RouterOsRecord malformed = RouterOsRecord.of(Map.of("signal", "not-a-number"));
        assertThrows(MikrotikDataException.class, () -> mapper.map(
                malformed,
                RegisteredClientsSourceResolver.MODERN_SOURCE));
    }

    private static RouterOsRecord fixture(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = WifiRegistrationMapperTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "fixture missing: " + resource);
            properties.load(input);
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        properties.stringPropertyNames().stream().sorted()
                .forEach(key -> values.put(key, properties.getProperty(key)));
        return RouterOsRecord.of(values);
    }
}
