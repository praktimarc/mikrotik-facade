package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsPackage;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityRegistry;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityState;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.compat.DataSourceStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegisteredClientsSourceResolverTest {

    @Test
    void routerOs6UsesLegacyCapsmanCandidateOnly() {
        RegisteredClientsSourceResolver resolver = resolver(environment("6.49.17", List.of()));
        var candidates = resolver.candidates();
        assertEquals(1, candidates.size());
        assertEquals(RegisteredClientsSourceResolver.LEGACY_SOURCE, candidates.get(0).source());
    }

    @Test
    void routerOs713PlusWithoutWirelessPackageUsesModernCandidateOnly() {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.20.4", List.of("wifi-qcom")));
        var candidates = resolver.candidates();
        assertEquals(1, candidates.size());
        assertEquals(RegisteredClientsSourceResolver.MODERN_SOURCE, candidates.get(0).source());
    }

    @Test
    void routerOs713PlusLocalModernDriverIsAnExplicitStandaloneSourceHint() {
        RegisteredClientsSourceResolver modern = resolver(environment("7.20.4", List.of("wifi-qcom")));
        RegisteredClientsSourceResolver controllerOnly = resolver(environment("7.20.4", List.of()));
        RegisteredClientsSourceResolver oldAlias = resolver(environment("7.12.2", List.of("wifiwave2")));

        assertTrue(modern.modernLocalStackHint());
        assertFalse(controllerOnly.modernLocalStackHint());
        assertFalse(oldAlias.modernLocalStackHint());
        assertEquals(CapabilityState.UNKNOWN, modern.modernRegistrationCapabilityState());
        modern.recordModernRegistrationSupported();
        assertEquals(CapabilityState.SUPPORTED, modern.modernRegistrationCapabilityState());
    }

    @Test
    void localModernSourceCanBeSelectedWithoutEnabledCapsmanManagerAfterPathVerification() {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.20.4", List.of("wifi-qcom")));
        var plan = resolver.plan(Map.of(RegisteredClientsSourceResolver.MODERN_SOURCE, true)).orElseThrow();

        assertEquals(DataSourceStrategy.SINGLE, plan.strategy());
        assertEquals(List.of(RegisteredClientsSourceResolver.MODERN_SOURCE), plan.sources());
    }

    @Test
    void bothPathFamiliesCanExistWhileOnlyOneManagerIsRelevant() {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.20.4", List.of("wireless")));
        assertEquals(2, resolver.candidates().size());

        var plan = resolver.plan(Map.of(
                RegisteredClientsSourceResolver.LEGACY_SOURCE, false,
                RegisteredClientsSourceResolver.MODERN_SOURCE, true)).orElseThrow();

        assertEquals(DataSourceStrategy.SINGLE, plan.strategy());
        assertEquals(List.of(RegisteredClientsSourceResolver.MODERN_SOURCE), plan.sources());
    }

    @Test
    void bothEnabledManagersProduceCompositeWithoutGenericDeduplication() throws Exception {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.20.4", List.of("wireless")));
        var plan = resolver.plan(Map.of(
                RegisteredClientsSourceResolver.LEGACY_SOURCE, true,
                RegisteredClientsSourceResolver.MODERN_SOURCE, true)).orElseThrow();

        assertEquals(DataSourceStrategy.COMPOSITE, plan.strategy());
        RouterOsRecord legacy = RouterOsRecord.of(Map.of(
                ".id", "*1", "mac-address", "AA:BB:CC:DD:EE:FF", "rx-signal", "-50"));
        RouterOsRecord modern = RouterOsRecord.of(Map.of(
                ".id", "*1", "mac-address", "AA:BB:CC:DD:EE:FF", "signal", "-51"));

        var registrations = resolver.map(plan, Map.of(
                RegisteredClientsSourceResolver.LEGACY_SOURCE, List.of(legacy),
                RegisteredClientsSourceResolver.MODERN_SOURCE, List.of(modern)),
                new WifiRegistrationMapper());

        assertEquals(2, registrations.size());
        assertEquals(RegisteredClientsSourceResolver.LEGACY_SOURCE, registrations.get(0).source());
        assertEquals(RegisteredClientsSourceResolver.MODERN_SOURCE, registrations.get(1).source());
    }

    @Test
    void emptyRegistrationTableIsSuccessfulEmptyDataNotFallbackEvidence() throws Exception {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.20.4", List.of("wifi-qcom")));
        var operation = resolver.registrationOperation(RegisteredClientsSourceResolver.MODERN_SOURCE);
        assertEquals("/interface/wifi/registration-table/print", operation.command().path());
        assertTrue(operation.map(new CommandResult(List.of(), RouterOsRecord.empty())).isEmpty());
    }

    @Test
    void managerProbesUseDocumentedReadOnlyGlobalConfigurationPaths() throws Exception {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.20.4", List.of("wireless")));
        var candidates = resolver.candidates();

        assertEquals("/caps-man/manager/print",
                resolver.managerEnabledOperation(candidates.get(0)).command().path());
        assertEquals("/interface/wifi/capsman/print",
                resolver.managerEnabledOperation(candidates.get(1)).command().path());
        assertTrue(resolver.managerEnabledOperation(candidates.get(0)).map(
                new CommandResult(
                        List.of(RouterOsRecord.of(Map.of("enabled", "yes"))),
                        RouterOsRecord.empty())));
    }

    @Test
    void missingManagerCommandIsDefinitiveOnlyForCategoryZeroAndCacheDoesNotFlip() {
        CapabilityRegistry capabilities = new CapabilityRegistry();
        RegisteredClientsSourceResolver resolver = new RegisteredClientsSourceResolver(
                environment("7.20.4", null), capabilities);
        var legacy = resolver.candidates().get(0);

        assertTrue(resolver.isMissingManagerCommand(new MikrotikCommandException(
                "missing", "probe", "/caps-man/manager/print", 0, "missing command", null)));
        assertFalse(resolver.isMissingManagerCommand(new MikrotikCommandException(
                "denied", "probe", "/caps-man/manager/print", 6, "permission denied", null)));

        resolver.recordUnsupported(legacy);
        assertEquals(CapabilityState.UNSUPPORTED, resolver.capabilityState(legacy));
        assertThrows(IllegalStateException.class, () -> resolver.recordSupported(legacy));
    }

    @Test
    void unavailablePackageSnapshotKeepsBothRouterOs7SourcesProbeable() {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.20.4", null));
        assertEquals(List.of(
                        RegisteredClientsSourceResolver.LEGACY_SOURCE,
                        RegisteredClientsSourceResolver.MODERN_SOURCE),
                resolver.candidates().stream().map(RegisteredClientsSourceResolver.SourceCandidate::source).toList());
    }

    @Test
    void pre713Wifiwave2DoesNotInventUndocumentedThirdRegistrationPath() {
        RegisteredClientsSourceResolver resolver = resolver(environment("7.12.2", List.of("wifiwave2")));
        assertEquals(List.of(RegisteredClientsSourceResolver.LEGACY_SOURCE),
                resolver.candidates().stream().map(RegisteredClientsSourceResolver.SourceCandidate::source).toList());
    }

    private static RegisteredClientsSourceResolver resolver(RouterOsEnvironment environment) {
        return new RegisteredClientsSourceResolver(environment, new CapabilityRegistry());
    }

    private static RouterOsEnvironment environment(String version, List<String> packages) {
        RouterOsSystemInfo system = new RouterOsSystemInfo(
                version, "arm64", "test-board", "MikroTik", RouterOsRecord.empty());
        if (packages == null) {
            return RouterOsEnvironment.withoutPackageInformation(system);
        }
        return RouterOsEnvironment.withPackages(system, packages.stream()
                .map(name -> new RouterOsPackage(name, version, RouterOsRecord.of(Map.of("name", name))))
                .toList());
    }
}
