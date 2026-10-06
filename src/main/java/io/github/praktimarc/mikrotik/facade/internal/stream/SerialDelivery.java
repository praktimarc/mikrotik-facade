package io.github.praktimarc.mikrotik.facade.internal.stream;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Serializes delivery tasks for one stream subscription on top of an arbitrary executor.
 */
final class SerialDelivery {

    private final Executor executor;
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private boolean running;

    /**
     * Creates a serial delivery lane backed by the supplied executor.
     *
     * @param executor backing executor
     */
    SerialDelivery(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Enqueues one delivery task. Tasks execute in submission order and never concurrently.
     *
     * @param task delivery task
     */
    void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        boolean start;
        synchronized (tasks) {
            tasks.add(task);
            start = !running;
            if (start) {
                running = true;
            }
        }
        if (start) {
            scheduleDrain();
        }
    }

    private void scheduleDrain() {
        try {
            executor.execute(this::drain);
        } catch (RuntimeException failure) {
            synchronized (tasks) {
                running = false;
                tasks.clear();
            }
            throw failure;
        }
    }

    private void drain() {
        while (true) {
            Runnable task;
            synchronized (tasks) {
                task = tasks.poll();
                if (task == null) {
                    running = false;
                    return;
                }
            }
            try {
                task.run();
            } catch (RuntimeException | Error ignored) {
                // Subscription code contains user-callback failures; keep the serial lane usable.
            }
        }
    }
}
