package io.github.praktimarc.mikrotik.facade.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.firewall.addresslist.internal.AddressListEntryMapper;
import io.github.praktimarc.mikrotik.facade.firewall.internal.FirewallOperations;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Synchronous typed RouterOS firewall address-list facade. */
public final class AddressListApi {
    static final String PRINT_PATH = "/ip/firewall/address-list/print";
    static final String ADD_PATH = "/ip/firewall/address-list/add";
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final AddressListEntryMapper mapper;

    /**
     * Creates the address-list facade for session wiring.
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     */
    public AddressListApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new AddressListEntryMapper());
    }

    AddressListApi(CommandEngine engine, SessionLifecycle lifecycle, AddressListEntryMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Finds address-list entries matching all supplied equality properties.
     * @param queries RouterOS equality-query properties; empty means all entries
     * @return immutable ordered matching entries
     * @throws MikrotikFacadeException when execution or typed mapping fails
     */
    public List<AddressListEntry> find(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(findOperation(queries, mapper));
    }

    /**
     * Finds address-list entries by exact list name and address.
     * @param list exact RouterOS {@code list} property
     * @param address exact RouterOS address value
     * @return immutable ordered matching entries
     * @throws MikrotikFacadeException when execution or typed mapping fails
     */
    public List<AddressListEntry> findByListAndAddress(String list, String address) throws MikrotikFacadeException {
        RouterOsProperties queries = RouterOsProperties.builder()
                .set("list", Objects.requireNonNull(list, "list"))
                .set("address", Objects.requireNonNull(address, "address"))
                .build();
        return find(queries);
    }

    /**
     * Adds an address-list entry using flexible RouterOS properties.
     * @param properties exact RouterOS write properties
     * @return optional created RouterOS item id from terminal {@code ret}
     * @throws MikrotikFacadeException when the command fails
     */
    public Optional<String> add(RouterOsProperties properties) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(addOperation(properties));
    }

    static RouterOsOperation<List<AddressListEntry>> findOperation(RouterOsProperties queries, AddressListEntryMapper mapper) {
        return FirewallOperations.find("find firewall address-list entries", PRINT_PATH, queries, mapper::map);
    }

    static RouterOsOperation<Optional<String>> addOperation(RouterOsProperties properties) {
        return FirewallOperations.add("add firewall address-list entry", ADD_PATH, properties);
    }
}
