package io.github.praktimarc.mikrotik.facade.raw;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;

/**
 * Synchronous raw RouterOS escape hatch backed by the shared command engine.
 */
public final class RawApi {

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;

    /**
     * Creates the raw API for session wiring.
     *
     * <p>Consumers normally obtain this object from {@code MikrotikRtrApi.raw()}.</p>
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     */
    public RawApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /**
     * Executes a raw RouterOS command path and returns only normal records.
     *
     * <p>Use {@link #command(String)} when arguments, queries, property selection,
     * or terminal completion metadata are needed.</p>
     *
     * @param commandPath RouterOS command path
     * @return immutable list of normal {@code !re} records
     * @throws MikrotikFacadeException if the RouterOS operation fails
     */
    public List<RouterOsRecord> execute(String commandPath) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(new RawCommandBuilder.RecordsOperation(
                RouterOsCommand.builder(commandPath).build()));
    }

    /**
     * Starts a structured raw RouterOS command.
     *
     * @param commandPath RouterOS command path
     * @return fluent raw command builder
     */
    public RawCommandBuilder command(String commandPath) {
        return new RawCommandBuilder(this, commandPath);
    }

    RawCommandResult executeCommand(RouterOsCommand command) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(new RawCommandBuilder.ResultOperation(command));
    }
}
