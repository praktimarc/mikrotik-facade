package io.github.praktimarc.mikrotik.facade.internal.stream;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikBackpressureException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.OperationContext;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.error.ExceptionMapper;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * One independent bounded Flow subscription backed by one RouterOS operation.
 *
 * @param <T> delivered item type
 */
final class RouterOsSubscription<T> implements Flow.Subscription, ResultListener {

    private final ApiConnection connection;
    private final String operation;
    private final RouterOsCommand command;
    private final int queueCapacity;
    private final Executor dispatchExecutor;
    private final RouterOsPublisher.RecordMapper<T> mapper;
    private final SerialDelivery delivery;
    private final Consumer<RouterOsSubscription<T>> terminalCallback;
    private final OperationContext operationContext;
    private final Object lock = new Object();
    private final Queue<RouterOsRecord> queue = new ArrayDeque<>();

    private Flow.Subscriber<? super T> subscriber;
    private long demand;
    private boolean cancelled;
    private boolean upstreamComplete;
    private Throwable terminalError;
    private boolean terminalDelivered;

    RouterOsSubscription(
            ApiConnection connection,
            String operation,
            RouterOsCommand command,
            int queueCapacity,
            Executor dispatchExecutor,
            Executor callbackExecutor,
            RouterOsPublisher.RecordMapper<T> mapper,
            Consumer<RouterOsSubscription<T>> terminalCallback) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.command = Objects.requireNonNull(command, "command");
        this.queueCapacity = queueCapacity;
        this.dispatchExecutor = Objects.requireNonNull(dispatchExecutor, "dispatchExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.delivery = new SerialDelivery(Objects.requireNonNull(callbackExecutor, "callbackExecutor"));
        this.terminalCallback = Objects.requireNonNull(terminalCallback, "terminalCallback");
        this.operationContext = new OperationContext(connection, operation, command);
    }

    void signalOnSubscribe(Flow.Subscriber<? super T> target) {
        Objects.requireNonNull(target, "target");
        synchronized (lock) {
            if (subscriber != null) {
                throw new IllegalStateException("subscriber already assigned");
            }
            subscriber = target;
        }
        delivery.execute(() -> {
            if (isCancelled()) {
                return;
            }
            try {
                target.onSubscribe(this);
            } catch (RuntimeException | Error callbackFailure) {
                cancel();
                return;
            }
            dispatchExecutor.execute(this::start);
        });
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            failFlowRule(new IllegalArgumentException("Flow request must be greater than zero"));
            return;
        }
        synchronized (lock) {
            if (cancelled || terminalDelivered) {
                return;
            }
            demand = addCap(demand, n);
        }
        scheduleDrain();
    }

    @Override
    public void cancel() {
        boolean changed;
        synchronized (lock) {
            changed = !cancelled && !terminalDelivered;
            if (changed) {
                cancelled = true;
                queue.clear();
            }
        }
        if (!changed) {
            return;
        }
        operationContext.cancel();
        terminalCallback.accept(this);
    }

    @Override
    public void receive(Map<String, String> result) {
        Objects.requireNonNull(result, "result");
        MikrotikBackpressureException overflow = null;
        try {
            RouterOsRecord record = RouterOsRecord.of(result);
            synchronized (lock) {
                if (cancelled || terminalDelivered || terminalError != null || upstreamComplete) {
                    return;
                }
                if (queue.size() >= queueCapacity) {
                    overflow = new MikrotikBackpressureException(
                            "RouterOS stream queue capacity " + queueCapacity + " exceeded",
                            operation,
                            command.path(),
                            null);
                    terminalError = overflow;
                    queue.clear();
                } else {
                    queue.add(record);
                }
            }
        } catch (RuntimeException invalidRecord) {
            failLocal(
                    new MikrotikDataException("RouterOS stream returned invalid record data", invalidRecord),
                    true);
            return;
        }
        if (overflow != null) {
            scheduleRemoteCancel();
        }
        scheduleDrain();
    }

    @Override
    public void error(MikrotikApiException failure) {
        Objects.requireNonNull(failure, "failure");
        dispatchExecutor.execute(() -> failFromUpstream(ExceptionMapper.map(
                failure,
                operation,
                command.path(),
                command.arguments(),
                command.queries())));
    }

    @Override
    public void completed() {
        completed(Map.of());
    }

    @Override
    public void completed(Map<String, String> completion) {
        Objects.requireNonNull(completion, "completion");
        synchronized (lock) {
            if (cancelled || terminalDelivered || terminalError != null || upstreamComplete) {
                return;
            }
            upstreamComplete = true;
        }
        operationContext.complete(completion);
        scheduleDrain();
    }

    boolean isTerminal() {
        synchronized (lock) {
            return cancelled || terminalDelivered;
        }
    }

    long demand() {
        synchronized (lock) {
            return demand;
        }
    }

    int buffered() {
        synchronized (lock) {
            return queue.size();
        }
    }

    private void start() {
        if (!operationContext.beginDispatch()) {
            return;
        }
        try {
            String tag = connection.execute(command.serialize(), this);
            operationContext.tagAssigned(tag);
        } catch (MikrotikApiException failure) {
            operationContext.dispatchFinishedWithoutTag();
            failLocal(ExceptionMapper.map(
                    failure,
                    operation,
                    command.path(),
                    command.arguments(),
                    command.queries()), false);
        } catch (RuntimeException failure) {
            operationContext.dispatchFinishedWithoutTag();
            failLocal(new MikrotikFacadeException(
                    "RouterOS stream dispatch failed unexpectedly",
                    failure), false);
        }
    }

    private void failFlowRule(IllegalArgumentException failure) {
        synchronized (lock) {
            if (cancelled || terminalDelivered || terminalError != null) {
                return;
            }
            terminalError = failure;
            queue.clear();
        }
        operationContext.cancel();
        scheduleDrain();
    }

    private void failFromUpstream(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        synchronized (lock) {
            if (cancelled || terminalDelivered || terminalError != null || upstreamComplete) {
                return;
            }
        }
        failLocal(failure, false);
    }

    private void failLocal(Throwable failure, boolean cancelRemote) {
        Objects.requireNonNull(failure, "failure");
        synchronized (lock) {
            if (cancelled || terminalDelivered || terminalError != null) {
                return;
            }
            terminalError = failure;
            queue.clear();
        }
        if (cancelRemote) {
            scheduleRemoteCancel();
        } else {
            operationContext.fail(
                    failure instanceof MikrotikFacadeException facadeFailure
                            ? facadeFailure
                            : new MikrotikFacadeException("RouterOS stream failed", failure));
        }
        scheduleDrain();
    }

    private void scheduleDrain() {
        dispatchExecutor.execute(() -> delivery.execute(this::drain));
    }

    private void scheduleRemoteCancel() {
        try {
            dispatchExecutor.execute(operationContext::cancel);
        } catch (RuntimeException ignored) {
            // Remote cancellation is best effort and must not block the RouterOS processor thread.
        }
    }

    private void drain() {
        while (true) {
            Flow.Subscriber<? super T> target;
            RouterOsRecord record = null;
            Throwable failure = null;
            boolean complete = false;

            synchronized (lock) {
                if (cancelled || terminalDelivered) {
                    return;
                }
                target = subscriber;
                if (target == null) {
                    return;
                }
                if (terminalError != null) {
                    failure = terminalError;
                    terminalDelivered = true;
                } else if (demand > 0 && !queue.isEmpty()) {
                    record = queue.remove();
                    if (demand != Long.MAX_VALUE) {
                        demand--;
                    }
                } else if (upstreamComplete && queue.isEmpty()) {
                    complete = true;
                    terminalDelivered = true;
                } else {
                    return;
                }
            }

            if (failure != null) {
                terminalCallback.accept(this);
                safeOnError(target, failure);
                return;
            }
            if (complete) {
                terminalCallback.accept(this);
                safeOnComplete(target);
                return;
            }

            final T mapped;
            try {
                mapped = Objects.requireNonNull(mapper.map(record), "stream mapper result");
            } catch (MikrotikFacadeException mappingFailure) {
                failLocal(mappingFailure, true);
                continue;
            } catch (RuntimeException mappingFailure) {
                failLocal(
                        new MikrotikDataException("RouterOS stream record mapping failed", mappingFailure),
                        true);
                continue;
            }

            try {
                target.onNext(mapped);
            } catch (RuntimeException | Error callbackFailure) {
                cancel();
                return;
            }
        }
    }

    private static void safeOnError(Flow.Subscriber<?> subscriber, Throwable failure) {
        try {
            subscriber.onError(failure);
        } catch (RuntimeException | Error ignored) {
            // Subscriber callbacks must not escape the serialized delivery lane.
        }
    }

    private static void safeOnComplete(Flow.Subscriber<?> subscriber) {
        try {
            subscriber.onComplete();
        } catch (RuntimeException | Error ignored) {
            // Subscriber callbacks must not escape the serialized delivery lane.
        }
    }

    private boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    static long addCap(long current, long increment) {
        long updated = current + increment;
        return updated < 0 ? Long.MAX_VALUE : updated;
    }
}
