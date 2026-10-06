package io.github.praktimarc.mikrotik.facade.internal.session;

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

    @Override
    public void connectionLost(ApiConnectionException cause) {
        Objects.requireNonNull(cause, "cause");
        if (state.compareAndSet(SessionState.OPEN, SessionState.BROKEN)) {
            failure.compareAndSet(null, cause);
        }
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
