package io.github.praktimarc.mikrotik.facade.internal.command;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic lifecycle and data collector for one finite RouterOS operation.
 */
public final class OperationContext {

    /** Internal operation states. */
    public enum State {
        /** Operation exists but dispatch has not started. */ CREATED,
        /** Dispatch started but the low-level tag is not yet known. */ DISPATCHING,
        /** Dispatch returned the low-level tag. */ ACTIVE,
        /** Logical cancellation won while remote cancellation is pending. */ CANCELLING,
        /** RouterOS completed successfully. */ COMPLETED,
        /** Timeout, transport, command, data, or mapping failure won. */ FAILED,
        /** Cancellation is logically complete. */ CANCELLED
    }

    private final ApiConnection connection;
    private final String operation;
    private final RouterOsCommand command;
    private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);
    private final AtomicReference<String> tag = new AtomicReference<>();
    private final AtomicBoolean remoteCancelRequested = new AtomicBoolean();
    private final AtomicBoolean remoteCancelSent = new AtomicBoolean();
    private final CompletableFuture<CommandResult> result = new CompletableFuture<>();
    private final Object recordsLock = new Object();
    private final List<RouterOsRecord> records = new ArrayList<>();

    /** Creates a new operation context. */
    public OperationContext(ApiConnection connection, String operation, RouterOsCommand command) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.command = Objects.requireNonNull(command, "command");
    }

    /** Returns the current operation state. */
    public State state() {
        return state.get();
    }

    /** Returns the low-level tag once assigned. */
    public Optional<String> tag() {
        return Optional.ofNullable(tag.get());
    }

    /** Returns the common internal result future. */
    public CompletableFuture<CommandResult> resultFuture() {
        return result;
    }

    /** Marks that low-level dispatch is starting. */
    public boolean beginDispatch() {
        return state.compareAndSet(State.CREATED, State.DISPATCHING);
    }

    /** Records the tag returned by the low-level connection. */
    public void tagAssigned(String lowLevelTag) {
        Objects.requireNonNull(lowLevelTag, "lowLevelTag");
        if (!tag.compareAndSet(null, lowLevelTag)) {
            throw new IllegalStateException("RouterOS tag already assigned");
        }
        state.compareAndSet(State.DISPATCHING, State.ACTIVE);
        if (remoteCancelRequested.get()) {
            sendRemoteCancelBestEffort();
            state.compareAndSet(State.CANCELLING, State.CANCELLED);
        }
    }

    /** Collects one normal RouterOS !re record while the operation is active. */
    public void receive(Map<String, String> record) {
        Objects.requireNonNull(record, "record");
        synchronized (recordsLock) {
            if (isTerminalOrCancelling(state.get())) {
                return;
            }
            records.add(RouterOsRecord.of(record));
        }
    }

    /** Completes from terminal !done metadata. */
    public boolean complete(Map<String, String> completion) {
        Objects.requireNonNull(completion, "completion");
        synchronized (recordsLock) {
            if (!transitionTo(State.COMPLETED)) {
                return false;
            }
            result.complete(new CommandResult(List.copyOf(records), RouterOsRecord.of(completion)));
            return true;
        }
    }

    /** Fails with an already mapped facade exception. */
    public boolean fail(MikrotikFacadeException failure) {
        Objects.requireNonNull(failure, "failure");
        if (!transitionTo(State.FAILED)) {
            return false;
        }
        result.completeExceptionally(failure);
        return true;
    }

    /** Marks timeout failure and requests best-effort remote cancellation. */
    public boolean timeout(MikrotikFacadeException failure) {
        Objects.requireNonNull(failure, "failure");
        if (!transitionTo(State.FAILED)) {
            return false;
        }
        remoteCancelRequested.set(true);
        result.completeExceptionally(failure);
        sendRemoteCancelBestEffort();
        return true;
    }

    /**
     * Requests user cancellation.
     *
     * <p>Cancellation during DISPATCHING is remembered until the tag exists.</p>
     */
    public boolean cancel() {
        while (true) {
            State current = state.get();
            if (isTerminalOrCancelling(current)) {
                return false;
            }
            State next = current == State.CREATED ? State.CANCELLED : State.CANCELLING;
            if (state.compareAndSet(current, next)) {
                if (next == State.CANCELLING) {
                    remoteCancelRequested.set(true);
                }
                result.completeExceptionally(new CancellationException("RouterOS command cancelled"));
                if (next == State.CANCELLING) {
                    sendRemoteCancelBestEffort();
                    if (tag.get() != null) {
                        state.compareAndSet(State.CANCELLING, State.CANCELLED);
                    }
                }
                return true;
            }
        }
    }

    /** Finalizes cancellation when dispatch ends without producing a tag. */
    public void dispatchFinishedWithoutTag() {
        state.compareAndSet(State.CANCELLING, State.CANCELLED);
    }

    /** Returns the safe operation name. */
    public String operation() {
        return operation;
    }

    /** Returns the immutable command. */
    public RouterOsCommand command() {
        return command;
    }

    private boolean transitionTo(State terminal) {
        while (true) {
            State current = state.get();
            if (isTerminalOrCancelling(current)) {
                return false;
            }
            if (state.compareAndSet(current, terminal)) {
                return true;
            }
        }
    }

    private static boolean isTerminalOrCancelling(State candidate) {
        return candidate == State.CANCELLING
                || candidate == State.CANCELLED
                || candidate == State.COMPLETED
                || candidate == State.FAILED;
    }

    private void sendRemoteCancelBestEffort() {
        String currentTag = tag.get();
        if (currentTag == null
                || !remoteCancelRequested.get()
                || !remoteCancelSent.compareAndSet(false, true)) {
            return;
        }
        try {
            connection.cancel(currentTag);
        } catch (MikrotikApiException ignored) {
            // Remote cancellation is best effort and never replaces the logical terminal result.
        }
    }
}
