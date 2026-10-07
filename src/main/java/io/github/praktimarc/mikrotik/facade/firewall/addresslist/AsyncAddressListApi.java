package io.github.praktimarc.mikrotik.facade.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.firewall.addresslist.internal.AddressListEntryMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the RouterOS firewall address-list facade. */
public final class AsyncAddressListApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final AddressListEntryMapper mapper;

    /**
     * Creates the asynchronous address-list facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param callbackExecutor public completion executor
     */
    public AsyncAddressListApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new AddressListEntryMapper());
    }

    AsyncAddressListApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor, AddressListEntryMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Finds address-list entries by equality properties.
     * @param queries equality-query properties
     * @return future containing matching entries
     */
    public CompletableFuture<List<AddressListEntry>> find(RouterOsProperties queries) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }
        return engine.executeAsync(AddressListApi.findOperation(queries, mapper));
    }

    /** Finds address-list entries by exact list and address.
     * @param list exact list name
     * @param address exact address
     * @return future containing matching entries
     */
    public CompletableFuture<List<AddressListEntry>> findByListAndAddress(String list, String address) {
        RouterOsProperties queries = RouterOsProperties.builder()
                .set("list", Objects.requireNonNull(list, "list"))
                .set("address", Objects.requireNonNull(address, "address"))
                .build();
        return find(queries);
    }

    /** Adds an address-list entry using flexible properties.
     * @param properties exact RouterOS write properties
     * @return future containing optional created item id
     */
    public CompletableFuture<Optional<String>> add(RouterOsProperties properties) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }
        return engine.executeAsync(AddressListApi.addOperation(properties));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
