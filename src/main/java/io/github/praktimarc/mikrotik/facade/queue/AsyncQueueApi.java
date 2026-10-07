package io.github.praktimarc.mikrotik.facade.queue;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.queue.type.AsyncQueueTypeApi;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Asynchronous RouterOS queue facade. */
public final class AsyncQueueApi {
    private final AsyncQueueTypeApi type;

    /** Creates the asynchronous queue facade for session wiring. */
    public AsyncQueueApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.type = new AsyncQueueTypeApi(engine, lifecycle, callbackExecutor);
    }

    /** Returns the asynchronous typed queue-type API. */
    public AsyncQueueTypeApi type() {
        return type;
    }
}
