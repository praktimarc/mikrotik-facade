package io.github.praktimarc.mikrotik.facade.ip.dhcpserver.lease;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.ip.dhcpserver.lease.internal.DhcpLeaseMapper;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the typed DHCP server facade. */
public final class AsyncDhcpLeaseApi {

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final DhcpLeaseMapper mapper;

    /**
     * Creates the asynchronous DHCP facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param callbackExecutor public completion executor
     */
    public AsyncDhcpLeaseApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new DhcpLeaseMapper());
    }

    AsyncDhcpLeaseApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            DhcpLeaseMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Finds one lease by exact MAC address. */
    public CompletableFuture<Optional<DhcpLease>> findByMac(String macAddress) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpLeaseApi.findByMacOperation(macAddress, mapper));
    }

    /** Finds one lease by exact RouterOS address value. */
    public CompletableFuture<Optional<DhcpLease>> findByAddress(String address) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpLeaseApi.findByAddressOperation(address, mapper));
    }

    /** Counts leases matching exact RouterOS properties through count-only. */
    public CompletableFuture<Long> count(RouterOsProperties queries) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpLeaseApi.countLeasesOperation(queries));
    }

    /** Removes one exact DHCP lease id. */
    public CompletableFuture<Void> remove(String id) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(DhcpLeaseApi.removeLeaseOperation(id));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
