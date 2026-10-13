package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.interfaces.internal.InterfaceInfoMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of finite interface and IP-address reads. */
public final class AsyncInterfacesApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final InterfaceInfoMapper interfaceMapper;

    /** Creates the asynchronous interface facade for session wiring. */
    public AsyncInterfacesApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new InterfaceInfoMapper());
    }

    AsyncInterfacesApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            InterfaceInfoMapper interfaceMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.interfaceMapper = Objects.requireNonNull(interfaceMapper, "interfaceMapper");
    }

    public CompletableFuture<List<InterfaceInfo>> list() {
        return list(RouterOsProperties.builder().build());
    }

    public CompletableFuture<List<InterfaceInfo>> list(RouterOsProperties queries) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(InterfacesApi.listOperation(queries, interfaceMapper));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
