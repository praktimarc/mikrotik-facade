package io.github.praktimarc.mikrotik.facade.environment;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterOsEnvironmentTest {

    @Test
    void environmentIsImmutableSessionSnapshotAndPreservesUnavailablePackages() {
        RouterOsSystemInfo systemInfo = new RouterOsSystemInfo(
                "7.20.2",
                "arm64",
                "RB5009UG+S+",
                "MikroTik",
                RouterOsRecord.of(Map.of("version", "7.20.2", "future", "raw")));
        List<RouterOsPackage> source = new ArrayList<>();
        source.add(new RouterOsPackage(
                "routeros",
                "7.20.2",
                RouterOsRecord.of(Map.of("name", "routeros", "version", "7.20.2"))));

        RouterOsEnvironment available = RouterOsEnvironment.withPackages(systemInfo, source);
        source.clear();

        assertEquals(1, available.packages().orElseThrow().size());
        assertThrows(UnsupportedOperationException.class,
                () -> available.packages().orElseThrow().add(new RouterOsPackage(
                        "extra",
                        null,
                        RouterOsRecord.of(Map.of("name", "extra")))));
        assertEquals("raw", available.systemInfo().raw().find("future").orElseThrow());
        assertTrue(available.packageInformationAvailable());

        RouterOsEnvironment unavailable = RouterOsEnvironment.withoutPackageInformation(systemInfo);
        assertTrue(unavailable.packages().isEmpty());
        assertFalse(unavailable.packageInformationAvailable());
    }
}
