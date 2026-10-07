package io.github.praktimarc.mikrotik.facade.snmp;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.snmp.internal.SnmpCommunityMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Synchronous typed RouterOS SNMP facade. */
public final class SnmpApi {
    static final String COMMUNITY_PRINT_PATH = "/snmp/community/print";
    static final String COMMUNITY_SET_PATH = "/snmp/community/set";

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final SnmpCommunityMapper mapper;

    /** Creates the SNMP facade for session wiring. */
    public SnmpApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new SnmpCommunityMapper());
    }

    SnmpApi(CommandEngine engine, SessionLifecycle lifecycle, SnmpCommunityMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Lists all RouterOS SNMP communities. */
    public List<SnmpCommunity> communities() throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(communitiesOperation(mapper));
    }

    /**
     * Finds exactly one SNMP community by name.
     *
     * <p>The lookup intentionally filters the typed result locally instead of placing
     * the community name in a RouterOS query, so credential-like community values are
     * not part of command diagnostics.</p>
     */
    public Optional<SnmpCommunity> findCommunityByName(String name) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(findCommunityByNameOperation(requireNonBlank(name, "name"), mapper));
    }

    /** Updates generic properties on one exact SNMP community id. */
    public void setCommunityProperties(String id, RouterOsProperties properties)
            throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        engine.executeSync(setCommunityPropertiesOperation(id, properties));
    }

    /** Updates the stable RouterOS {@code write-access} property. */
    public void setWriteAccess(String id, boolean enabled) throws MikrotikFacadeException {
        setCommunityProperties(
                id,
                RouterOsProperties.builder().set("write-access", Boolean.toString(enabled)).build());
    }

    static RouterOsOperation<List<SnmpCommunity>> communitiesOperation(SnmpCommunityMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        RouterOsCommand command = RouterOsCommand.builder(COMMUNITY_PRINT_PATH).build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "list SNMP communities"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public List<SnmpCommunity> map(CommandResult result) throws MikrotikFacadeException {
                ArrayList<SnmpCommunity> mapped = new ArrayList<>(result.records().size());
                for (RouterOsRecord record : result.records()) mapped.add(mapper.map(record));
                return List.copyOf(mapped);
            }
        };
    }

    static RouterOsOperation<Optional<SnmpCommunity>> findCommunityByNameOperation(
            String name,
            SnmpCommunityMapper mapper) {
        String expected = requireNonBlank(name, "name");
        Objects.requireNonNull(mapper, "mapper");
        RouterOsCommand command = RouterOsCommand.builder(COMMUNITY_PRINT_PATH).build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "find SNMP community by name"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public Optional<SnmpCommunity> map(CommandResult result) throws MikrotikFacadeException {
                SnmpCommunity match = null;
                for (RouterOsRecord record : result.records()) {
                    SnmpCommunity candidate = mapper.map(record);
                    if (!expected.equals(candidate.name())) continue;
                    if (match != null) {
                        throw new MikrotikDataException(
                                "Expected at most one SNMP community with the requested name");
                    }
                    match = candidate;
                }
                return Optional.ofNullable(match);
            }
        };
    }

    static RouterOsOperation<Void> setCommunityPropertiesOperation(
            String id,
            RouterOsProperties properties) {
        String exactId = requireNonBlank(id, "id");
        Objects.requireNonNull(properties, "properties");
        if (properties.asMap().isEmpty()) {
            throw new IllegalArgumentException("properties must not be empty");
        }
        if (properties.asMap().containsKey(".id")) {
            throw new IllegalArgumentException("properties must not override .id");
        }
        RouterOsCommand.Builder builder = RouterOsCommand.builder(COMMUNITY_SET_PATH)
                .argument(".id", exactId);
        properties.asMap().forEach(builder::argument);
        RouterOsCommand command = builder.build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "set SNMP community properties"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public Void map(CommandResult result) { return null; }
        };
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
