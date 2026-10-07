package io.github.praktimarc.mikrotik.facade.snmp;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.snmp.internal.SnmpCommunityMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the typed RouterOS SNMP facade. */
public final class AsyncSnmpApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final SnmpCommunityMapper mapper;

    /** Creates the asynchronous SNMP facade for session wiring. */
    public AsyncSnmpApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new SnmpCommunityMapper());
    }

    AsyncSnmpApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            SnmpCommunityMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public CompletableFuture<List<SnmpCommunity>> communities() {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(SnmpApi.communitiesOperation(mapper));
    }

    public CompletableFuture<Optional<SnmpCommunity>> findCommunityByName(String name) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(SnmpApi.findCommunityByNameOperation(name, mapper));
    }

    public CompletableFuture<Void> setCommunityProperties(
            String id,
            RouterOsProperties properties) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(SnmpApi.setCommunityPropertiesOperation(id, properties));
    }

    public CompletableFuture<Void> setWriteAccess(String id, boolean enabled) {
        return setCommunityProperties(
                id,
                RouterOsProperties.builder().set("write-access", Boolean.toString(enabled)).build());
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
