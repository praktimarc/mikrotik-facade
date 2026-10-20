package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikBackpressureException;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RawStreamWordsApiTest {

    private static final List<String> LOG_WORDS = List.of(
            "/log/print", "=follow-only=", "=.proplist=time,topics,message");

    @Test
    void coldPublisherUsesExactWordsAndStreamsIndependentOrderedReplies() throws Exception {
        FakeConnection connection = new FakeConnection();
        try (MikrotikRtrApi api = facade(connection)) {
            var publisher = api.raw().streamWords(LOG_WORDS);
            assertEquals(0, connection.wordCalls.get(), "No RouterOS I/O before subscribe");

            Probe subscriber = new Probe(true);
            publisher.subscribe(subscriber);
            assertTrue(connection.started.await(3, TimeUnit.SECONDS));
            assertEquals(LOG_WORDS, connection.seenWords.get(0));
            assertEquals(0, connection.commandCalls.get(),
                    "Raw streams must never traverse the pseudo-CLI string parser");

            ResultListener listener = connection.listeners.get(0);
            listener.receive(Map.of("time", "12:00:00", "topics", "system,info", "message", "one"));
            listener.receive(Map.of("time", "12:00:01", "topics", "system,info", "message", "two"));
            await(() -> subscriber.rows.size() == 2);
            assertEquals(List.of("one", "two"), subscriber.messages());
            assertFalse(subscriber.completed);
            assertNull(subscriber.failure);
            subscriber.subscription.cancel();
            assertEquals(List.of("wire-tag-1"), connection.cancelledTags);
        }
    }

    @Test
    void listenerCommandWorksAndEverySubscriptionOwnsItsTag() throws Exception {
        FakeConnection connection = new FakeConnection();
        try (MikrotikRtrApi api = facade(connection)) {
            var pub = api.raw().streamWords("/user/active/listen");
            Probe one = new Probe(true);
            Probe two = new Probe(true);
            pub.subscribe(one);
            pub.subscribe(two);
            awaitCalls(connection, 2);
            assertEquals(List.of("/user/active/listen"), connection.seenWords.get(0));
            assertEquals(List.of("/user/active/listen"), connection.seenWords.get(1));

            connection.listeners.get(0).receive(Map.of(".id", "*A", "name", "operator"));
            connection.listeners.get(1).receive(Map.of(".id", "*A", ".dead", "yes"));
            await(() -> one.rows.size() == 1 && two.rows.size() == 1);
            assertEquals("operator", one.rows.get(0).require("name"));
            assertEquals("yes", two.rows.get(0).require(".dead"));
            one.subscription.cancel();
            assertEquals(List.of("wire-tag-1"), connection.cancelledTags);
            connection.listeners.get(1).receive(Map.of(".id", "*B", "name", "still-active"));
            await(() -> two.rows.size() == 2);
            assertEquals(2, two.rows.size());
            two.subscription.cancel();
            assertEquals(List.of("wire-tag-1", "wire-tag-2"), connection.cancelledTags);
        }
    }

    @Test
    void asyncRawTreeReturnsSameFlowPublisherAndCompletionIsTerminal() throws Exception {
        FakeConnection connection = new FakeConnection();
        try (MikrotikRtrApi api = facade(connection)) {
            Probe subscriber = new Probe(true);
            api.async().raw().streamWords("/log/print", "=follow-only=").subscribe(subscriber);
            assertTrue(connection.started.await(3, TimeUnit.SECONDS));
            assertEquals(List.of("/log/print", "=follow-only="), connection.seenWords.get(0));
            connection.listeners.get(0).receive(Map.of("message", "last"));
            connection.listeners.get(0).completed();
            await(() -> subscriber.completed);
            assertEquals(List.of("last"), subscriber.messages());
            assertNull(subscriber.failure);
        }
    }

    @Test
    void upstreamFailureIsDeliveredAsOnError() throws Exception {
        FakeConnection connection = new FakeConnection();
        try (MikrotikRtrApi api = facade(connection)) {
            Probe subscriber = new Probe(true);
            api.raw().streamWords(LOG_WORDS).subscribe(subscriber);
            assertTrue(connection.started.await(3, TimeUnit.SECONDS));
            connection.listeners.get(0).error(new MikrotikApiException("simulated network failure"));
            await(() -> subscriber.failure != null);
            assertNotNull(subscriber.failure);
            assertFalse(subscriber.completed);
        }
    }

    @Test
    void boundedBufferOverrunIsAnErrorAndRequestsCancel() throws Exception {
        FakeConnection connection = new FakeConnection();
        try (MikrotikRtrApi api = facade(connection)) {
            Probe slow = new Probe(false);
            api.raw().streamWords(LOG_WORDS).subscribe(slow);
            assertTrue(connection.started.await(3, TimeUnit.SECONDS));
            ResultListener listener = connection.listeners.get(0);
            for (int i = 0; i < 257; i++) {
                listener.receive(Map.of("message", Integer.toString(i)));
            }
            await(() -> slow.failure != null);
            assertInstanceOf(MikrotikBackpressureException.class, slow.failure);
            assertTrue(slow.rows.isEmpty(), "Overflow must never silently drop selected records");
            awaitCancels(connection, 1);
        }
    }

    @Test
    void rejectsCallerTagsEmptyWordsAndInvalidCommandPaths() throws Exception {
        FakeConnection connection = new FakeConnection();
        try (MikrotikRtrApi api = facade(connection)) {
            assertThrows(IllegalArgumentException.class,
                    () -> api.raw().streamWords("/log/print", ".tag=caller"));
            assertThrows(IllegalArgumentException.class,
                    () -> api.raw().streamWords("/log/print", ""));
            assertThrows(IllegalArgumentException.class,
                    () -> api.async().raw().streamWords("log/print", "=follow="));
            assertThrows(NullPointerException.class,
                    () -> api.raw().streamWords((List<String>) null));
            assertTrue(connection.seenWords.isEmpty());
        }
    }

    @Test
    void cancellingBeforeTagIsKnownSendsCancelAfterDispatch() throws Exception {
        FakeConnection connection = new FakeConnection();
        connection.blockTag = true;
        try (MikrotikRtrApi api = facade(connection)) {
            Probe subscriber = new Probe(true);
            api.raw().streamWords(LOG_WORDS).subscribe(subscriber);
            assertTrue(connection.started.await(3, TimeUnit.SECONDS));
            subscriber.subscription.cancel();
            assertTrue(connection.cancelledTags.isEmpty());
            connection.releaseTag.countDown();
            awaitCancels(connection, 1);
            assertEquals(List.of("wire-tag-1"), connection.cancelledTags);
            connection.listeners.get(0).receive(Map.of("message", "after-cancel"));
            assertTrue(subscriber.rows.isEmpty());
        } finally {
            connection.releaseTag.countDown();
        }
    }

    @Test
    void facadeCloseCancelsEveryActiveRawSubscription() throws Exception {
        FakeConnection connection = new FakeConnection();
        MikrotikRtrApi api = facade(connection);
        Probe one = new Probe(true);
        Probe two = new Probe(true);
        try {
            api.raw().streamWords("/log/print", "=follow-only=").subscribe(one);
            api.raw().streamWords("/interface/listen").subscribe(two);
            awaitCalls(connection, 2);
        } finally {
            api.close();
        }
        assertEquals(2, connection.cancelledTags.size());
        assertTrue(connection.cancelledTags.containsAll(List.of("wire-tag-1", "wire-tag-2")));
        assertFalse(one.completed);
        assertFalse(two.completed);
    }

    @Test
    void structuredInterfaceMonitorStillUsesOriginalStringCommandPath() throws Exception {
        FakeConnection connection = new FakeConnection();
        try (MikrotikRtrApi api = facade(connection)) {
            var monitor = api.interfaces().monitor("ether1");
            var subscription = new Flow.Subscriber<io.github.praktimarc.mikrotik.facade.interfaces.InterfaceMonitorEntry>() {
                Flow.Subscription current;
                public void onSubscribe(Flow.Subscription sub) {
                    current = sub;
                    sub.request(1);
                }
                public void onNext(io.github.praktimarc.mikrotik.facade.interfaces.InterfaceMonitorEntry value) {
                    current.cancel();
                }
                public void onError(Throwable throwable) {}
                public void onComplete() {}
            };
            monitor.subscribe(subscription);
            awaitStringCalls(connection, 1);
            assertEquals(1, connection.commandCalls.get());
            assertEquals(0, connection.wordCalls.get());
        }
    }

    private static MikrotikRtrApi facade(FakeConnection connection) {
        return new MikrotikRtrApi(
                connection,
                new io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle(),
                RouterOsEnvironment.withPackages(
                        new RouterOsSystemInfo("7.21", "arm64", "test", null, RouterOsRecord.empty()),
                        List.of()),
                Runnable::run);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for Flow callback");
    }

    private static void awaitCalls(FakeConnection connection, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (connection.wordCalls.get() < count && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(count, connection.wordCalls.get());
    }

    private static void awaitStringCalls(FakeConnection connection, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (connection.commandCalls.get() < count && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(count, connection.commandCalls.get());
    }

    private static void awaitCancels(FakeConnection connection, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (connection.cancelledTags.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(count, connection.cancelledTags.size());
    }

    private static final class Probe implements Flow.Subscriber<RouterOsRecord> {
        private final boolean eager;
        volatile Flow.Subscription subscription;
        final List<RouterOsRecord> rows = new CopyOnWriteArrayList<>();
        volatile Throwable failure;
        volatile boolean completed;

        Probe(boolean eager) { this.eager = eager; }
        @Override public void onSubscribe(Flow.Subscription sub) {
            this.subscription = sub;
            if (eager) sub.request(Long.MAX_VALUE);
        }
        @Override public void onNext(RouterOsRecord record) { rows.add(record); }
        @Override public void onError(Throwable error) { failure = error; }
        @Override public void onComplete() { completed = true; }
        List<String> messages() {
            return rows.stream().map(record -> record.find("message").orElse("")).toList();
        }
    }

    private static final class FakeConnection extends StubApiConnection {
        final AtomicInteger wordCalls = new AtomicInteger();
        final AtomicInteger commandCalls = new AtomicInteger();
        final List<List<String>> seenWords = Collections.synchronizedList(new ArrayList<>());
        final List<ResultListener> listeners = Collections.synchronizedList(new ArrayList<>());
        final List<String> cancelledTags = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch releaseTag = new CountDownLatch(1);
        boolean blockTag;

        @Override
        public String executeWords(List<String> words, ResultListener listener) {
            seenWords.add(List.copyOf(words));
            listeners.add(listener);
            int current = wordCalls.incrementAndGet();
            started.countDown();
            if (blockTag) {
                try {
                    if (!releaseTag.await(3, TimeUnit.SECONDS)) {
                        throw new AssertionError("test tag release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("test interrupted", e);
                }
            }
            return "wire-tag-" + current;
        }

        @Override
        public String execute(String command, ResultListener listener) {
            commandCalls.incrementAndGet();
            listeners.add(listener);
            return "legacy-tag-" + commandCalls.get();
        }

        @Override
        public void cancel(String tag) {
            cancelledTags.add(tag);
        }
    }
}
