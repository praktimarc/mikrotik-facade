package io.github.praktimarc.mikrotik.facade.internal.session;

import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;
import me.legrange.mikrotik.ApiConnectionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionLifecycleDiagnosticsTest {
    @Test
    void unexpectedLossWarnsOnceButLateLossDuringControlledCloseDoesNot() {
        List<Entry> entries = new ArrayList<>();
        FacadeDiagnostics diagnostics = new FacadeDiagnostics(
                "session-test",
                (level, message) -> entries.add(new Entry(level, message)));
        SessionLifecycle lifecycle = new SessionLifecycle();
        lifecycle.attachDiagnostics(diagnostics);

        lifecycle.connectionLost(new ApiConnectionException("wire-secret"));
        lifecycle.connectionLost(new ApiConnectionException("duplicate-secret"));

        assertEquals(SessionState.BROKEN, lifecycle.state());
        assertEquals(1, entries.stream()
                .filter(entry -> entry.level() == FacadeDiagnostics.Level.WARN)
                .count());
        assertFalse(entries.toString().contains("wire-secret"));
        assertFalse(entries.toString().contains("duplicate-secret"));

        SessionLifecycle closing = new SessionLifecycle();
        closing.attachDiagnostics(diagnostics);
        assertTrue(closing.beginClose());
        closing.connectionLost(new ApiConnectionException("late-close-secret"));
        assertEquals(SessionState.CLOSING, closing.state());
        assertFalse(entries.toString().contains("late-close-secret"));
        assertEquals(1, entries.stream()
                .filter(entry -> entry.level() == FacadeDiagnostics.Level.WARN)
                .count());
    }

    private record Entry(FacadeDiagnostics.Level level, String message) {}
}
