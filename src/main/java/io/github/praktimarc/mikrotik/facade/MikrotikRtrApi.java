package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Entry point for one authenticated RouterOS facade session.
 */
public final class MikrotikRtrApi implements AutoCloseable {

    private final ApiConnection connection;
    private final SessionLifecycle lifecycle;
    private final RouterOsEnvironment environment;
    private final Executor configuredCallbackExecutor;

    MikrotikRtrApi(
            ApiConnection connection,
            SessionLifecycle lifecycle,
            RouterOsEnvironment environment,
            Executor configuredCallbackExecutor) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.configuredCallbackExecutor = configuredCallbackExecutor;
    }

    /**
     * Creates a new facade builder.
     *
     * @return new builder with safe defaults
     */
    public static MikrotikRtrApiBuilder builder() {
        return new MikrotikRtrApiBuilder();
    }

    /**
     * Returns the immutable RouterOS environment captured during bootstrap.
     *
     * @return session environment snapshot
     */
    public RouterOsEnvironment environment() {
        return environment;
    }

    /**
     * Closes this facade session. Repeated calls are idempotent.
     *
     * @throws MikrotikConnectionException if the underlying low-level connection reports an error while closing
     */
    @Override
    public void close() throws MikrotikConnectionException {
        if (!lifecycle.beginClose()) {
            return;
        }

        connection.removeConnectionListener(lifecycle);
        try {
            connection.close();
        } catch (ApiConnectionException exception) {
            throw new MikrotikConnectionException("Unable to close RouterOS connection cleanly", exception);
        } finally {
            lifecycle.finishClose();
        }
    }

    SessionLifecycle lifecycle() {
        return lifecycle;
    }

    Executor configuredCallbackExecutor() {
        return configuredCallbackExecutor;
    }
}
