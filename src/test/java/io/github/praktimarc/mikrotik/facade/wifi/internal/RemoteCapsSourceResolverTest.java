package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsPackage;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityRegistry;
import io.github.praktimarc.mikrotik.facade.internal.compat.DataSourceStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RemoteCapsSourceResolverTest {
    @Test
    void bothRelevantManagersProduceCompositePlanInStableOrder() {
        RemoteCapsSourceResolver resolver = new RemoteCapsSourceResolver(
                environment("7.20.4", List.of("wireless")),
                new CapabilityRegistry());

        var plan = resolver.plan(Map.of(
                RemoteCapsSourceResolver.LEGACY_SOURCE, true,
                RemoteCapsSourceResolver.MODERN_SOURCE, true)).orElseThrow();

        assertEquals(DataSourceStrategy.COMPOSITE, plan.strategy());
        assertEquals(
                List.of(
                        RemoteCapsSourceResolver.LEGACY_SOURCE,
                        RemoteCapsSourceResolver.MODERN_SOURCE),
                plan.sources());
    }

    @Test
    void remoteCapReadUsesExactBaseMacQueryAndNeverFallbackSemantics() {
        RemoteCapsSourceResolver resolver = new RemoteCapsSourceResolver(
                environment("7.20.4", List.of("wifi-qcom")),
                new CapabilityRegistry());

        var operation = resolver.remoteCapsOperation(
                RemoteCapsSourceResolver.MODERN_SOURCE,
                Optional.of("AA:BB:CC:DD:EE:FF"));

        assertEquals("/interface/wifi/capsman/remote-cap/print", operation.command().path());
        assertEquals("AA:BB:CC:DD:EE:FF", operation.command().queries().get("base-mac"));
    }

    private static RouterOsEnvironment environment(String version, List<String> packages) {
        RouterOsSystemInfo system = new RouterOsSystemInfo(
                version, "arm64", "test-board", "MikroTik", RouterOsRecord.empty());
        return RouterOsEnvironment.withPackages(system, packages.stream()
                .map(name -> new RouterOsPackage(
                        name, version, RouterOsRecord.of(Map.of("name", name))))
                .toList());
    }
}
