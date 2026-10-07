package io.github.praktimarc.mikrotik.facade.internal.stream;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamRegistryTest {
    @Test
    void cancelActiveCancelsAndRemovesRegisteredSubscription() {
        StreamRegistry registry = new StreamRegistry();
        CountingSubscription subscription = new CountingSubscription(registry);
        registry.register(subscription);
        assertEquals(1, registry.activeCount());

        registry.cancelActive();

        assertEquals(1, subscription.cancels.get());
        assertEquals(0, registry.activeCount());
    }

    @Test
    void registrationAfterSessionCancellationIsCancelledImmediately() {
        StreamRegistry registry = new StreamRegistry();
        registry.cancelActive();
        CountingSubscription subscription = new CountingSubscription(registry);

        registry.register(subscription);

        assertEquals(1, subscription.cancels.get());
        assertEquals(0, registry.activeCount());
    }

    private static final class CountingSubscription implements Flow.Subscription {
        private final StreamRegistry registry;
        private final AtomicInteger cancels = new AtomicInteger();

        private CountingSubscription(StreamRegistry registry) {
            this.registry = registry;
        }

        @Override public void request(long n) {}
        @Override public void cancel() {
            cancels.incrementAndGet();
            registry.unregister(this);
        }
    }
}
