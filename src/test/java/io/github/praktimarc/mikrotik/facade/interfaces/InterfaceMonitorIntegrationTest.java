package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.internal.stream.StreamRegistry;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceMonitorIntegrationTest {
    @Test
    void monitorIsColdMapsSamplesAndCancellationUsesRouterOsTag() throws Exception {
        FakeConnection connection = new FakeConnection();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CommandEngine engine = new CommandEngine(
                    connection, Duration.ofSeconds(5), Runnable::run, Runnable::run, scheduler);
            InterfacesApi api = new InterfacesApi(
                    connection, engine, new SessionLifecycle(), Runnable::run, Runnable::run, new StreamRegistry());

            Flow.Publisher<InterfaceMonitorEntry> publisher = api.monitor("ether1");
            assertEquals(0, connection.executions.get());

            RecordingSubscriber subscriber = new RecordingSubscriber();
            publisher.subscribe(subscriber);
            assertEquals(1, connection.executions.get());
            assertTrue(connection.command.contains("/interface/monitor-traffic"));
            assertTrue(connection.command.contains("interface='ether1'"));

            subscriber.subscription.request(1);
            connection.listener.receive(Map.of("name", "ether1", "rx-bits-per-second", "777"));
            assertEquals(777L, subscriber.items.get(0).rxBitsPerSecond().orElseThrow());

            subscriber.subscription.cancel();
            assertEquals(1, connection.cancels.get());
            assertEquals("tag-1", connection.cancelTag);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void sessionRegistryCancellationReachesActiveMonitor() throws Exception {
        FakeConnection connection = new FakeConnection();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            StreamRegistry registry = new StreamRegistry();
            CommandEngine engine = new CommandEngine(
                    connection, Duration.ofSeconds(5), Runnable::run, Runnable::run, scheduler);
            InterfacesApi api = new InterfacesApi(
                    connection, engine, new SessionLifecycle(), Runnable::run, Runnable::run, registry);
            RecordingSubscriber subscriber = new RecordingSubscriber();
            api.monitor("ether1").subscribe(subscriber);

            registry.cancelActive();

            assertEquals(1, connection.cancels.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static final class RecordingSubscriber implements Flow.Subscriber<InterfaceMonitorEntry> {
        private Flow.Subscription subscription;
        private final List<InterfaceMonitorEntry> items = new ArrayList<>();
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; }
        @Override public void onNext(InterfaceMonitorEntry item) { items.add(item); }
        @Override public void onError(Throwable throwable) { fail(throwable); }
        @Override public void onComplete() {}
    }

    private static final class FakeConnection extends ApiConnection {
        private final AtomicInteger executions = new AtomicInteger();
        private final AtomicInteger cancels = new AtomicInteger();
        private ResultListener listener;
        private String command;
        private String cancelTag;

        @Override
        public String execute(String command, ResultListener listener) throws MikrotikApiException {
            this.command = command;
            this.listener = listener;
            return "tag-" + executions.incrementAndGet();
        }

        @Override
        public void cancel(String tag) {
            cancelTag = tag;
            cancels.incrementAndGet();
        }

        @Override public void close() {}
    }
}
