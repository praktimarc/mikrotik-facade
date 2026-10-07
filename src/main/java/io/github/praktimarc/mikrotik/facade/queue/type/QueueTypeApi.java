package io.github.praktimarc.mikrotik.facade.queue.type;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.queue.type.internal.QueueTypeMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Synchronous typed RouterOS queue-type API. */
public final class QueueTypeApi {
    static final String PRINT_PATH = "/queue/type/print";

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final QueueTypeMapper mapper;

    /** Creates the queue-type API for session wiring. */
    public QueueTypeApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new QueueTypeMapper());
    }

    QueueTypeApi(CommandEngine engine, SessionLifecycle lifecycle, QueueTypeMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Lists every RouterOS queue type. */
    public List<QueueType> list() throws MikrotikFacadeException {
        return find(RouterOsProperties.builder().build());
    }

    /** Lists queue types matching exact RouterOS properties. */
    public List<QueueType> find(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(findOperation(queries, mapper));
    }

    static RouterOsOperation<List<QueueType>> findOperation(
            RouterOsProperties queries,
            QueueTypeMapper mapper) {
        Objects.requireNonNull(queries, "queries");
        Objects.requireNonNull(mapper, "mapper");
        RouterOsCommand.Builder builder = RouterOsCommand.builder(PRINT_PATH);
        queries.asMap().forEach(builder::query);
        RouterOsCommand command = builder.build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "list queue types"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public List<QueueType> map(CommandResult result) throws MikrotikFacadeException {
                ArrayList<QueueType> mapped = new ArrayList<>(result.records().size());
                for (RouterOsRecord record : result.records()) mapped.add(mapper.map(record));
                return List.copyOf(mapped);
            }
        };
    }
}
