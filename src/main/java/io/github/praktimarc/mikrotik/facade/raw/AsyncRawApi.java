package io.github.praktimarc.mikrotik.facade.raw;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.internal.stream.RawWordStreams;
import java.util.concurrent.Flow;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Asynchronous raw RouterOS escape hatch backed by the shared command engine.
 */
public final class AsyncRawApi {

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final RawWordStreams rawWordStreams;

    /**
     * Creates the asynchronous raw API for session wiring.
     *
     * <p>Consumers normally obtain this object from
     * {@code MikrotikRtrApi.async().raw()}.</p>
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param callbackExecutor public completion executor
     */
    public AsyncRawApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, null);
    }

    /** Constructor for fully wired session-scoped finite and streaming raw APIs. */
    public AsyncRawApi(
            CommandEngine engine, SessionLifecycle lifecycle,
            Executor callbackExecutor, RawWordStreams rawWordStreams) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.rawWordStreams = rawWordStreams;
    }

    /**
     * Executes a raw RouterOS command path and returns only normal records.
     *
     * <p>The returned future is the shared engine future directly, so public
     * cancellation retains RouterOS tag cancellation semantics.</p>
     *
     * @param commandPath RouterOS command path
     * @return cancellable future for immutable normal {@code !re} records
     */
    public CompletableFuture<List<RouterOsRecord>> execute(String commandPath) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(new RawCommandBuilder.RecordsOperation(
                RouterOsCommand.builder(commandPath).build()));
    }

    /**
     * Starts a structured asynchronous raw RouterOS command.
     *
     * @param commandPath RouterOS command path
     * @return fluent asynchronous raw command builder
     */
    public RawCommandBuilder.Async command(String commandPath) {
        return new RawCommandBuilder.Async(this, commandPath);
    }

    /**
     * Executes exact RouterOS API words asynchronously on the existing session.
     *
     * <p>See the <a href="https://github.com/praktimarc/mikrotik-facade/blob/main/docs/raw-protocol-words.md">Raw-Words Cookbook</a>
     * for query syntax, result handling, command timeout versus local wait timeout,
     * and best-effort cancellation.</p>
     *
     * @param words complete ordered protocol sentence (without tag or terminator)
     * @return cancellable future of normal records and terminal completion metadata
     */
    public CompletableFuture<RawCommandResult> words(List<String> words) {
        return executeCommand(RouterOsCommand.fromWireWords(words));
    }

    /**
     * Convenience form of {@link #words(List)} with the command path first.
     *
     * @param path absolute RouterOS command path
     * @param words additional exact protocol words
     * @return cancellable future of the raw result
     */
    public CompletableFuture<RawCommandResult> words(String path, String... words) {
        Objects.requireNonNull(words, "words");
        java.util.ArrayList<String> sentence = new java.util.ArrayList<>(words.length + 1);
        sentence.add(path);
        java.util.Collections.addAll(sentence, words);
        return words(sentence);
    }

    /**
     * Mirror of {@link RawApi#streamWords(List)}. The returned Publisher
     * already runs asynchronously and is not a CompletableFuture.
     *
     * @param words complete native RouterOS protocol words
     * @return cold tagged stream with per-subscription cancellation
     * @throws MikrotikConnectionException if the session has failed
     */
    public Flow.Publisher<RouterOsRecord> streamWords(List<String> words)
            throws MikrotikConnectionException {
        if (rawWordStreams == null) {
            throw new IllegalStateException("Raw streaming requires a fully wired facade session");
        }
        return rawWordStreams.streamWords(words);
    }

    /** Convenience form of {@link #streamWords(List)} with path first. */
    public Flow.Publisher<RouterOsRecord> streamWords(String path, String... words)
            throws MikrotikConnectionException {
        Objects.requireNonNull(words, "words");
        java.util.ArrayList<String> sentence = new java.util.ArrayList<>(words.length + 1);
        sentence.add(path);
        java.util.Collections.addAll(sentence, words);
        return streamWords(sentence);
    }

    CompletableFuture<RawCommandResult> executeCommand(RouterOsCommand command) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(new RawCommandBuilder.ResultOperation(command));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
