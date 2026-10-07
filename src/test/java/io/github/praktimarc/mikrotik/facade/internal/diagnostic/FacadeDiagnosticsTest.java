package io.github.praktimarc.mikrotik.facade.internal.diagnostic;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikTimeoutException;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.compat.DataSourcePlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FacadeDiagnosticsTest {
    @Test
    void commandDiagnosticsExposeStructureButNeverValues() {
        List<Entry> entries = new ArrayList<>();
        FacadeDiagnostics diagnostics = new FacadeDiagnostics(
                "session-test",
                (level, message) -> entries.add(new Entry(level, message)));

        RouterOsCommand command = RouterOsCommand.builder("/tool/fetch")
                .argument("url", "sftp://secret-host/private")
                .argument("opaque-credential", "UNCLASSIFIED-SECRET")
                .argument("password", "PASSWORD-SECRET")
                .query("address", "192.0.2.99")
                .flag("count-only")
                .property("status")
                .build();

        diagnostics.commandStarted("op-1", "raw /tool/fetch", command);
        diagnostics.commandTerminal(
                "op-1",
                "raw /tool/fetch",
                command,
                new MikrotikCommandException(
                        "RouterOS command failed",
                        "raw /tool/fetch",
                        "/tool/fetch",
                        4,
                        "reflected UNCLASSIFIED-SECRET",
                        null));

        String all = entries.toString();
        assertTrue(all.contains("session=session-test"));
        assertTrue(all.contains("operation=op-1"));
        assertTrue(all.contains("argumentKeys=[url, opaque-credential, password]"));
        assertTrue(all.contains("queryKeys=[address]"));
        assertTrue(all.contains("flags=[count-only]"));
        assertTrue(all.contains("properties=[status]"));
        assertFalse(all.contains("secret-host"));
        assertFalse(all.contains("UNCLASSIFIED-SECRET"));
        assertFalse(all.contains("PASSWORD-SECRET"));
        assertFalse(all.contains("192.0.2.99"));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.level() == FacadeDiagnostics.Level.DEBUG
                        && entry.message().contains("routeros-rejected")));
        assertFalse(entries.stream().anyMatch(entry ->
                entry.level() == FacadeDiagnostics.Level.ERROR));
    }

    @Test
    void timeoutIsWarnAndInternalFailureIsError() {
        List<Entry> entries = new ArrayList<>();
        FacadeDiagnostics diagnostics = new FacadeDiagnostics(
                "session-test",
                (level, message) -> entries.add(new Entry(level, message)));
        RouterOsCommand command = RouterOsCommand.builder("/ping").build();

        diagnostics.commandTerminal(
                "op-2",
                "ping from router",
                command,
                new MikrotikTimeoutException("timeout"));
        diagnostics.internalError(
                "op-3",
                "internal test",
                command,
                new IllegalStateException("do-not-log-this-message"));

        assertTrue(entries.stream().anyMatch(entry ->
                entry.level() == FacadeDiagnostics.Level.WARN
                        && entry.message().contains("event=timeout")));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.level() == FacadeDiagnostics.Level.ERROR
                        && entry.message().contains("cause=IllegalStateException")));
        assertFalse(entries.toString().contains("do-not-log-this-message"));
    }

    @Test
    void lifecycleCapabilityAndFallbackMessagesAreCorrelatableAndValueFree() {
        List<Entry> entries = new ArrayList<>();
        FacadeDiagnostics diagnostics = new FacadeDiagnostics(
                "session-77",
                (level, message) -> entries.add(new Entry(level, message)));

        diagnostics.sessionReady();
        diagnostics.capabilityDecision("wifi.modern", "SUPPORTED", "probe");
        DataSourcePlan plan = DataSourcePlan.preferredFallback(
                "wifi.registration",
                "/interface/wifi/registration-table",
                "/caps-man/registration-table");
        diagnostics.sourceSelection(plan, List.of("/caps-man/registration-table"));
        diagnostics.compatibilityFallback(
                "wifi.registration",
                "/interface/wifi/registration-table",
                "/caps-man/registration-table");
        diagnostics.compatibilityFallback(
                "wifi.registration",
                "/interface/wifi/registration-table",
                "/caps-man/registration-table");
        diagnostics.sessionClosing();
        diagnostics.sessionClosed();

        assertEquals(3, entries.stream()
                .filter(entry -> entry.level() == FacadeDiagnostics.Level.INFO)
                .count());
        assertTrue(entries.stream().anyMatch(entry ->
                entry.level() == FacadeDiagnostics.Level.DEBUG
                        && entry.message().contains("capability=wifi.modern")));
        assertEquals(1, entries.stream().filter(entry ->
                entry.level() == FacadeDiagnostics.Level.WARN
                        && entry.message().contains("compatibility-fallback")
                        && entry.message().contains("unavailable=/interface/wifi/registration-table")
                        && entry.message().contains("selected=/caps-man/registration-table")).count());
    }

    private record Entry(FacadeDiagnostics.Level level, String message) {}
}
