package io.github.praktimarc.mikrotik.facade.files.internal;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFileException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FileDownloadExecutorTest {
    @Test
    void transferRunsOnBlockingWorkerAndCompletionRunsOnCallbackExecutor() throws Exception {
        ExecutorService callback = Executors.newSingleThreadExecutor(named("callback"));
        AtomicReference<String> completionThread = new AtomicReference<>();
        FileDownloadExecutor executor = new FileDownloadExecutor(
                task -> callback.execute(() -> {
                    completionThread.set(Thread.currentThread().getName());
                    task.run();
                }),
                named("download"), 1, 2);
        try {
            AtomicReference<String> workThread = new AtomicReference<>();

            CompletableFuture<String> future = executor.submit(() -> {
                workThread.set(Thread.currentThread().getName());
                return "ok";
            });

            assertEquals("ok", future.get(2, TimeUnit.SECONDS));
            assertTrue(workThread.get().startsWith("download"));
            assertTrue(completionThread.get().startsWith("callback"));
        } finally {
            executor.beginClose();
            executor.finishClose();
            callback.shutdownNow();
        }
    }

    @Test
    void boundedQueueRejectsExcessAndCloseFailsQueuedWork() throws Exception {
        FileDownloadExecutor executor = new FileDownloadExecutor(Runnable::run, named("download"), 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> running = executor.submit(() -> {
            started.countDown();
            release.await();
            return "done";
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<String> queued = executor.submit(() -> "queued");
        CompletableFuture<String> rejected = executor.submit(() -> "rejected");

        ExecutionException queueFailure = assertThrows(ExecutionException.class, rejected::get);
        assertInstanceOf(MikrotikFileException.class, queueFailure.getCause());

        executor.beginClose();
        ExecutionException closingFailure = assertThrows(ExecutionException.class, queued::get);
        assertInstanceOf(MikrotikConnectionException.class, closingFailure.getCause());

        release.countDown();
        assertEquals("done", running.get(2, TimeUnit.SECONDS));
        executor.finishClose();
    }

    @Test
    void publicCancellationInterruptsLocalWorkerWithoutClaimingRemoteCancel() throws Exception {
        FileDownloadExecutor executor = new FileDownloadExecutor(Runnable::run, named("download"), 1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CompletableFuture<String> future = executor.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return "late";
        });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(future.cancel(true));
        assertTrue(future.isCancelled());
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));

        executor.beginClose();
        executor.finishClose();
    }

    private static ThreadFactory named(String prefix) {
        return task -> {
            Thread thread = new Thread(task, prefix + "-1");
            thread.setDaemon(true);
            return thread;
        };
    }
}
