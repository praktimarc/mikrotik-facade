package io.github.praktimarc.mikrotik.facade.ip.pool;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.pool.internal.IpPoolMapper;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous counterpart to /ip/pool. */
public final class AsyncIpPoolApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final IpPoolMapper mapper;

    public AsyncIpPoolApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = new IpPoolMapper();
    }

    public CompletableFuture<List<IpPool>> list() {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException failure) {
            CompletableFuture<List<IpPool>> result = new CompletableFuture<>();
            callbackExecutor.execute(() -> result.completeExceptionally(failure));
            return result;
        }
        return engine.executeAsync(IpPoolApi.listOperation(mapper));
    }
}
