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
     * Removes every address-list entry whose RouterOS {@code address} property
     * exactly equals the supplied string, across all address lists.
     *
     * <p>Reads the complete table without relying on server-side filtering,
     * then validates every matching {@code .id} before any removal is sent.
     * No match returns {@code false}; one or more successful removals return
     * {@code true}. Missing or duplicate item IDs cause a data error before
     * the first removal. Comparison is textual: no DNS resolution, subnet
     * membership checks, or IP normalization.</p>
     *
     * <p>Removal is <strong>not atomic</strong>. If a RouterOS operation fails
     * after some items have been removed, earlier removals remain applied.
     * A concurrent router change can make a previously read ID stale.
     * To remove from a specific list only, use
     * {@link #removeByListAndAddress(String, String)}.</p>
     *
     * @param ipAddress exact RouterOS address text
     * @return {@code true} if one or more entries were removed,
     *         {@code false} if no matching entries existed
     * @throws IllegalArgumentException if the address is blank
     * @throws MikrotikDataException if any matching entry lacks a usable ID
     *         or two matching rows report the same ID
     * @throws MikrotikFacadeException if a RouterOS operation fails
     */
    public boolean removeByIpAddress(String ipAddress) throws MikrotikFacadeException {
        return removeMatching(null, requireIpAddress(ipAddress));
    }

    /**
     * Removes all entries with an exact list name and address value.
     *
     * <p>Like {@link #removeByIpAddress(String)}, this performs an unfiltered
     * read of all address-list entries, then matches both fields locally
     * with exact string equality. All matching IDs are validated before
     * sending any removal command. Multiple matches <em>within the selected
     * list</em> are all removed; entries in other lists remain unchanged.</p>
     *
     * <p>The remove commands are independent, not atomic. If removal fails
     * partway through, some entries may already have been deleted. No
     * automatic rollback or retry occurs.</p>
     *
     * @param list exact RouterOS address-list name
     * @param address exact RouterOS address text (no CIDR inclusion or normalization)
     * @return {@code true} after all matching entries were removed,
     *         {@code false} if no entries matched
     * @throws IllegalArgumentException if either argument is blank
     * @throws MikrotikDataException if a matching entry has no ID or IDs are duplicated
     * @throws MikrotikFacadeException if reading or removing entries fails
     */
    public boolean removeByListAndAddress(String list, String address)
            throws MikrotikFacadeException {
        return removeMatching(requireListName(list), requireIpAddress(address));
    }

    private boolean removeMatching(String list, String address) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        List<AddressListEntry> entries = engine.executeSync(
                findOperation(RouterOsProperties.builder().build(), mapper));
        List<String> ids = idsForAddressAndList(list, address, entries);
        for (String id : ids) {
            engine.executeSync(removeOperation(id));
        }
        return !ids.isEmpty();
    }

    static String requireListName(String list) {
        Objects.requireNonNull(list, "list");
        if (list.isBlank()) {
            throw new IllegalArgumentException("list must not be blank");
        }
        return list;
    }

    static String requireIpAddress(String ipAddress) {
        Objects.requireNonNull(ipAddress, "ipAddress");
        if (ipAddress.isBlank()) {
            throw new IllegalArgumentException("ipAddress must not be blank");
        }
        return ipAddress;
    }

    static List<String> idsForIpAddress(String ipAddress, List<AddressListEntry> entries)
            throws MikrotikDataException {
        return idsForAddressAndList(null, ipAddress, entries);
    }

    static List<String> idsForListAndAddress(
            String list, String address, List<AddressListEntry> entries)
            throws MikrotikDataException {
        return idsForAddressAndList(requireListName(list), address, entries);
    }

    private static List<String> idsForAddressAndList(
            String list, String address, List<AddressListEntry> entries)
            throws MikrotikDataException {
        String expectedAddress = requireIpAddress(address);
        Objects.requireNonNull(entries, "entries");
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        for (AddressListEntry entry : entries) {
            if (!entry.address().map(expectedAddress::equals).orElse(false)
                    || (list != null && !entry.list().map(list::equals).orElse(false))) {
                continue;
            }
            String id = entry.id().filter(value -> !value.isBlank()).orElseThrow(
                    () -> new MikrotikDataException(
                            "Matching address-list entry has no RouterOS item ID"));
            if (!ids.add(id)) {
                throw new MikrotikDataException(
                        "Duplicate RouterOS item ID in matching address-list entries");
            }
        }
        return List.copyOf(ids);
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
