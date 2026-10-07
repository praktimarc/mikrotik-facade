package io.github.praktimarc.mikrotik.facade.internal.stream;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/** Session-scoped registry of active RouterOS Flow subscriptions. */
public final class StreamRegistry {
    private final Set<Flow.Subscription> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cancelling = new AtomicBoolean();

    /** Registers one active stream subscription. */
    public void register(Flow.Subscription subscription) {
        if (cancelling.get()) {
            subscription.cancel();
            return;
        }
        active.add(subscription);
        if (cancelling.get() && active.remove(subscription)) {
            subscription.cancel();
        }
    }

    /** Removes one terminal or cancelled stream subscription. */
    public void unregister(Flow.Subscription subscription) {
        active.remove(subscription);
    }

    /** Requests best-effort cancellation of every active stream subscription. */
    public void cancelActive() {
        cancelling.set(true);
        active.forEach(Flow.Subscription::cancel);
    }

    int activeCount() {
        return active.size();
    }
}
