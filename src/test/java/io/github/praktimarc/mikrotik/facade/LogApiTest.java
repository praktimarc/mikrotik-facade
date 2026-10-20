package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.log.RouterLogEntry;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class LogApiTest {
    private static final List<String> FOLLOW = List.of("/log/print", "=follow=");
    private static final List<String> FOLLOW_ONLY = List.of("/log/print", "=follow-only=");

    @Test
    void followsHistoricalAndNewEntriesAsTypedStreamWithExactWords() throws Exception {
        FakeConnection c = new FakeConnection();
        try (MikrotikRtrApi api = facade(c)) {
            Flow.Publisher<RouterLogEntry> stream = api.log().follow();
            assertTrue(c.words.isEmpty(), "Cold stream must not execute until subscribed");
            Probe probe = new Probe();
            stream.subscribe(probe);
            await(() -> c.words.size() == 1);
            assertEquals(FOLLOW, c.words.get(0));
            assertEquals(0, c.stringCalls, "No pseudo-command string parser");

            c.listeners.get(0).receive(Map.of(
                    ".id", "*12",
                    "time", "oct/20 15:12:00",
                    "topics", "system, info ,account",
                    "message", "user connected",
                    "extra-property", "retained"));
            c.listeners.get(0).receive(Map.of(
                    "time", "15:13:00",
                    "topics", "script,info",
                    "message", "new log message"));

            await(() -> probe.rows.size() == 2);
            RouterLogEntry first = probe.rows.get(0);
            assertEquals("*12", first.id().orElseThrow());
            assertEquals("oct/20 15:12:00", first.time().orElseThrow());
            assertEquals("system, info ,account", first.topicsText().orElseThrow());
            assertEquals(List.of("system", "info", "account"), first.topics());
            assertEquals("user connected", first.message().orElseThrow());
            assertEquals("retained", first.raw().require("extra-property"));
            assertEquals("15:13:00", probe.rows.get(1).time().orElseThrow());
            assertFalse(probe.completed, "Should keep following until cancelled or completed");
        }
    }

    @Test
    void followOnlyUsesDistinctProtocolFlagAndCancelledSubscriptionLeavesSessionUsable()
            throws Exception {
        FakeConnection c = new FakeConnection();
        try (MikrotikRtrApi api = facade(c)) {
            Probe first = new Probe();
            api.log().followOnly().subscribe(first);
            await(() -> c.words.size() == 1);
            assertEquals(FOLLOW_ONLY, c.words.get(0));
            first.subscription.cancel();
            await(() -> c.cancelled.size() == 1);
            assertEquals(List.of("log-tag-1"), c.cancelled);

            Probe second = new Probe();
            api.log().followOnly().subscribe(second);
            await(() -> c.words.size() == 2);
            assertEquals(FOLLOW_ONLY, c.words.get(1));
            c.listeners.get(1).receive(Map.of("message", "session still works"));
            await(() -> second.rows.size() == 1);
            assertEquals("session still works", second.rows.get(0).message().orElseThrow());
            second.subscription.cancel();
        }
    }

    @Test
    void missingOptionalFieldsDoNotProduceInventedTimestampsOrTopics() throws Exception {
        FakeConnection c = new FakeConnection();
        try (MikrotikRtrApi api = facade(c)) {
            Probe probe = new Probe();
            api.log().followOnly().subscribe(probe);
            await(() -> c.listeners.size() == 1);
            c.listeners.get(0).receive(Map.of("message", "without timestamp"));
            c.listeners.get(0).receive(Map.of("time", "2026-10-20 15:13:00", "topics", " , , "));
            c.listeners.get(0).completed();
            await(() -> probe.completed);
            assertTrue(probe.rows.get(0).time().isEmpty());
            assertEquals(List.of(), probe.rows.get(0).topics());
            assertTrue(probe.rows.get(0).topicsText().isEmpty());
            assertEquals("without timestamp", probe.rows.get(0).message().orElseThrow());
            assertTrue(probe.rows.get(1).message().isEmpty());
            assertEquals("2026-10-20 15:13:00", probe.rows.get(1).time().orElseThrow());
            assertEquals(" , , ", probe.rows.get(1).topicsText().orElseThrow());
            assertEquals(List.of(), probe.rows.get(1).topics());
        }
    }

    @Test
    void multipleSubscribersAreIndependentAndSessionCloseCancelsBoth() throws Exception {
        FakeConnection c = new FakeConnection();
        MikrotikRtrApi api = facade(c);
        Probe a = new Probe(), b = new Probe();
        try {
            var shared = api.log().follow();
            shared.subscribe(a);
            shared.subscribe(b);
            await(() -> c.listeners.size() == 2);
            c.listeners.get(0).receive(Map.of("message", "first only"));
            c.listeners.get(1).receive(Map.of("message", "second only"));
            await(() -> a.rows.size() == 1 && b.rows.size() == 1);
            assertEquals("first only", a.rows.get(0).message().orElseThrow());
            assertEquals("second only", b.rows.get(0).message().orElseThrow());
        } finally {
            api.close();
        }
        assertTrue(c.cancelled.containsAll(List.of("log-tag-1", "log-tag-2")));
    }

    @Test
    void upstreamTrapIsReportedThroughOnError() throws Exception {
        FakeConnection c = new FakeConnection();
        try (MikrotikRtrApi api = facade(c)) {
            Probe probe = new Probe();
            api.log().follow().subscribe(probe);
            await(() -> c.listeners.size() == 1);
            c.listeners.get(0).error(new MikrotikApiException("router rejected follow"));
            await(() -> probe.failure != null);
            assertFalse(probe.completed);
            assertTrue(probe.rows.isEmpty());
        }
    }

    @Test
    void cancellingPreventsLaterCallbacksWithoutRequiringOnComplete() throws Exception {
        FakeConnection c = new FakeConnection();
        try (MikrotikRtrApi api = facade(c)) {
            Probe probe = new Probe();
            api.log().followOnly().subscribe(probe);
            await(() -> c.listeners.size() == 1);
            probe.subscription.cancel();
            await(() -> !c.cancelled.isEmpty());
            c.listeners.get(0).receive(Map.of("message", "must not arrive"));
            c.listeners.get(0).completed();
            assertTrue(probe.rows.isEmpty());
            assertFalse(probe.completed);
            assertNull(probe.failure);
        }
    }

    private static MikrotikRtrApi facade(FakeConnection connection) {
        return new MikrotikRtrApi(
                connection,
                new io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle(),
                RouterOsEnvironment.withPackages(
                        new RouterOsSystemInfo("7.21", "arm64", "test", null, RouterOsRecord.empty()),
                        List.of()), Runnable::run);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(1);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for stream callback");
    }

    private static final class Probe implements Flow.Subscriber<RouterLogEntry> {
        volatile Flow.Subscription subscription;
        final List<RouterLogEntry> rows = new CopyOnWriteArrayList<>();
        volatile Throwable failure;
        volatile boolean completed;

        @Override public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            s.request(Long.MAX_VALUE);
        }
        @Override public void onNext(RouterLogEntry row) { rows.add(row); }
        @Override public void onError(Throwable error) { failure = error; }
        @Override public void onComplete() { completed = true; }
    }

    private static final class FakeConnection extends StubApiConnection {
        final List<List<String>> words = new CopyOnWriteArrayList<>();
        final List<ResultListener> listeners = new CopyOnWriteArrayList<>();
        final List<String> cancelled = new CopyOnWriteArrayList<>();
        volatile int stringCalls;

        @Override public String executeWords(List<String> command, ResultListener listener) {
            int i = words.size() + 1;
            listeners.add(listener);
            words.add(List.copyOf(command));
            return "log-tag-" + i;
        }
        @Override public String execute(String command, ResultListener listener) {
            stringCalls++;
            throw new AssertionError("Typed log stream must use exact words, not string parser");
        }
        @Override public void cancel(String tag) { cancelled.add(tag); }
    }
}
