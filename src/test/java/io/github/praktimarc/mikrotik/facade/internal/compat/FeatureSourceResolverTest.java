package io.github.praktimarc.mikrotik.facade.internal.compat;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureSourceResolverTest {

    private final FeatureSourceResolver resolver = new FeatureSourceResolver();

    @Test
    void emptySuccessfulPreferredResultDoesNotTriggerFallback() {
        DataSourcePlan plan = DataSourcePlan.preferredFallback(
                "wifi.registration",
                "/interface/wifi/registration-table",
                "/caps-man/registration-table");

        List<ResolvedRecord> resolved = resolver.resolve(plan, Map.of(
                "/interface/wifi/registration-table", List.of(),
                "/caps-man/registration-table", List.of(record("mac-address", "AA:BB:CC:DD:EE:FF"))));

        assertTrue(resolved.isEmpty());
        assertTrue(resolver.diagnostic(plan, Map.of(
                "/interface/wifi/registration-table", List.of(),
                "/caps-man/registration-table", List.of())).contains("selected=[/interface/wifi/registration-table]"));
    }

    @Test
    void existingUnplannedPathDoesNotBecomeAuthoritativeAutomatically() throws MikrotikDataException {
        DataSourcePlan plan = DataSourcePlan.single(
                "wifi.registration",
                "/caps-man/registration-table");

        List<ResolvedRecord> resolved = resolver.resolve(plan, Map.of(
                "/interface/wifi/registration-table", List.of(record("id", "new")),
                "/caps-man/registration-table", List.of(record("id", "legacy"))));

        assertEquals(1, resolved.size());
        assertEquals("legacy", resolved.get(0).record().require("id"));
        assertEquals("/caps-man/registration-table", resolved.get(0).source());
    }

    @Test
    void compositePreservesProvenanceAndNeverGenericallyDeduplicates() throws MikrotikDataException {
        DataSourcePlan plan = DataSourcePlan.composite(
                "wifi.registration",
                List.of("/caps-man/registration-table", "/interface/wifi/registration-table"));
        RouterOsRecord sameIdentityLegacy = RouterOsRecord.of(Map.of(
                ".id", "*1", "mac-address", "AA:BB:CC:DD:EE:FF", "stack", "legacy"));
        RouterOsRecord sameIdentityNew = RouterOsRecord.of(Map.of(
                ".id", "*1", "mac-address", "AA:BB:CC:DD:EE:FF", "stack", "new"));

        List<ResolvedRecord> resolved = resolver.resolve(plan, Map.of(
                "/caps-man/registration-table", List.of(sameIdentityLegacy),
                "/interface/wifi/registration-table", List.of(sameIdentityNew)));

        assertEquals(2, resolved.size());
        assertEquals("/caps-man/registration-table", resolved.get(0).source());
        assertEquals("/interface/wifi/registration-table", resolved.get(1).source());
        assertEquals("legacy", resolved.get(0).record().require("stack"));
        assertEquals("new", resolved.get(1).record().require("stack"));
    }

    @Test
    void conditionalPlanSelectsExactlyTheConditionChosenSource() {
        DataSourcePlan plan = DataSourcePlan.conditional(
                "dhcp.circuit-id",
                true,
                "/ip/dhcp-server/lease",
                "/ip/dhcp-server/registration");

        assertEquals(DataSourceStrategy.CONDITIONAL, plan.strategy());
        assertEquals(List.of("/ip/dhcp-server/lease"), plan.sources());
    }

    @Test
    void diagnosticsExplainSelectionWithoutIncludingRecordValuesOrInjectedArguments() {
        DataSourcePlan plan = DataSourcePlan.preferredFallback(
                "wifi.registration",
                "/interface/wifi/registration-table",
                "/caps-man/registration-table");
        String diagnostic = resolver.diagnostic(plan, Map.of(
                "/interface/wifi/registration-table",
                List.of(RouterOsRecord.of(Map.of("password", "super-secret")))));

        assertTrue(diagnostic.contains("PREFERRED_FALLBACK"));
        assertTrue(diagnostic.contains("/interface/wifi/registration-table"));
        assertFalse(diagnostic.contains("super-secret"));
        assertThrows(IllegalArgumentException.class, () -> DataSourcePlan.single(
                "wifi.registration",
                "/interface/wifi/print password=super-secret"));
    }

    private static RouterOsRecord record(String key, String value) {
        return RouterOsRecord.of(Map.of(key, value));
    }

    @Test
    void preferredFallbackUsesFallbackOnlyWhenPreferredResultSetIsUnavailable() throws MikrotikDataException {
        DataSourcePlan plan = DataSourcePlan.preferredFallback(
                "wifi.registration",
                "/interface/wifi/registration-table",
                "/caps-man/registration-table");

        List<ResolvedRecord> resolved = resolver.resolve(plan, Map.of(
                "/caps-man/registration-table", List.of(record("id", "fallback"))));

        assertEquals(1, resolved.size());
        assertEquals("fallback", resolved.get(0).record().require("id"));
        assertEquals("/caps-man/registration-table", resolved.get(0).source());
    }


    @Test
    void preferredFallbackEmitsDebugSelectionAndActionableWarn() {
        List<String> logs = new ArrayList<>();
        FacadeDiagnostics diagnostics = new FacadeDiagnostics(
                "session-test",
                (level, message) -> logs.add(level + ":" + message));
        FeatureSourceResolver loggingResolver = new FeatureSourceResolver(diagnostics);
        DataSourcePlan plan = DataSourcePlan.preferredFallback(
                "wifi.registration",
                "/interface/wifi/registration-table",
                "/caps-man/registration-table");

        loggingResolver.resolve(plan, Map.of(
                "/caps-man/registration-table", List.of(record("id", "fallback"))));

        assertTrue(logs.stream().anyMatch(line ->
                line.startsWith("DEBUG:")
                        && line.contains("feature=wifi.registration")
                        && line.contains("selected=[/caps-man/registration-table]")));
        assertTrue(logs.stream().anyMatch(line ->
                line.startsWith("WARN:")
                        && line.contains("compatibility-fallback")
                        && line.contains("unavailable=/interface/wifi/registration-table")
                        && line.contains("selected=/caps-man/registration-table")));
    }

}
