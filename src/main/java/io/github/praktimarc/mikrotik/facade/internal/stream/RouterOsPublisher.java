package io.github.praktimarc.mikrotik.facade.internal.stream;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import me.legrange.mikrotik.ApiConnection;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

/**
 * Cold bounded Flow publisher for one RouterOS streaming command definition.
 *
 * @param <T> public stream item type
 */
public final class RouterOsPublisher<T> implements Flow.Publisher<T> {

    private final ApiConnection connection;
    private final String operation;
    private final RouterOsCommand command;
    private final int queueCapacity;
    private final Executor dispatchExecutor;
    private final Executor callbackExecutor;
    private final RecordMapper<T> mapper;
    private final Set<RouterOsSubscription<T>> subscriptions = ConcurrentHashMap.newKeySet();

    /**
     * Creates a cold publisher. Construction performs no RouterOS I/O.
     *
     * @param connection authenticated low-level connection
     * @param operation safe facade operation name
     * @param command immutable RouterOS streaming command
     * @param queueCapacity per-subscription buffer capacity, greater than zero
     * @param dispatchExecutor executor used to start the low-level operation
     * @param callbackExecutor executor used for Flow callbacks and record mapping
     * @param mapper internal record mapper
     */
    public RouterOsPublisher(
            ApiConnection connection,
            String operation,
            RouterOsCommand command,
            int queueCapacity,
            Executor dispatchExecutor,
            Executor callbackExecutor,
            RecordMapper<T> mapper) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.operation = requireNonBlank(operation, "operation");
        this.command = Objects.requireNonNull(command, "command");
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be greater than zero");
        }
        this.queueCapacity = queueCapacity;
        this.dispatchExecutor = Objects.requireNonNull(dispatchExecutor, "dispatchExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        RouterOsSubscription<T> subscription = new RouterOsSubscription<>(
                connection,
                operation,
                command,
                queueCapacity,
                dispatchExecutor,
                callbackExecutor,
                mapper,
                subscriptions::remove);
        subscriptions.add(subscription);
        subscription.signalOnSubscribe(subscriber);
    }

    /** Requests cancellation of subscriptions created by this publisher. */
    public void cancelActive() {
        subscriptions.forEach(Flow.Subscription::cancel);
    }

    /**
     * Internal record mapper executed away from RouterOS low-level threads.
     *
     * @param <T> stream item type
     */
    @FunctionalInterface
    public interface RecordMapper<T> {
        /**
         * Maps one immutable RouterOS record.
         *
         * @param record immutable RouterOS record
         * @return mapped stream item
         * @throws MikrotikFacadeException if typed interpretation fails
         */
        T map(RouterOsRecord record) throws MikrotikFacadeException;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
