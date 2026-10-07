package io.github.praktimarc.mikrotik.facade.system;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.system.internal.PingResultMapper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the RouterOS system/diagnostic facade. */
public final class AsyncSystemApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final PingResultMapper pingMapper;

    /** Creates the asynchronous system facade for session wiring. */
    public AsyncSystemApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new PingResultMapper());
    }

    AsyncSystemApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            PingResultMapper pingMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.pingMapper = Objects.requireNonNull(pingMapper, "pingMapper");
    }

    public CompletableFuture<PingResult> ping(PingRequest request) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(SystemApi.pingOperation(request, pingMapper));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
