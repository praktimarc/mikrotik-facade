package io.github.praktimarc.mikrotik.facade.queue.type;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.queue.type.internal.QueueTypeMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the typed RouterOS queue-type API. */
public final class AsyncQueueTypeApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final QueueTypeMapper mapper;

    /** Creates the asynchronous queue-type API for session wiring. */
    public AsyncQueueTypeApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new QueueTypeMapper());
    }

    AsyncQueueTypeApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            QueueTypeMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public CompletableFuture<List<QueueType>> list() {
        return find(RouterOsProperties.builder().build());
    }

    public CompletableFuture<List<QueueType>> find(RouterOsProperties queries) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(QueueTypeApi.findOperation(queries, mapper));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
