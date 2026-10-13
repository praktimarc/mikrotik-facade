package io.github.praktimarc.mikrotik.facade.ip.address;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.address.internal.IpAddressV4Mapper;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous /ip/address (IPv4) API. */
public final class AsyncIpAddressApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final IpAddressV4Mapper mapper = new IpAddressV4Mapper();

    public AsyncIpAddressApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        this.engine = Objects.requireNonNull(engine);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor);
    }

    public CompletableFuture<List<IpAddressV4>> list() {
        return list(RouterOsProperties.builder().build());
    }

    public CompletableFuture<List<IpAddressV4>> list(RouterOsProperties queries) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException error) {
            CompletableFuture<List<IpAddressV4>> result = new CompletableFuture<>();
            callbackExecutor.execute(() -> result.completeExceptionally(error));
            return result;
        }
        return engine.executeAsync(IpAddressApi.listOperation(queries, mapper));
    }
}
