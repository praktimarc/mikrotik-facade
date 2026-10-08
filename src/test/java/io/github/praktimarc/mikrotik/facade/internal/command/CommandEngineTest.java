package io.github.praktimarc.mikrotik.facade.internal.command;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikTimeoutException;
import io.github.praktimarc.mikrotik.facade.RouterOsQuery;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandEngineTest {

    @Test
    void syncAndAsyncUseSameListenerBasedEngineAndPreserveCompletion() throws Exception {
        FakeConnection connection = new FakeConnection();
        ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
        ExecutorService callback = Executors.newSingleThreadExecutor(named("callback"));
        try {
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    callback,
                    new ManualScheduler());
            RouterOsOperation<String> operation = operation();

            FutureTask<String> sync = new FutureTask<>(
                    () -> engine.executeSync(operation));
            new Thread(sync, "sync-caller").start();
            connection.awaitExecute();
            connection.listener.receive(Map.of("value", "sync"));
            connection.listener.completed(
                    Map.of("ret", "*SYNC", "future", "kept"));
            assertEquals(
                    "sync:*SYNC:kept",
                    sync.get(2, TimeUnit.SECONDS));

            connection.resetForNext();
            CompletableFuture<String> async = engine.executeAsync(operation);
            connection.awaitExecute();
            connection.listener.receive(Map.of("value", "async"));
            connection.listener.completed(
                    Map.of("ret", "*ASYNC", "future", "kept"));
            assertEquals(
                    "async:*ASYNC:kept",
                    async.get(2, TimeUnit.SECONDS));

            assertEquals(2, connection.listenerExecutions.get());
            assertEquals(0, connection.syncExecutions.get());
        } finally {
            dispatch.shutdownNow();
            callback.shutdownNow();
        }
    }

    @Test
    void cancellationBeforeReturnedTagIsRememberedAndPropagated() throws Exception {
        FakeConnection connection = new FakeConnection();
        connection.blockReturnTag = true;
        ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
        try {
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    Runnable::run,
                    new ManualScheduler());

            CompletableFuture<String> future = engine.executeAsync(operation());
            connection.awaitExecute();
            assertTrue(future.cancel(false));
            assertEquals(0, connection.cancelCalls.get());

            connection.allowTagReturn.countDown();
            connection.awaitCancel();

            assertEquals("tag-1", connection.cancelledTag.get());
            assertEquals(1, connection.cancelCalls.get());
        } finally {
            dispatch.shutdownNow();
        }
    }

    @Test
    void cancellationAfterTagUsesLowLevelCancelExactlyOnce() throws Exception {
        FakeConnection connection = new FakeConnection();
        ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
        try {
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    Runnable::run,
                    new ManualScheduler());

            CompletableFuture<String> future = engine.executeAsync(operation());
            connection.awaitExecute();
            connection.awaitTagReturned();

            assertTrue(future.cancel(true));
            connection.awaitCancel();
            connection.listener.completed(Map.of("ret", "late"));

            assertEquals(1, connection.cancelCalls.get());
            assertFalse(future.cancel(false));
            assertEquals(1, connection.cancelCalls.get());
        } finally {
            dispatch.shutdownNow();
        }
    }

    @Test
    void timeoutProducesTimeoutExceptionAndBestEffortCancel() throws Exception {
        FakeConnection connection = new FakeConnection();
        ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
        try {
            ManualScheduler scheduler = new ManualScheduler();
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofMillis(50),
                    dispatch,
                    Runnable::run,
                    scheduler);

            CompletableFuture<String> future = engine.executeAsync(operation());
            connection.awaitExecute();
            connection.awaitTagReturned();
            scheduler.fire();

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> future.get(2, TimeUnit.SECONDS));
            assertInstanceOf(
                    MikrotikTimeoutException.class,
                    failure.getCause());
            connection.awaitCancel();
            assertEquals(1, connection.cancelCalls.get());
        } finally {
            dispatch.shutdownNow();
        }
    }

    @Test
    void competingTerminalSignalsProduceExactlyOneLogicalResult() throws Exception {
        FakeConnection connection = new FakeConnection();
        ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
        try {
            ManualScheduler scheduler = new ManualScheduler();
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    Runnable::run,
                    scheduler);

            CompletableFuture<String> future = engine.executeAsync(operation());
            connection.awaitExecute();
            connection.awaitTagReturned();

            connection.listener.completed(
                    Map.of("ret", "*WIN", "future", "done"));
            scheduler.fire();
            connection.listener.error(
                    new TestCommandException("trap", 4));
            connection.listener.error(
                    new ApiConnectionException("closed"));

            assertEquals(
                    "none:*WIN:done",
                    future.get(2, TimeUnit.SECONDS));
            assertEquals(0, connection.cancelCalls.get());
        } finally {
            dispatch.shutdownNow();
        }
    }

    @Test
    void trapCloseAndConnectionLossAllUseSingleFailureTerminalPath() throws Exception {
        for (MikrotikApiException failure : new MikrotikApiException[]{
                new TestCommandException("trap", 4),
                new ApiConnectionException("closed"),
                new ApiConnectionException("connection lost")}) {

            FakeConnection connection = new FakeConnection();
            ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
            try {
                CommandEngine engine = new CommandEngine(
                        connection,
                        Duration.ofSeconds(10),
                        dispatch,
                        Runnable::run,
                        new ManualScheduler());

                CompletableFuture<String> future = engine.executeAsync(operation());
                connection.awaitExecute();
                connection.listener.error(failure);
                connection.listener.completed(Map.of("ret", "late"));

                ExecutionException mapped = assertThrows(
                        ExecutionException.class,
                        () -> future.get(2, TimeUnit.SECONDS));
                assertInstanceOf(
                        MikrotikFacadeException.class,
                        mapped.getCause());
            } finally {
                dispatch.shutdownNow();
            }
        }
    }

    @Test
    void interruptedSyncWaitRestoresInterruptAndBecomesCommandError() throws Exception {
        FakeConnection connection = new FakeConnection();
        ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
        try {
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    Runnable::run,
                    new ManualScheduler());

            AtomicReference<Throwable> thrown = new AtomicReference<>();
            AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
            Thread waiter = new Thread(() -> {
                try {
                    engine.executeSync(operation());
                } catch (Throwable failure) {
                    thrown.set(failure);
                    interrupted.set(
                            Thread.currentThread().isInterrupted());
                }
            }, "sync-waiter");

            waiter.start();
            connection.awaitExecute();
            connection.awaitTagReturned();
            waiter.interrupt();
            waiter.join(2000);

            assertInstanceOf(
                    MikrotikCommandException.class,
                    thrown.get());
            assertTrue(interrupted.get());
            connection.awaitCancel();
        } finally {
            dispatch.shutdownNow();
        }
    }

    @Test
    void lowLevelCallbackRunsNoOperationMappingOrUserFutureCallback() throws Exception {
        FakeConnection connection = new FakeConnection();
        ExecutorService dispatch = Executors.newSingleThreadExecutor(
                named("dispatch-thread"));
        ExecutorService callback = Executors.newSingleThreadExecutor(
                named("callback-thread"));
        try {
            AtomicReference<String> mappingThread = new AtomicReference<>();
            AtomicReference<String> userThread = new AtomicReference<>();
            AtomicReference<String> completionThread = new AtomicReference<>();
            CountDownLatch userDone = new CountDownLatch(1);

            RouterOsOperation<String> operation =
                    new RouterOsOperation<>() {
                @Override
                public String name() {
                    return "threading";
                }

                @Override
                public RouterOsCommand command() {
                    return RouterOsCommand.builder("/x/print").build();
                }

                @Override
                public String map(CommandResult result) {
                    mappingThread.set(
                            Thread.currentThread().getName());
                    return "mapped";
                }
            };

            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    task -> callback.execute(() -> {
                        completionThread.set(Thread.currentThread().getName());
                        task.run();
                    }),
                    new ManualScheduler());

            CompletableFuture<String> future = engine.executeAsync(operation);
            future.thenRun(() -> {
                userThread.set(Thread.currentThread().getName());
                userDone.countDown();
            });

            connection.awaitExecute();
            Thread processor = new Thread(
                    () -> connection.listener.completed(Map.of()),
                    "routeros-processor");
            processor.start();
            processor.join(2000);

            assertEquals(
                    "mapped",
                    future.get(2, TimeUnit.SECONDS));
            assertTrue(userDone.await(2, TimeUnit.SECONDS));
            assertTrue(
                    mappingThread.get().startsWith("dispatch-thread"));
            assertTrue(
                    completionThread.get().startsWith("callback-thread"));
            assertFalse(
                    mappingThread.get().contains("routeros-processor"));
            assertFalse(
                    userThread.get().contains("routeros-processor"));
        } finally {
            dispatch.shutdownNow();
            callback.shutdownNow();
        }
    }



    @Test
    void diagnosticsClassifyRouterOsTrapAsDebugWithoutExposingValues() throws Exception {
        FakeConnection connection = new FakeConnection();
        ExecutorService dispatch = Executors.newSingleThreadExecutor(named("dispatch"));
        try {
            List<String> logs = java.util.Collections.synchronizedList(new ArrayList<>());
            FacadeDiagnostics diagnostics = new FacadeDiagnostics(
                    "session-test",
                    (level, message) -> logs.add(level + ":" + message));
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    Runnable::run,
                    new ManualScheduler(),
                    diagnostics);
            RouterOsOperation<String> operation = new RouterOsOperation<>() {
                @Override
                public String name() {
                    return "raw secure operation";
                }

                @Override
                public RouterOsCommand command() {
                    return RouterOsCommand.builder("/future/service/set")
                            .argument("opaque-secret-field", "UNCLASSIFIED-SECRET")
                            .query(RouterOsQuery.eq("address", "192.0.2.77")
                                    .or(RouterOsQuery.eq("address", "192.0.2.78")))
                            .build();
                }

                @Override
                public String map(CommandResult result) {
                    return "unused";
                }
            };

            CompletableFuture<String> future = engine.executeAsync(operation);
            connection.awaitExecute();
            connection.listener.error(
                    new TestCommandException(
                            "rejected UNCLASSIFIED-SECRET for 192.0.2.77 or 192.0.2.78",
                            4));

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> future.get(2, TimeUnit.SECONDS));
            assertInstanceOf(MikrotikCommandException.class, failure.getCause());

            String all = logs.toString();
            assertTrue(all.contains("session=session-test"));
            assertTrue(all.contains("operation=op-1"));
            assertTrue(all.contains("event=routeros-rejected"));
            assertTrue(all.contains("category=4"));
            assertFalse(all.contains("UNCLASSIFIED-SECRET"));
            assertFalse(all.contains("192.0.2.77"));
            assertFalse(all.contains("192.0.2.78"));
            assertFalse(logs.stream().anyMatch(line -> line.startsWith("ERROR:")));
        } finally {
            dispatch.shutdownNow();
        }
    }
    @Test
    void engineContainsNoFacadeSendLockOrTagAllocator() {
        for (var field : CommandEngine.class.getDeclaredFields()) {
            assertFalse(
                    field.getType()
                            == java.util.concurrent.atomic.AtomicInteger.class);
            assertFalse(
                    field.getName()
                            .toLowerCase()
                            .contains("lock"));
            assertFalse(
                    field.getName()
                            .toLowerCase()
                            .contains("tagallocator"));
        }
    }

    private static RouterOsOperation<String> operation() {
        return new RouterOsOperation<>() {
            @Override
            public String name() {
                return "test operation";
            }

            @Override
            public RouterOsCommand command() {
                return RouterOsCommand.builder("/test/print").build();
            }

            @Override
            public String map(CommandResult result)
                    throws MikrotikFacadeException {
                String value = result.records().isEmpty()
                        ? "none"
                        : result.records().get(0).require("value");
                String ret = result.completion()
                        .find("ret")
                        .orElse("none");
                String future = result.completion()
                        .find("future")
                        .orElse("none");
                return value + ":" + ret + ":" + future;
            }
        };
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> new Thread(
                task,
                prefix + '-' + sequence.incrementAndGet());
    }

    private static final class ManualScheduler
            implements CommandEngine.TimeoutScheduler {
        private Runnable task;
        private volatile boolean cancelled;

        @Override
        public CommandEngine.TimeoutTask schedule(
                Runnable task,
                Duration delay) {
            this.task = task;
            this.cancelled = false;
            return () -> cancelled = true;
        }

        private void fire() {
            if (!cancelled && task != null) {
                task.run();
            }
        }
    }

    private static final class TestCommandException
            extends ApiCommandException {
        private TestCommandException(
                String message,
                Integer category) {
            super(message, "tag", category);
        }
    }

    private static final class FakeConnection
            extends ApiConnection {
        private volatile ResultListener listener;
        private volatile boolean blockReturnTag;
        private volatile CountDownLatch executeEntered =
                new CountDownLatch(1);
        private volatile CountDownLatch tagReturned =
                new CountDownLatch(1);
        private volatile CountDownLatch allowTagReturn =
                new CountDownLatch(1);
        private volatile CountDownLatch cancelCalled =
                new CountDownLatch(1);

        private final AtomicInteger listenerExecutions =
                new AtomicInteger();
        private final AtomicInteger syncExecutions =
                new AtomicInteger();
        private final AtomicInteger cancelCalls =
                new AtomicInteger();
        private final AtomicReference<String> cancelledTag =
                new AtomicReference<>();

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void login(String username, String password) {
        }

        @Override
        public List<Map<String, String>> execute(String command) {
            syncExecutions.incrementAndGet();
            throw new AssertionError(
                    "CommandEngine must not use low-level execute(String)");
        }

        @Override
        public String execute(
                String command,
                ResultListener listener)
                throws MikrotikApiException {
            listenerExecutions.incrementAndGet();
            this.listener = listener;
            executeEntered.countDown();

            if (blockReturnTag) {
                try {
                    allowTagReturn.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ApiConnectionException(
                            "interrupted",
                            interrupted);
                }
            }

            tagReturned.countDown();
            return "tag-1";
        }

        @Override
        public long downloadFile(
                String remoteFile,
                Path localFile)
                throws IOException {
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
        }

        private void awaitExecute()
                throws InterruptedException {
            assertTrue(
                    executeEntered.await(
                            2,
                            TimeUnit.SECONDS));
        }

        private void awaitTagReturned()
                throws InterruptedException {
            assertTrue(
                    tagReturned.await(
                            2,
                            TimeUnit.SECONDS));
        }

        private void awaitCancel()
                throws InterruptedException {
            assertTrue(
                    cancelCalled.await(
                            2,
                            TimeUnit.SECONDS));
        }

        private void resetForNext() {
            executeEntered = new CountDownLatch(1);
            tagReturned = new CountDownLatch(1);
            allowTagReturn = new CountDownLatch(1);
            cancelCalled = new CountDownLatch(1);
            listener = null;
            blockReturnTag = false;
        }
    }
}
