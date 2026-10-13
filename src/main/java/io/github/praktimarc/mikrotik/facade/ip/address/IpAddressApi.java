package io.github.praktimarc.mikrotik.facade.ip.address;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.compat.EqualityQueryVerifier;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.address.internal.IpAddressV4Mapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Typed RouterOS /ip/address IPv4 assignments. */
public final class IpAddressApi {
    private static final String PATH = "/ip/address/print";
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final IpAddressV4Mapper mapper;

    public IpAddressApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new IpAddressV4Mapper());
    }

    IpAddressApi(CommandEngine engine, SessionLifecycle lifecycle, IpAddressV4Mapper mapper) {
        this.engine = Objects.requireNonNull(engine);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public List<IpAddressV4> list() throws MikrotikFacadeException {
        return list(RouterOsProperties.builder().build());
    }

    public List<IpAddressV4> list(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(listOperation(queries, mapper));
    }

    static RouterOsOperation<List<IpAddressV4>> listOperation(RouterOsProperties queries, IpAddressV4Mapper mapper) {
        Objects.requireNonNull(queries);
        Objects.requireNonNull(mapper);
        RouterOsCommand.Builder builder = RouterOsCommand.builder(PATH);
        queries.asMap().forEach(builder::query);
        RouterOsCommand command = builder.build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "list IPv4 addresses"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public List<IpAddressV4> map(CommandResult result) throws MikrotikFacadeException {
                ArrayList<IpAddressV4> mapped = new ArrayList<>();
                for (RouterOsRecord record : result.records()) {
                    if (EqualityQueryVerifier.matches(record, queries)) {
                        mapped.add(mapper.map(record));
                    }
                }
                return List.copyOf(mapped);
            }
        };
    }
}
