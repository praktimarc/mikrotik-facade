package io.github.praktimarc.mikrotik.facade.internal.stream;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Tracks one Flow subscription for session-scoped cancellation.
 *
 * <p>Subscriber callback failures cancel only their own RouterOS operation.
 * The session registry removes subscriptions on terminal or explicit cancel.</p>
 */
public final class RegisteredStreamSubscriber<T> implements Flow.Subscriber<T> {
    private final Flow.Subscriber<? super T> target;
    private final StreamRegistry registry;
    private RegisteredSubscription subscription;

    public RegisteredStreamSubscriber(Flow.Subscriber<? super T> target, StreamRegistry registry) {
        this.target = Objects.requireNonNull(target, "target");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override public void onSubscribe(Flow.Subscription upstream) {
        RegisteredSubscription registered = new RegisteredSubscription(upstream, registry);
        subscription = registered;
        registry.register(registered);
        try {
            target.onSubscribe(registered);
        } catch (RuntimeException | Error callbackFailure) {
            registered.cancel();
            throw callbackFailure;
        }
    }

    @Override public void onNext(T item) {
        try {
            target.onNext(item);
        } catch (RuntimeException | Error callbackFailure) {
            RegisteredSubscription current = subscription;
            if (current != null) current.cancel();
            throw callbackFailure;
        }
    }

    @Override public void onError(Throwable failure) {
        unregister();
        target.onError(failure);
    }

    @Override public void onComplete() {
        unregister();
        target.onComplete();
    }

    private void unregister() {
        RegisteredSubscription current = subscription;
        if (current != null) registry.unregister(current);
    }

    private static final class RegisteredSubscription implements Flow.Subscription {
        private final Flow.Subscription upstream;
        private final StreamRegistry registry;
        private boolean cancelled;

        private RegisteredSubscription(Flow.Subscription upstream, StreamRegistry registry) {
            this.upstream = Objects.requireNonNull(upstream, "upstream");
            this.registry = Objects.requireNonNull(registry, "registry");
        }

        @Override public void request(long n) {
            upstream.request(n);
        }

        @Override public synchronized void cancel() {
            if (cancelled) return;
            cancelled = true;
            registry.unregister(this);
            upstream.cancel();
        }
    }
}
