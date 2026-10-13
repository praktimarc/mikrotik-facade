package io.github.praktimarc.mikrotik.facade.ip.dhcpserver.lease;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.ip.dhcpserver.lease.internal.DhcpLeaseMapper;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.compat.EqualityQueryVerifier;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.Objects;
import java.util.Optional;

/** Synchronous typed DHCP server facade. */
public final class DhcpLeaseApi {

    static final String LEASE_PATH = "/ip/dhcp-server/lease/print";
    static final String LEASE_REMOVE_PATH = "/ip/dhcp-server/lease/remove";

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final DhcpLeaseMapper mapper;

    /**
     * Creates the DHCP facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     */
    public DhcpLeaseApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new DhcpLeaseMapper());
    }

    DhcpLeaseApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            DhcpLeaseMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Finds the single DHCP lease with the exact RouterOS {@code mac-address} value.
     *
     * @param macAddress exact RouterOS MAC address value
     * @return empty when no lease matches
     * @throws MikrotikFacadeException when execution or typed mapping fails
     */
    public Optional<DhcpLease> findByMac(String macAddress) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(findByMacOperation(macAddress, mapper));
    }

    /**
     * Finds the single DHCP lease with the exact RouterOS {@code address} value.
     *
     * @param address exact RouterOS lease address value
     * @return empty when no lease matches
     * @throws MikrotikFacadeException when execution or typed mapping fails
     */
    public Optional<DhcpLease> findByAddress(String address) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(findByAddressOperation(address, mapper));
    }

    /**
     * Counts DHCP leases matching exact RouterOS properties.
     *
     * <p>The operation uses RouterOS {@code count-only} and reads the terminal
     * {@code ret} property structurally.</p>
     */
    public long count(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(countLeasesOperation(queries));
    }

    /** Removes one exact DHCP lease id. */
    public void remove(String id) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        engine.executeSync(removeLeaseOperation(id));
    }

    static RouterOsOperation<Optional<DhcpLease>> findByMacOperation(String macAddress, DhcpLeaseMapper mapper) {
        return findOneOperation("find DHCP lease by MAC", "mac-address", requireNonBlank(macAddress, "macAddress"), mapper);
    }

    static RouterOsOperation<Optional<DhcpLease>> findByAddressOperation(String address, DhcpLeaseMapper mapper) {
        return findOneOperation("find DHCP lease by address", "address", requireNonBlank(address, "address"), mapper);
    }

    static RouterOsOperation<Long> countLeasesOperation(RouterOsProperties queries) {
        Objects.requireNonNull(queries, "queries");
        if (!queries.asMap().isEmpty()) {
            RouterOsCommand.Builder builder = RouterOsCommand.builder(LEASE_PATH);
            queries.asMap().forEach(builder::query);
            RouterOsCommand command = builder.build();
            return new RouterOsOperation<>() {
                @Override public String name() { return "count matching DHCP leases"; }
                @Override public RouterOsCommand command() { return command; }
                @Override public Long map(CommandResult result) {
                    return result.records().stream()
                            .filter(record -> EqualityQueryVerifier.matches(record, queries))
                            .count();
                }
            };
        }

        RouterOsCommand command = RouterOsCommand.builder(LEASE_PATH).flag("count-only").build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "count all DHCP leases"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public Long map(CommandResult result) throws MikrotikFacadeException {
                long count = result.completion().requireLong("ret");
                if (count < 0) {
                    throw new MikrotikDataException("RouterOS DHCP lease count must not be negative");
                }
                return count;
            }
        };
    }

    static RouterOsOperation<Void> removeLeaseOperation(String id) {
        String exactId = requireNonBlank(id, "id");
        RouterOsCommand command = RouterOsCommand.builder(LEASE_REMOVE_PATH)
                .argument(".id", exactId)
                .build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "remove DHCP lease"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public Void map(CommandResult result) { return null; }
        };
    }

    private static RouterOsOperation<Optional<DhcpLease>> findOneOperation(
            String name,
            String queryKey,
            String queryValue,
            DhcpLeaseMapper mapper) {
        RouterOsCommand command = RouterOsCommand.builder(LEASE_PATH)
                .query(queryKey, queryValue)
                .build();
        return new RouterOsOperation<>() {
            @Override public String name() { return name; }
            @Override public RouterOsCommand command() { return command; }

            @Override
            public Optional<DhcpLease> map(CommandResult result) throws MikrotikFacadeException {
                RouterOsRecord match = null;
                for (RouterOsRecord record : result.records()) {
                    if (!EqualityQueryVerifier.matches(record, java.util.Map.of(queryKey, queryValue))) {
                        continue;
                    }
                    if (match != null) {
                        throw new MikrotikDataException(
                                "Expected at most one DHCP lease matching the requested property");
                    }
                    match = record;
                }
                return match == null ? Optional.empty() : Optional.of(mapper.map(match));
            }
        };
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
