package io.github.praktimarc.mikrotik.facade.queue;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.queue.type.QueueTypeApi;

import java.util.Objects;

/** Synchronous RouterOS queue facade. */
public final class QueueApi {
    private final QueueTypeApi type;

    /** Creates the queue facade for session wiring. */
    public QueueApi(CommandEngine engine, SessionLifecycle lifecycle) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(lifecycle, "lifecycle");
        this.type = new QueueTypeApi(engine, lifecycle);
    }

    /** Returns the typed queue-type API. */
    public QueueTypeApi type() {
        return type;
    }
}
