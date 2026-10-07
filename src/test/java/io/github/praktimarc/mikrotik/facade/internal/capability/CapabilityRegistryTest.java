package io.github.praktimarc.mikrotik.facade.internal.capability;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityRegistryTest {

    @Test
    void definitiveSupportedAndUnsupportedResultsAreCached() throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry();
        AtomicInteger supportedCalls = new AtomicInteger();
        AtomicInteger unsupportedCalls = new AtomicInteger();
        RouterOsCommand probe = RouterOsCommand.builder("/interface/wifi/print").build();

        assertEquals(CapabilityState.SUPPORTED, registry.resolve(
                "wifi.path", probe, command -> {
                    supportedCalls.incrementAndGet();
                    return CapabilityState.SUPPORTED;
                }));
        assertEquals(CapabilityState.SUPPORTED, registry.resolve(
                "wifi.path", probe, command -> {
                    supportedCalls.incrementAndGet();
                    return CapabilityState.UNSUPPORTED;
                }));

        assertEquals(CapabilityState.UNSUPPORTED, registry.resolve(
                "legacy.path", probe, command -> {
                    unsupportedCalls.incrementAndGet();
                    return CapabilityState.UNSUPPORTED;
                }));
        assertEquals(CapabilityState.UNSUPPORTED, registry.resolve(
                "legacy.path", probe, command -> {
                    unsupportedCalls.incrementAndGet();
                    return CapabilityState.SUPPORTED;
                }));

        assertEquals(1, supportedCalls.get());
        assertEquals(1, unsupportedCalls.get());
    }

    @Test
    void technicalProbeFailureRemainsUnknownAndIsRetried() {
        CapabilityRegistry registry = new CapabilityRegistry();
        AtomicInteger calls = new AtomicInteger();
        RouterOsCommand probe = RouterOsCommand.builder("/interface/wifi/print").build();

        for (int i = 0; i < 2; i++) {
            assertThrows(MikrotikConnectionException.class, () -> registry.resolve(
                    "wifi.path", probe, command -> {
                        calls.incrementAndGet();
                        throw new MikrotikConnectionException("temporary transport failure");
                    }));
            assertEquals(CapabilityState.UNKNOWN, registry.state("wifi.path"));
        }
        assertEquals(2, calls.get());
    }

    @Test
    void activeCapabilityProbesMustBeReadOnlyPrintCommands() {
        CapabilityRegistry registry = new CapabilityRegistry();
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () -> registry.resolve(
                "unsafe.probe",
                RouterOsCommand.builder("/ip/address/add").build(),
                command -> {
                    calls.incrementAndGet();
                    return CapabilityState.SUPPORTED;
                }));
        assertEquals(0, calls.get());
    }

    @Test
    void successfulEmptyReadMayStillClassifyPathAsSupported() throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry();
        RouterOsCommand probe = RouterOsCommand.builder("/caps-man/registration-table/print").build();

        CapabilityState state = registry.resolve(
                "capsman.registration.path",
                probe,
                command -> CapabilityState.SUPPORTED);

        assertEquals(CapabilityState.SUPPORTED, state);
        assertEquals(CapabilityState.SUPPORTED, registry.state("capsman.registration.path"));
    }

    @Test
    void explicitUnknownProbeResultIsNotCached() throws Exception {
        CapabilityRegistry registry = new CapabilityRegistry();
        AtomicInteger calls = new AtomicInteger();
        RouterOsCommand probe = RouterOsCommand.builder("/interface/print").build();

        for (int i = 0; i < 2; i++) {
            assertEquals(CapabilityState.UNKNOWN, registry.resolve(
                    "driver.counter", probe, command -> {
                        calls.incrementAndGet();
                        return CapabilityState.UNKNOWN;
                    }));
        }
        assertEquals(2, calls.get());
        assertEquals(CapabilityState.UNKNOWN, registry.state("driver.counter"));
    }

    @Test
    void conflictingDefinitiveKnowledgeIsRejectedInsteadOfSilentlyFlipped() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.recordDefinitive("wifi.path", CapabilityState.SUPPORTED);

        assertThrows(IllegalStateException.class, () -> registry.recordDefinitive(
                "wifi.path", CapabilityState.UNSUPPORTED));
        assertEquals(CapabilityState.SUPPORTED, registry.state("wifi.path"));
    }


    @Test
    void capabilityProbeAndCacheDecisionsEmitDebugWithoutCommandValues() throws Exception {
        List<String> logs = new ArrayList<>();
        FacadeDiagnostics diagnostics = new FacadeDiagnostics(
                "session-test",
                (level, message) -> logs.add(level + ":" + message));
        CapabilityRegistry registry = new CapabilityRegistry(diagnostics);
        RouterOsCommand probe = RouterOsCommand.builder("/interface/wifi/print")
                .query("opaque", "DO-NOT-LOG")
                .build();

        assertEquals(CapabilityState.SUPPORTED, registry.resolve(
                "wifi.path",
                probe,
                command -> CapabilityState.SUPPORTED));
        assertEquals(CapabilityState.SUPPORTED, registry.resolve(
                "wifi.path",
                probe,
                command -> CapabilityState.UNSUPPORTED));

        assertTrue(logs.stream().anyMatch(line ->
                line.startsWith("DEBUG:")
                        && line.contains("capability=wifi.path")
                        && line.contains("state=SUPPORTED")
                        && line.contains("source=probe")));
        assertTrue(logs.stream().anyMatch(line ->
                line.startsWith("DEBUG:")
                        && line.contains("source=cache")));
        assertFalse(logs.toString().contains("DO-NOT-LOG"));
    }

}
