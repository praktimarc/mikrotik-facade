package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.raw.RawCommandResult;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawApiIntegrationTest {

    @Test
    void rawReadUsesSharedListenerEngine() throws Exception {
        try (Fixture fixture = new Fixture(Runnable::run)) {
            FutureTask<List<RouterOsRecord>> call = new FutureTask<>(
                    () -> fixture.api.raw().execute("/ip/route/print"));
            new Thread(call, "raw-sync-caller").start();

            fixture.connection.awaitExecute();
            fixture.connection.listener.receive(Map.of("dst-address", "192.0.2.0/24"));
            fixture.connection.listener.completed(Map.of());

            assertEquals("192.0.2.0/24", call.get(2, TimeUnit.SECONDS).get(0).require("dst-address"));
            assertEquals(1, fixture.connection.listenerExecutions.get());
            assertEquals(0, fixture.connection.syncExecutions.get());
        }
    }

    @Test
    void rawWriteCompletionExposesRet() throws Exception {
        try (Fixture fixture = new Fixture(Runnable::run)) {
            FutureTask<RawCommandResult> call = new FutureTask<>(
                    () -> fixture.api.raw()
                            .command("/ip/firewall/address-list/add")
                            .argument("list", "blocked")
                            .argument("address", "192.0.2.10")
                            .execute());
            new Thread(call, "raw-write-caller").start();

            fixture.connection.awaitExecute();
            fixture.connection.listener.completed(Map.of("ret", "*A"));

            RawCommandResult result = call.get(2, TimeUnit.SECONDS);
            assertTrue(result.records().isEmpty());
            assertEquals("*A", result.completion().require("ret"));
        }
    }

    @Test
    void unknownFieldsSurviveRawRead() throws Exception {
        try (Fixture fixture = new Fixture(Runnable::run)) {
            FutureTask<List<RouterOsRecord>> call = new FutureTask<>(
                    () -> fixture.api.raw().execute("/future/subsystem/print"));
            new Thread(call, "raw-unknown-caller").start();

            fixture.connection.awaitExecute();
            fixture.connection.listener.receive(Map.of("unknown-future-property", "kept"));
            fixture.connection.listener.completed(Map.of("future-done-property", "also-kept"));

            assertEquals("kept", call.get(2, TimeUnit.SECONDS).get(0)
                    .require("unknown-future-property"));
        }
    }

    @Test
    void rawExecutionDoesNotPerformTypedCapabilityFiltering() throws Exception {
        try (Fixture fixture = new Fixture(Runnable::run)) {
            FutureTask<List<RouterOsRecord>> call = new FutureTask<>(
                    () -> fixture.api.raw().execute("/future/not-in-catalog/print"));
            new Thread(call, "raw-capability-caller").start();

            fixture.connection.awaitExecute();
            assertTrue(fixture.connection.lastCommand.get()
                    .startsWith("/future/not-in-catalog/print"));
            fixture.connection.listener.completed(Map.of());
            call.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void unsupportedRawCommandProducesNormalCommandException() throws Exception {
        try (Fixture fixture = new Fixture(Runnable::run)) {
            FutureTask<List<RouterOsRecord>> call = new FutureTask<>(
                    () -> fixture.api.raw().execute("/not/supported/print"));
            new Thread(call, "raw-unsupported-caller").start();

            fixture.connection.awaitExecute();
            fixture.connection.listener.error(new TestCommandException("no such command", 0));

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> call.get(2, TimeUnit.SECONDS));
            assertInstanceOf(MikrotikCommandException.class, failure.getCause());
        }
    }

    @Test
    void asyncPublicFutureCompletionOccursThroughCallbackExecutor() throws Exception {
        ExecutorService callback = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "task8-callback"));
        try (Fixture fixture = new Fixture(callback)) {
            CompletableFuture<List<RouterOsRecord>> future =
                    fixture.api.async().raw().execute("/ip/route/print");
            fixture.connection.awaitExecute();

            AtomicReference<String> callbackThread = new AtomicReference<>();
            CountDownLatch callbackDone = new CountDownLatch(1);
            future.thenRun(() -> {
                callbackThread.set(Thread.currentThread().getName());
                callbackDone.countDown();
            });

            Thread processor = new Thread(() -> {
                fixture.connection.listener.receive(Map.of("dst-address", "0.0.0.0/0"));
                fixture.connection.listener.completed(Map.of());
            }, "routeros-processor");
            processor.start();
            processor.join(2000);

            assertEquals(1, future.get(2, TimeUnit.SECONDS).size());
            assertTrue(callbackDone.await(2, TimeUnit.SECONDS));
            assertTrue(callbackThread.get().startsWith("task8-callback"));
            assertFalse(callbackThread.get().contains("routeros-processor"));
        } finally {
            callback.shutdownNow();
        }
    }

    @Test
    void controlledCloseRejectsSyncAndAsyncOperationsSynchronously() throws Exception {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.api.close();

        assertThrows(
                IllegalStateException.class,
                () -> fixture.api.raw().execute("/ip/route/print"));
        assertThrows(
                IllegalStateException.class,
                () -> fixture.api.async().raw().execute("/ip/route/print"));
        assertEquals(0, fixture.connection.listenerExecutions.get());
    }

    @Test
    void brokenSessionUsesConnectionFailureSemantics() throws Exception {
        try (Fixture fixture = new Fixture(Runnable::run)) {
            ApiConnectionException lowLevel = new ApiConnectionException("fatal link loss");
            fixture.lifecycle.connectionLost(lowLevel);

            MikrotikConnectionException syncFailure = assertThrows(
                    MikrotikConnectionException.class,
                    () -> fixture.api.raw().execute("/ip/route/print"));
            assertSame(lowLevel, syncFailure.getCause());

            CompletableFuture<List<RouterOsRecord>> async =
                    fixture.api.async().raw().execute("/ip/route/print");
            ExecutionException asyncFailure = assertThrows(
                    ExecutionException.class,
                    () -> async.get(2, TimeUnit.SECONDS));
            MikrotikConnectionException mapped = assertInstanceOf(
                    MikrotikConnectionException.class,
                    asyncFailure.getCause());
            assertSame(lowLevel, mapped.getCause());
            assertEquals(0, fixture.connection.listenerExecutions.get());
        }
    }

    @Test
    void asyncConvenienceCancellationStillReachesLowLevelTag() throws Exception {
        try (Fixture fixture = new Fixture(Runnable::run)) {
            CompletableFuture<List<RouterOsRecord>> future =
                    fixture.api.async().raw().execute("/ip/route/print");
            fixture.connection.awaitExecute();
            fixture.connection.awaitTagReturned();

            assertTrue(future.cancel(false));
            assertTrue(fixture.connection.cancelCalled.await(2, TimeUnit.SECONDS));
            assertEquals("tag-1", fixture.connection.cancelledTag.get());
            assertEquals(1, fixture.connection.cancelCalls.get());
        }
    }

    @Test
    void controlledCloseBestEffortCancelsActiveAsyncOperation() throws Exception {
        Fixture fixture = new Fixture(Runnable::run);
        CompletableFuture<List<RouterOsRecord>> future =
                fixture.api.async().raw().execute("/ip/route/print");
        fixture.connection.awaitExecute();
        fixture.connection.awaitTagReturned();

        fixture.api.close();

        assertTrue(fixture.connection.cancelCalled.await(2, TimeUnit.SECONDS));
        assertEquals("tag-1", fixture.connection.cancelledTag.get());
        assertEquals(1, fixture.connection.cancelCalls.get());
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void callerProvidedCallbackExecutorRemainsCallerOwned() throws Exception {
        ExecutorService callback = Executors.newSingleThreadExecutor();
        try {
            Fixture fixture = new Fixture(callback);
            fixture.api.close();
            assertFalse(callback.isShutdown());
        } finally {
            callback.shutdownNow();
        }
    }

    private static RouterOsEnvironment environment() {
        return RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo(
                        "7.20.2",
                        "arm64",
                        "RB5009UG+S+",
                        null,
                        RouterOsRecord.of(Map.of("version", "7.20.2"))),
                List.of());
    }

    private static final class Fixture implements AutoCloseable {
        private final FakeConnection connection = new FakeConnection();
        private final SessionLifecycle lifecycle = new SessionLifecycle();
        private final MikrotikRtrApi api;

        private Fixture(java.util.concurrent.Executor callbackExecutor) {
            api = new MikrotikRtrApi(
                    connection,
                    lifecycle,
                    environment(),
                    callbackExecutor);
        }

        @Override
        public void close() throws MikrotikConnectionException {
            api.close();
        }
    }

    private static final class TestCommandException extends ApiCommandException {
        private static final long serialVersionUID = 1L;

        private TestCommandException(String message, Integer category) {
            super(message, "tag-1", category);
        }
    }

    private static final class FakeConnection extends ApiConnection {
        private volatile ResultListener listener;
        private volatile CountDownLatch executeEntered = new CountDownLatch(1);
        private volatile CountDownLatch tagReturned = new CountDownLatch(1);
        private final CountDownLatch cancelCalled = new CountDownLatch(1);
        private final AtomicInteger listenerExecutions = new AtomicInteger();
        private final AtomicInteger syncExecutions = new AtomicInteger();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private final AtomicReference<String> cancelledTag = new AtomicReference<>();
        private final AtomicReference<String> lastCommand = new AtomicReference<>();
        private volatile boolean connected = true;

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void login(String username, String password) {
        }

        @Override
        public List<Map<String, String>> execute(String command) {
            syncExecutions.incrementAndGet();
            throw new AssertionError("Raw facade must use listener-based CommandEngine");
        }

        @Override
        public String execute(String command, ResultListener listener) {
            lastCommand.set(command);
            this.listener = listener;
            listenerExecutions.incrementAndGet();
            executeEntered.countDown();
            tagReturned.countDown();
            return "tag-1";
        }

        @Override
        public long downloadFile(String remoteFile, Path localFile) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(String tag) {
            cancelledTag.set(tag);
            cancelCalls.incrementAndGet();
            cancelCalled.countDown();
        }

        @Override
        public void setTimeout(int timeout) {
        }

        @Override
        public void close() {
            connected = false;
        }

        private void awaitExecute() throws InterruptedException {
            assertTrue(executeEntered.await(2, TimeUnit.SECONDS));
        }

        private void awaitTagReturned() throws InterruptedException {
            assertTrue(tagReturned.await(2, TimeUnit.SECONDS));
        }
    }
}
