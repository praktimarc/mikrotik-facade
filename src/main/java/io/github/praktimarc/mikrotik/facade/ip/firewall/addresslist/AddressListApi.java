package io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist.internal.AddressListEntryMapper;
import io.github.praktimarc.mikrotik.facade.ip.firewall.internal.FirewallOperations;
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
    static final String SET_PATH = "/ip/firewall/address-list/set";
    static final String REMOVE_PATH = "/ip/firewall/address-list/remove";
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

    /**
     * Updates one existing address-list entry using its exact RouterOS item ID.
     *
     * <p>This method changes router configuration. Look up and validate the
     * intended item first. It does not perform an atomic read-modify-write or
     * retry on connection failure. The properties must be nonempty and may
     * not contain the reserved {@code .id} key.</p>
     *
     * @param id exact RouterOS item ID (for example {@code *1}), not an IP address
     * @param properties nonempty RouterOS update properties, excluding {@code .id}
     * @throws IllegalArgumentException if the ID is blank, properties are empty,
     *                                  or they contain {@code .id}
     * @throws MikrotikFacadeException if execution fails
     */
    public void setProperties(String id, RouterOsProperties properties) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        engine.executeSync(setPropertiesOperation(id, properties));
    }

    /**
     * Removes one address-list entry by exact RouterOS item ID.
     *
     * <p>Destructive operation: do not pass an IP address, list name, or
     * unverified ID. The caller is responsible for authorization.</p>
     *
     * @param id exact RouterOS item ID
     * @throws IllegalArgumentException if the ID is blank
     * @throws MikrotikFacadeException if removal fails
     */
    public void remove(String id) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        engine.executeSync(removeOperation(id));
    }

    /**
     * Removes an address-list entry matching exactly the supplied RouterOS {@code address} value.
     *
     * <p>This convenience method first reads <em>all</em> address-list entries without
     * relying on potentially unreliable server-side equality filtering.
     * It compares the returned address strings exactly (no DNS lookup, CIDR inclusion,
     * or IP normalization). If no entry matches, no command is sent and {@code false}
     * is returned. If more than one entry matches, it fails without deleting anything:
     * the same address can occur in multiple RouterOS lists.</p>
     *
     * <p>For an ambiguous address, call {@link #findByListAndAddress(String, String)}
     * and then {@link #remove(String)} with a verified RouterOS {@code .id}.
     * The read and remove are separate non-atomic commands; a stale ID or
     * connection failure can still make removal fail.</p>
     *
     * @param ipAddress exact address text as stored in RouterOS
     * @return {@code true} if one matching entry was removed, {@code false} if none existed
     * @throws IllegalArgumentException if the address is blank
     * @throws MikrotikDataException if multiple entries match or the target has no usable item ID
     * @throws MikrotikFacadeException if a RouterOS operation fails
     */
    public boolean removeByIpAddress(String ipAddress) throws MikrotikFacadeException {
        String expected = requireIpAddress(ipAddress);
        lifecycle.ensureOpen();
        List<AddressListEntry> entries = engine.executeSync(
                findOperation(RouterOsProperties.builder().build(), mapper));
        Optional<String> id = uniqueIdForIpAddress(expected, entries);
        if (id.isEmpty()) {
            return false;
        }
        engine.executeSync(removeOperation(id.orElseThrow()));
        return true;
    }

    static String requireIpAddress(String ipAddress) {
        Objects.requireNonNull(ipAddress, "ipAddress");
        if (ipAddress.isBlank()) {
            throw new IllegalArgumentException("ipAddress must not be blank");
        }
        return ipAddress;
    }

    static Optional<String> uniqueIdForIpAddress(String ipAddress, List<AddressListEntry> entries)
            throws MikrotikDataException {
        String expected = requireIpAddress(ipAddress);
        Objects.requireNonNull(entries, "entries");
        AddressListEntry match = null;
        for (AddressListEntry entry : entries) {
            if (!entry.address().map(expected::equals).orElse(false)) {
                continue;
            }
            if (match != null) {
                throw new MikrotikDataException(
                        "Multiple address-list entries have the same address; select a list and item ID");
            }
            match = entry;
        }
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(match.id().filter(id -> !id.isBlank()).orElseThrow(
                () -> new MikrotikDataException("Matching address-list entry has no RouterOS item ID")));
    }

    static RouterOsOperation<List<AddressListEntry>> findOperation(RouterOsProperties queries, AddressListEntryMapper mapper) {
        return FirewallOperations.find("find firewall address-list entries", PRINT_PATH, queries, mapper::map);
    }

    static RouterOsOperation<Optional<String>> addOperation(RouterOsProperties properties) {
        return FirewallOperations.add("add firewall address-list entry", ADD_PATH, properties);
    }

    static RouterOsOperation<Void> setPropertiesOperation(String id, RouterOsProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (properties.asMap().isEmpty()) {
            throw new IllegalArgumentException("properties must not be empty");
        }
        if (properties.asMap().containsKey(".id")) {
            throw new IllegalArgumentException("properties must not override .id");
        }
        return FirewallOperations.set("set firewall address-list properties", SET_PATH, id, properties);
    }

    static RouterOsOperation<Void> removeOperation(String id) {
        return FirewallOperations.remove("remove firewall address-list entry", REMOVE_PATH, id);
    }
}
