package io.github.praktimarc.mikrotik.facade.snmp.community;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.snmp.community.internal.SnmpCommunityMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the typed RouterOS SNMP facade. */
public final class AsyncSnmpCommunityApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final SnmpCommunityMapper mapper;

    /** Creates the asynchronous SNMP facade for session wiring. */
    public AsyncSnmpCommunityApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new SnmpCommunityMapper());
    }

    AsyncSnmpCommunityApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            SnmpCommunityMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public CompletableFuture<List<SnmpCommunity>> list() {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(SnmpCommunityApi.communitiesOperation(mapper));
    }

    public CompletableFuture<Optional<SnmpCommunity>> findByName(String name) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(SnmpCommunityApi.findCommunityByNameOperation(name, mapper));
    }

    public CompletableFuture<Void> setProperties(
            String id,
            RouterOsProperties properties) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(SnmpCommunityApi.setCommunityPropertiesOperation(id, properties));
    }

    public CompletableFuture<Void> setWriteAccess(String id, boolean enabled) {
        return setProperties(
                id,
                RouterOsProperties.builder().set("write-access", Boolean.toString(enabled)).build());
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
