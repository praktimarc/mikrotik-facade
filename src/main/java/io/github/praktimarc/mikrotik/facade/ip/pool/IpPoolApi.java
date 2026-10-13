package io.github.praktimarc.mikrotik.facade.ip.pool;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.pool.internal.IpPoolMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Typed view of the RouterOS /ip/pool menu, independent of DHCP. */
public final class IpPoolApi {
    private static final String PATH = "/ip/pool/print";
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final IpPoolMapper mapper;

    public IpPoolApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new IpPoolMapper());
    }

    IpPoolApi(CommandEngine engine, SessionLifecycle lifecycle, IpPoolMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public List<IpPool> list() throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(listOperation(mapper));
    }

    static RouterOsOperation<List<IpPool>> listOperation(IpPoolMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        RouterOsCommand command = RouterOsCommand.builder(PATH).build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "list IP pools"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public List<IpPool> map(CommandResult result) throws MikrotikFacadeException {
                ArrayList<IpPool> mapped = new ArrayList<>(result.records().size());
                for (RouterOsRecord record : result.records()) {
                    mapped.add(mapper.map(record));
                }
                return List.copyOf(mapped);
            }
        };
    }
}
