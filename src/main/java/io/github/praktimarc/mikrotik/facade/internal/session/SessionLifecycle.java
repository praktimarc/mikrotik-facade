package io.github.praktimarc.mikrotik.facade.internal.session;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.ConnectionListener;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal atomic lifecycle for one low-level RouterOS session.
 */
public final class SessionLifecycle implements ConnectionListener {

    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.OPEN);
    private final AtomicReference<ApiConnectionException> failure = new AtomicReference<>();
    private volatile FacadeDiagnostics diagnostics = FacadeDiagnostics.noOp("unbound-session");

    /**
     * Creates a lifecycle in the open state. The instance exists internally during bootstrap,
     * but no facade session is exposed until bootstrap completes successfully.
     */
    public SessionLifecycle() {
    }

    /**
     * Returns the current session state.
     *
     * @return current state
     */
    public SessionState state() {
        return state.get();
    }

    /**
     * Returns the retained fatal connection failure when the session became broken.
     *
     * @return optional retained failure
     */
    public Optional<ApiConnectionException> failure() {
        return Optional.ofNullable(failure.get());
    }

    /**
     * Attaches the session-scoped diagnostic context after the facade session id exists.
     *
     * @param diagnostics secret-safe session diagnostics
     */
    public void attachDiagnostics(FacadeDiagnostics diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public void connectionLost(ApiConnectionException cause) {
        Objects.requireNonNull(cause, "cause");
        if (state.compareAndSet(SessionState.OPEN, SessionState.BROKEN)) {
            failure.compareAndSet(null, cause);
            diagnostics.connectionLost(cause);
        }
    }

    /**
     * Verifies that a new technical RouterOS operation may start.
     *
     * <p>A broken session is a technical connection failure. A closing or closed
     * session is instead a lifecycle programming error.</p>
     *
     * @throws MikrotikConnectionException if the session is broken after fatal connection loss
     * @throws IllegalStateException if controlled close has already started or completed
     */
    public void ensureOpen() throws MikrotikConnectionException {
        SessionState current = state.get();
        if (current == SessionState.OPEN) {
            return;
        }
        if (current == SessionState.BROKEN) {
            ApiConnectionException cause = failure.get();
            if (cause == null) {
                throw new MikrotikConnectionException("RouterOS session is broken");
            }
            throw new MikrotikConnectionException(
                    "RouterOS session is broken after fatal connection loss",
                    cause);
        }
        throw new IllegalStateException(
                "RouterOS session is " + current.name().toLowerCase());
    }

    /**
     * Starts a controlled close when the session has not already started closing.
     *
     * @return {@code true} only for the caller that won the transition to {@link SessionState#CLOSING}
     */
    public boolean beginClose() {
        while (true) {
            SessionState current = state.get();
            if (current == SessionState.CLOSING || current == SessionState.CLOSED) {
                return false;
            }
            if (state.compareAndSet(current, SessionState.CLOSING)) {
                return true;
            }
        }
    }

    /**
     * Completes a controlled close.
     */
    public void finishClose() {
        state.set(SessionState.CLOSED);
    }
}
