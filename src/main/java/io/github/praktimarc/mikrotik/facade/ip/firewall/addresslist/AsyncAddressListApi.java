package io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist.internal.AddressListEntryMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Updates one existing address-list entry by its exact RouterOS item ID.
     *
     * <p>Destructive if properties change behavior. The caller must verify
     * authorization and target identity. The update map must be nonempty
     * and must not contain the reserved {@code .id} key.</p>
     *
     * @param id exact RouterOS item ID, not IP address or list name
     * @param properties fields to update
     * @return cancellable completion future
     */
    public CompletableFuture<Void> setProperties(String id, RouterOsProperties properties) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }
        return engine.executeAsync(AddressListApi.setPropertiesOperation(id, properties));
    }

    /**
     * Removes one existing address-list entry by exact RouterOS item ID.
     *
     * @param id verified item ID
     * @return cancellable completion future
     */
    public CompletableFuture<Void> remove(String id) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }
        return engine.executeAsync(AddressListApi.removeOperation(id));
    }

    /**
     * Asynchronously removes every address-list entry with the exact address
     * text, regardless of which RouterOS lists contain it.
     *
     * <p>Reads all entries, prevalidates all matching IDs, then submits
     * individual removal commands sequentially. The result is {@code 0}
     * if absent or the number of removed entries when all deletes succeed.
     * A failure or cancellation can leave a partially completed operation;
     * it never rolls back successful removals. Cancelling attempts to cancel
     * the currently pending RouterOS command and prevents subsequent commands
     * from being started after cancellation is observed.</p>
     *
     * @param ipAddress exact RouterOS address text, without normalization
     * @return cancellable future containing the count of removed entries
     */
    public CompletableFuture<Integer> removeByIpAddress(String ipAddress) {
        return removeMatching(null, AddressListApi.requireIpAddress(ipAddress));
    }

    /**
     * Removes every entry in one list with the exact address value.
     *
     * <p>Reads the address-list table, checks list and address locally, and
     * validates all matching IDs before issuing sequential deletes.
     * Returns {@code 0} if absent and the count when all matching
     * removals succeed. Cancellation or an error can leave some items already
     * deleted; there is no rollback.</p>
     *
     * @param list exact RouterOS list name
     * @param address exact RouterOS address text
     * @return cancellable future containing the count of removed entries
     */
    public CompletableFuture<Integer> removeByListAndAddress(String list, String address) {
        return removeMatching(AddressListApi.requireListName(list),
                AddressListApi.requireIpAddress(address));
    }

    private CompletableFuture<Integer> removeMatching(String list, String address) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }

        var result = new AddressRemovalFuture();
        CompletableFuture<List<AddressListEntry>> lookup = engine.executeAsync(
                AddressListApi.findOperation(RouterOsProperties.builder().build(), mapper));
        result.track(lookup);

        lookup.whenComplete((entries, lookupError) -> {
            if (result.isDone()) return;
            if (lookupError != null) {
                result.completeExceptionally(unwrap(lookupError));
                return;
            }

            final List<String> ids;
            try {
                ids = list == null
                        ? AddressListApi.idsForIpAddress(address, entries)
                        : AddressListApi.idsForListAndAddress(list, address, entries);
            } catch (MikrotikDataException invalidRecord) {
                result.completeExceptionally(invalidRecord);
                return;
            }
            if (ids.isEmpty()) {
                result.complete(0);
                return;
            }
            if (result.isDone()) return;

            // A separate command per ID. Promise chaining serializes removals:
            // only after one command completes successfully is the next dispatched.
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String id : ids) {
                chain = chain.thenCompose(ignored -> {
                    if (result.isDone()) {
                        return CompletableFuture.failedFuture(
                                new java.util.concurrent.CancellationException(
                                        "Address-list removal no longer active"));
                    }
                    CompletableFuture<Void> removal = engine.executeAsync(
                            AddressListApi.removeOperation(id));
                    result.track(removal);
                    return removal;
                });
            }
            chain.whenComplete((ignored, removalError) -> {
                if (result.isDone()) return;
                if (removalError == null) result.complete(ids.size());
                else result.completeExceptionally(unwrap(removalError));
            });
        });
        return result;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException wrapped && wrapped.getCause() != null
                ? wrapped.getCause() : error;
    }

    private static final class AddressRemovalFuture extends CompletableFuture<Integer> {
        private final AtomicReference<CompletableFuture<?>> active = new AtomicReference<>();

        void track(CompletableFuture<?> operation) {
            active.set(operation);
            if (isCancelled()) operation.cancel(true);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(false);
            if (cancelled) {
                CompletableFuture<?> current = active.get();
                if (current != null) current.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        }
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
