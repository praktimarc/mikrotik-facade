package io.github.praktimarc.mikrotik.facade.dhcp;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.dhcp.internal.DhcpLeaseMapper;
import io.github.praktimarc.mikrotik.facade.dhcp.internal.DhcpPoolMapper;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the typed DHCP server facade. */
public final class AsyncDhcpServerApi {

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final DhcpLeaseMapper mapper;
    private final DhcpPoolMapper poolMapper;

    /**
     * Creates the asynchronous DHCP facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param callbackExecutor public completion executor
     */
    public AsyncDhcpServerApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new DhcpLeaseMapper(), new DhcpPoolMapper());
    }

    AsyncDhcpServerApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            DhcpLeaseMapper mapper) {
        this(engine, lifecycle, callbackExecutor, mapper, new DhcpPoolMapper());
    }

    AsyncDhcpServerApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            DhcpLeaseMapper mapper,
            DhcpPoolMapper poolMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.poolMapper = Objects.requireNonNull(poolMapper, "poolMapper");
    }

    /** Finds one lease by exact MAC address. */
    public CompletableFuture<Optional<DhcpLease>> findLeaseByMac(String macAddress) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpServerApi.findByMacOperation(macAddress, mapper));
    }

    /** Finds one lease by exact RouterOS address value. */
    public CompletableFuture<Optional<DhcpLease>> findLeaseByAddress(String address) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpServerApi.findByAddressOperation(address, mapper));
    }

    /** Lists all configured RouterOS IP pools. */
    public CompletableFuture<List<DhcpPool>> pools() {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpServerApi.poolsOperation(poolMapper));
    }

    /** Counts leases matching exact RouterOS properties through count-only. */
    public CompletableFuture<Long> countLeases(RouterOsProperties queries) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpServerApi.countLeasesOperation(queries));
    }

    /** Removes one exact DHCP lease id. */
    public CompletableFuture<Void> removeLease(String id) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpServerApi.removeLeaseOperation(id));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
