package io.github.praktimarc.mikrotik.facade.internal.command;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikTimeoutException;
import io.github.praktimarc.mikrotik.facade.internal.error.ExceptionMapper;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shared listener-based execution engine for finite synchronous and asynchronous facade commands.
 */
public final class CommandEngine {

    private final ApiConnection connection;
    private final Duration commandTimeout;
    private final Executor dispatchExecutor;
    private final Executor callbackExecutor;
    private final TimeoutScheduler timeoutScheduler;

    /** Creates an engine backed by a scheduled executor for timeout tasks. */
    public CommandEngine(
            ApiConnection connection,
            Duration commandTimeout,
            Executor dispatchExecutor,
            Executor callbackExecutor,
            ScheduledExecutorService timeoutScheduler) {
        this(
                connection,
                commandTimeout,
                dispatchExecutor,
                callbackExecutor,
                (task, delay) -> {
                    var scheduled = timeoutScheduler.schedule(
                            task,
                            delay.toMillis(),
                            TimeUnit.MILLISECONDS);
                    return () -> scheduled.cancel(false);
                });
    }

    CommandEngine(
            ApiConnection connection,
            Duration commandTimeout,
            Executor dispatchExecutor,
            Executor callbackExecutor,
            TimeoutScheduler timeoutScheduler) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.commandTimeout = requirePositiveMillis(commandTimeout);
        this.dispatchExecutor = Objects.requireNonNull(dispatchExecutor, "dispatchExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
    }

    /**
     * Executes an internal operation synchronously through the common listener pipeline.
     *
     * @throws MikrotikFacadeException if command execution or typed mapping fails
     */
    public <T> T executeSync(RouterOsOperation<T> operation) throws MikrotikFacadeException {
        Execution execution = start(operation);
        try {
            CommandResult result = execution.context.resultFuture().get();
            return operation.map(result);
        } catch (InterruptedException interrupted) {
            execution.context.cancel();
            Thread.currentThread().interrupt();
            throw new MikrotikCommandException(
                    "Interrupted while waiting for RouterOS command",
                    operation.name(),
                    operation.command().path(),
                    null,
                    null,
                    interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof MikrotikFacadeException facadeFailure) {
                throw facadeFailure;
            }
            if (cause instanceof CancellationException cancellation) {
                throw cancelledSync(operation, cancellation);
            }
            throw new MikrotikFacadeException(
                    "Unexpected RouterOS command completion failure",
                    cause);
        } catch (CancellationException cancellation) {
            throw cancelledSync(operation, cancellation);
        }
    }

    /**
     * Executes an internal operation asynchronously through the same listener pipeline.
     */
    public <T> CompletableFuture<T> executeAsync(RouterOsOperation<T> operation) {
        Execution execution = start(operation);
        CancellableFuture<T> publicFuture = new CancellableFuture<>(execution.context);

        execution.context.resultFuture().whenCompleteAsync((result, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                callbackExecutor.execute(() -> publicFuture.completeExceptionally(cause));
                return;
            }

            final T mapped;
            try {
                mapped = operation.map(result);
            } catch (MikrotikFacadeException mappingFailure) {
                callbackExecutor.execute(
                        () -> publicFuture.completeExceptionally(mappingFailure));
                return;
            } catch (RuntimeException mappingFailure) {
                MikrotikFacadeException wrapped = new MikrotikFacadeException(
                        "Facade operation result mapping failed",
                        mappingFailure);
                callbackExecutor.execute(
                        () -> publicFuture.completeExceptionally(wrapped));
                return;
            }

            callbackExecutor.execute(() -> publicFuture.complete(mapped));
        }, dispatchExecutor);

        return publicFuture;
    }

    private Execution start(RouterOsOperation<?> operation) {
        Objects.requireNonNull(operation, "operation");
        RouterOsCommand command = Objects.requireNonNull(
                operation.command(),
                "operation.command()");
        String operationName = Objects.requireNonNull(
                operation.name(),
                "operation.name()");

        OperationContext context = new OperationContext(
                connection,
                operationName,
                command);

        TimeoutTask timeoutTask = timeoutScheduler.schedule(
                () -> context.timeout(new MikrotikTimeoutException(
                        "RouterOS command timed out after "
                                + commandTimeout.toMillis()
                                + " ms",
                        operationName,
                        command.path(),
                        null)),
                commandTimeout);

        context.resultFuture().whenComplete(
                (ignored, failure) -> timeoutTask.cancel());

        dispatchExecutor.execute(() -> dispatch(context));
        return new Execution(context);
    }

    private void dispatch(OperationContext context) {
        if (!context.beginDispatch()) {
            return;
        }

        RouterOsCommand command = context.command();
        ResultListener listener = new ResultListener() {
            @Override
            public void receive(Map<String, String> result) {
                try {
                    context.receive(result);
                } catch (RuntimeException invalidRecord) {
                    context.fail(new MikrotikDataException(
                            "RouterOS command returned invalid record data",
                            invalidRecord));
                }
            }

            @Override
            public void error(MikrotikApiException failure) {
                try {
                    context.fail(ExceptionMapper.map(
                            failure,
                            context.operation(),
                            command.path(),
                            command.arguments(),
                            command.queries()));
                } catch (RuntimeException mappingFailure) {
                    context.fail(new MikrotikFacadeException(
                            "RouterOS command failure could not be mapped safely",
                            mappingFailure));
                }
            }

            @Override
            public void completed() {
                completed(Map.of());
            }

            @Override
            public void completed(Map<String, String> completion) {
                try {
                    context.complete(completion);
                } catch (RuntimeException invalidCompletion) {
                    context.fail(new MikrotikDataException(
                            "RouterOS command returned invalid completion metadata",
                            invalidCompletion));
                }
            }
        };

        try {
            String tag = connection.execute(command.serialize(), listener);
            context.tagAssigned(tag);
        } catch (MikrotikApiException failure) {
            context.fail(ExceptionMapper.map(
                    failure,
                    context.operation(),
                    command.path(),
                    command.arguments(),
                    command.queries()));
            context.dispatchFinishedWithoutTag();
        } catch (RuntimeException unexpectedFailure) {
            context.fail(new MikrotikFacadeException(
                    "RouterOS command dispatch failed unexpectedly",
                    unexpectedFailure));
            context.dispatchFinishedWithoutTag();
        }
    }

    private static MikrotikCommandException cancelledSync(
            RouterOsOperation<?> operation,
            CancellationException cancellation) {
        return new MikrotikCommandException(
                "RouterOS command was cancelled while waiting synchronously",
                operation.name(),
                operation.command().path(),
                null,
                null,
                cancellation);
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof java.util.concurrent.CompletionException completion
                && completion.getCause() != null) {
            return completion.getCause();
        }
        return failure;
    }

    private static Duration requirePositiveMillis(Duration duration) {
        Objects.requireNonNull(duration, "commandTimeout");
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "commandTimeout is too large",
                    overflow);
        }
        if (duration.isZero()
                || duration.isNegative()
                || millis < 1
                || !duration.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException(
                    "commandTimeout must be positive whole milliseconds");
        }
        return duration;
    }

    @FunctionalInterface
    interface TimeoutScheduler {
        TimeoutTask schedule(Runnable task, Duration delay);
    }

    @FunctionalInterface
    interface TimeoutTask {
        void cancel();
    }

    private static final class Execution {
        private final OperationContext context;

        private Execution(OperationContext context) {
            this.context = context;
        }
    }

    private static final class CancellableFuture<T>
            extends CompletableFuture<T> {
        private final OperationContext context;

        private CancellableFuture(OperationContext context) {
            this.context = context;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean won = context.cancel();
            if (won) {
                super.cancel(false);
            }
            return won;
        }
    }
}
