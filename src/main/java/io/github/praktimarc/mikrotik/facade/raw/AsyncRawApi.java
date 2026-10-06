package io.github.praktimarc.mikrotik.facade.raw;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Asynchronous raw RouterOS escape hatch backed by the shared command engine.
 */
public final class AsyncRawApi {

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;

    /**
     * Creates the asynchronous raw API for session wiring.
     *
     * <p>Consumers normally obtain this object from
     * {@code MikrotikRtrApi.async().raw()}.</p>
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param callbackExecutor public completion executor
     */
    public AsyncRawApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    /**
     * Executes a raw RouterOS command path and returns only normal records.
     *
     * <p>The returned future is the shared engine future directly, so public
     * cancellation retains RouterOS tag cancellation semantics.</p>
     *
     * @param commandPath RouterOS command path
     * @return cancellable future for immutable normal {@code !re} records
     */
    public CompletableFuture<List<RouterOsRecord>> execute(String commandPath) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(new RawCommandBuilder.RecordsOperation(
                RouterOsCommand.builder(commandPath).build()));
    }

    /**
     * Starts a structured asynchronous raw RouterOS command.
     *
     * @param commandPath RouterOS command path
     * @return fluent asynchronous raw command builder
     */
    public RawCommandBuilder.Async command(String commandPath) {
        return new RawCommandBuilder.Async(this, commandPath);
    }

    CompletableFuture<RawCommandResult> executeCommand(RouterOsCommand command) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(new RawCommandBuilder.ResultOperation(command));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
