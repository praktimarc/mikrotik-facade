package io.github.praktimarc.mikrotik.facade.system;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.system.internal.PingResultMapper;

import java.time.Duration;
import java.util.Objects;

/** Synchronous RouterOS system/diagnostic facade. */
public final class SystemApi {
    static final String PING_PATH = "/ping";

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final PingResultMapper pingMapper;

    /** Creates the system facade for session wiring. */
    public SystemApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new PingResultMapper());
    }

    SystemApi(CommandEngine engine, SessionLifecycle lifecycle, PingResultMapper pingMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.pingMapper = Objects.requireNonNull(pingMapper, "pingMapper");
    }

    /** Executes one finite router-originated ping request. */
    public PingResult ping(PingRequest request) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(pingOperation(request, pingMapper));
    }

    static RouterOsOperation<PingResult> pingOperation(
            PingRequest request,
            PingResultMapper mapper) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(mapper, "mapper");

        RouterOsCommand.Builder builder = RouterOsCommand.builder(PING_PATH)
                .argument("address", request.address())
                .argument("count", Integer.toString(request.count()));
        request.interval().ifPresent(interval ->
                builder.argument("interval", formatInterval(interval)));
        RouterOsCommand command = builder.build();

        return new RouterOsOperation<>() {
            @Override public String name() { return "ping from router"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public PingResult map(CommandResult result) throws MikrotikFacadeException {
                return mapper.map(result);
            }
        };
    }

    private static String formatInterval(Duration interval) {
        return interval.toMillis() + "ms";
    }
}
