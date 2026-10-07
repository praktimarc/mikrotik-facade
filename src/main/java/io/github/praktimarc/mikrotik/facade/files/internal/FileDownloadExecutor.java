package io.github.praktimarc.mikrotik.facade.files.internal;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFileException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session-owned bounded executor for blocking low-level binary file transfers.
 */
public final class FileDownloadExecutor {
    public static final int DEFAULT_WORKERS = 2;
    public static final int DEFAULT_QUEUE_CAPACITY = 16;

    private final Executor callbackExecutor;
    private final ThreadPoolExecutor executor;
    private final Set<DownloadTask<?>> tracked = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /** Creates the default bounded binary-transfer executor. */
    public FileDownloadExecutor(Executor callbackExecutor, ThreadFactory threadFactory) {
        this(callbackExecutor, threadFactory, DEFAULT_WORKERS, DEFAULT_QUEUE_CAPACITY);
    }

    FileDownloadExecutor(
            Executor callbackExecutor,
            ThreadFactory threadFactory,
            int workers,
            int queueCapacity) {
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        Objects.requireNonNull(threadFactory, "threadFactory");
        if (workers <= 0) throw new IllegalArgumentException("workers must be positive");
        if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be positive");
        this.executor = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Submits one blocking transfer without occupying the callback executor. */
    public <T> CompletableFuture<T> submit(Callable<T> callable) {
        Objects.requireNonNull(callable, "callable");
        DownloadTask<T> task = new DownloadTask<>(callable);
        if (!accepting.get()) {
            task.failClosing();
            return task.future;
        }

        tracked.add(task);
        if (!accepting.get()) {
            tracked.remove(task);
            task.failClosing();
            return task.future;
        }

        try {
            executor.execute(task);
        } catch (RejectedExecutionException rejected) {
            tracked.remove(task);
            task.failQueueFull(rejected);
        }
        return task.future;
    }

    /**
     * Stops accepting new work and fails transfers that have not started yet.
     * Running transfers are left for connection close to terminate.
     */
    public void beginClose() {
        if (!accepting.compareAndSet(true, false)) {
            return;
        }
        for (DownloadTask<?> task : List.copyOf(tracked)) {
            if (executor.remove(task)) {
                tracked.remove(task);
                task.failClosing();
            }
        }
    }

    /**
     * Shuts down worker threads after the RouterOS connection has been closed.
     * Any still-running public futures are completed with session-close failure.
     */
    public void finishClose() {
        beginClose();
        for (Runnable queued : executor.shutdownNow()) {
            if (queued instanceof DownloadTask<?> task) {
                tracked.remove(task);
                task.failClosing();
            }
        }
        for (DownloadTask<?> task : List.copyOf(tracked)) {
            task.failClosed();
        }
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    int trackedCount() {
        return tracked.size();
    }

    private final class DownloadTask<T> implements Runnable {
        private final Callable<T> callable;
        private final TaskFuture<T> future;
        private volatile Thread runner;

        private DownloadTask(Callable<T> callable) {
            this.callable = callable;
            this.future = new TaskFuture<>(this);
        }

        @Override
        public void run() {
            if (future.isDone()) {
                tracked.remove(this);
                return;
            }
            runner = Thread.currentThread();
            try {
                if (future.isDone()) return;
                T result = callable.call();
                complete(() -> future.complete(result));
            } catch (Throwable failure) {
                complete(() -> future.completeExceptionally(failure));
            } finally {
                runner = null;
                tracked.remove(this);
            }
        }

        private boolean cancelFromUser(boolean mayInterruptIfRunning) {
            if (!future.cancelDirect()) {
                return false;
            }
            executor.remove(this);
            tracked.remove(this);
            Thread active = runner;
            if (mayInterruptIfRunning && active != null) {
                active.interrupt();
            }
            return true;
        }

        private void failClosing() {
            complete(() -> future.completeExceptionally(
                    new MikrotikConnectionException(
                            "RouterOS session is closing before binary file download could run")));
        }

        private void failClosed() {
            complete(() -> future.completeExceptionally(
                    new MikrotikConnectionException(
                            "RouterOS session closed during binary file download")));
        }

        private void failQueueFull(RejectedExecutionException cause) {
            complete(() -> future.completeExceptionally(
                    new MikrotikFileException(
                            "Binary file download queue is full",
                            cause)));
        }

        private void complete(Runnable completion) {
            if (future.isDone()) return;
            callbackExecutor.execute(() -> {
                if (!future.isDone()) completion.run();
            });
        }
    }

    private static final class TaskFuture<T> extends CompletableFuture<T> {
        private final FileDownloadExecutor.DownloadTask<T> task;

        private TaskFuture(FileDownloadExecutor.DownloadTask<T> task) {
            this.task = task;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return task.cancelFromUser(mayInterruptIfRunning);
        }

        private boolean cancelDirect() {
            return super.cancel(false);
        }
    }
}
