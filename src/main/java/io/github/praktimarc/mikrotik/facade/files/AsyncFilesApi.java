package io.github.praktimarc.mikrotik.facade.files;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFileException;
import io.github.praktimarc.mikrotik.facade.files.internal.FileDownloadExecutor;
import io.github.praktimarc.mikrotik.facade.files.internal.FileDownloadSupport;
import io.github.praktimarc.mikrotik.facade.files.internal.FileOperations;
import io.github.praktimarc.mikrotik.facade.files.internal.RouterFileMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/** Asynchronous RouterOS file metadata and binary-download facade. */
public final class AsyncFilesApi {
    private final ApiConnection connection;
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final FileDownloadExecutor downloadExecutor;
    private final RouterFileMapper mapper;

    /** Creates the asynchronous files facade for session wiring. */
    public AsyncFilesApi(
            ApiConnection connection,
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            FileDownloadExecutor downloadExecutor) {
        this(connection, engine, lifecycle, callbackExecutor, downloadExecutor, new RouterFileMapper());
    }

    AsyncFilesApi(
            ApiConnection connection,
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            FileDownloadExecutor downloadExecutor,
            RouterFileMapper mapper) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.downloadExecutor = Objects.requireNonNull(downloadExecutor, "downloadExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public CompletableFuture<List<RouterFile>> list() {
        return find(RouterOsProperties.builder().build());
    }

    public CompletableFuture<List<RouterFile>> find(RouterOsProperties queries) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(FileOperations.find(queries, mapper));
    }

    public CompletableFuture<Optional<RouterFile>> findByName(String name) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }
        return engine.executeAsync(FileOperations.findByName(name, mapper));
    }

    /**
     * Runs the blocking low-level binary transfer on the dedicated bounded executor.
     * Cancellation is local best-effort because low-level chunk tags are not public.
     */
    public CompletableFuture<FileDownloadResult> download(String remoteFile, Path localFile) {
        final String remote = requireNonBlank(remoteFile, "remoteFile");
        final Path local = Objects.requireNonNull(localFile, "localFile");
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }

        CompletableFuture<Optional<RouterFile>> lookup =
                engine.executeAsync(FileOperations.findByName(remote, mapper));
        DownloadChainFuture result = new DownloadChainFuture(lookup);

        lookup.whenComplete((file, failure) -> {
            if (result.isDone()) return;
            if (failure != null) {
                result.completeExceptionally(unwrap(failure));
                return;
            }
            if (file.isEmpty()) {
                result.completeExceptionally(
                        new MikrotikFileException("Requested RouterOS file does not exist"));
                return;
            }

            CompletableFuture<FileDownloadResult> transfer = downloadExecutor.submit(
                    () -> FileDownloadSupport.download(connection, remote, local));
            result.attachTransfer(transfer);
            transfer.whenComplete((downloaded, transferFailure) -> {
                if (transferFailure != null) {
                    result.completeExceptionally(unwrap(transferFailure));
                } else {
                    result.complete(downloaded);
                }
            });
        });

        return result;
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return failure;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static final class DownloadChainFuture extends CompletableFuture<FileDownloadResult> {
        private final CompletableFuture<?> lookup;
        private final AtomicReference<CompletableFuture<?>> transfer = new AtomicReference<>();

        private DownloadChainFuture(CompletableFuture<?> lookup) {
            this.lookup = lookup;
        }

        private void attachTransfer(CompletableFuture<?> transferFuture) {
            transfer.set(transferFuture);
            if (isCancelled()) {
                transferFuture.cancel(true);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean won = super.cancel(false);
            if (!won) return false;
            lookup.cancel(mayInterruptIfRunning);
            CompletableFuture<?> current = transfer.get();
            if (current != null) current.cancel(mayInterruptIfRunning);
            return true;
        }
    }
}
