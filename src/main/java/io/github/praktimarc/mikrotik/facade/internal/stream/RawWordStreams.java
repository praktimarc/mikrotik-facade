package io.github.praktimarc.mikrotik.facade.internal.stream;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

/**
 * Session-scoped cold publisher factory for exact RouterOS API words.
 * Finite RawApi.words() remains intentionally separate from continuous streaming.
 */
public final class RawWordStreams {
    /** Maximum number of undelivered RouterOS records per subscription. */
    public static final int QUEUE_CAPACITY = 256;

    private final ApiConnection connection;
    private final SessionLifecycle lifecycle;
    private final Executor dispatchExecutor;
    private final Executor callbackExecutor;
    private final StreamRegistry registry;

    public RawWordStreams(
            ApiConnection connection,
            SessionLifecycle lifecycle,
            Executor dispatchExecutor,
            Executor callbackExecutor,
            StreamRegistry registry) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.dispatchExecutor = Objects.requireNonNull(dispatchExecutor, "dispatchExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Returns a cold publisher for a complete, immutable native API sentence.
     * No RouterOS I/O is performed until subscription. Every subscription
     * starts its own tagged command and must be cancelled independently.
     */
    public Flow.Publisher<RouterOsRecord> streamWords(List<String> words)
            throws MikrotikConnectionException {
        RouterOsCommand command = RouterOsCommand.fromWireWords(words);
        lifecycle.ensureOpen();
        RouterOsPublisher<RouterOsRecord> delegate = new RouterOsPublisher<>(
                connection, "stream exact RouterOS API words", command,
                QUEUE_CAPACITY, dispatchExecutor, callbackExecutor, record -> record);

        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber");
            delegate.subscribe(new RegisteredStreamSubscriber<>(subscriber, registry));
        };
    }
}
