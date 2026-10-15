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

    /**
     * Executes exact RouterOS API words, bypassing the low-level CLI string parser.
     * The first word is the absolute command path. Later words can be arbitrary
     * RouterOS API argument/query words such as {@code ?=base-mac=value}.
     *
     * <p>The connection owns the command correlation tag. This is a powerful
     * diagnostic interface that can also send configuration mutations.</p>
     */
    public RawCommandResult words(List<String> words) throws MikrotikFacadeException {
        return executeCommand(RouterOsCommand.fromWireWords(words));
    }

    /** Convenience form of {@link #words(List)} with the command path first. */
    public RawCommandResult words(String path, String... words) throws MikrotikFacadeException {
        Objects.requireNonNull(words, "words");
        java.util.ArrayList<String> sentence = new java.util.ArrayList<>(words.length + 1);
        sentence.add(path);
        java.util.Collections.addAll(sentence, words);
        return words(sentence);
    }

    RawCommandResult executeCommand(RouterOsCommand command) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(new RawCommandBuilder.ResultOperation(command));
    }
}
