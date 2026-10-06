package io.github.praktimarc.mikrotik.facade.internal.session;

import javax.net.SocketFactory;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Internal validated bootstrap configuration. This type is not part of the supported public facade API.
 */
public final class BootstrapConfig {

    private final String host;
    private final String username;
    private final String password;
    private final SocketFactory socketFactory;
    private final int port;
    private final int connectTimeoutMillis;
    private final int commandTimeoutMillis;
    private final int bootstrapRetries;
    private final Executor callbackExecutor;

    /**
     * Creates a fully validated bootstrap configuration.
     *
     * @param host RouterOS host
     * @param username RouterOS user name
     * @param password RouterOS password
     * @param socketFactory configured socket factory
     * @param port resolved API port
     * @param connectTimeoutMillis socket connect timeout in milliseconds
     * @param commandTimeoutMillis finite command timeout in milliseconds
     * @param bootstrapRetries number of bootstrap retries
     * @param callbackExecutor caller-provided callback executor, or {@code null}
     */
    public BootstrapConfig(
            String host,
            String username,
            String password,
            SocketFactory socketFactory,
            int port,
            int connectTimeoutMillis,
            int commandTimeoutMillis,
            int bootstrapRetries,
            Executor callbackExecutor) {
        this.host = Objects.requireNonNull(host, "host");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.socketFactory = Objects.requireNonNull(socketFactory, "socketFactory");
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.commandTimeoutMillis = commandTimeoutMillis;
        this.bootstrapRetries = bootstrapRetries;
        this.callbackExecutor = callbackExecutor;
    }

    /**
     * Returns the RouterOS host.
     *
     * @return RouterOS host
     */
    public String host() {
        return host;
    }

    /**
     * Returns the RouterOS user name.
     *
     * @return RouterOS user name
     */
    public String username() {
        return username;
    }

    /**
     * Returns the RouterOS password for the immediate login operation.
     *
     * @return RouterOS password; internal use only
     */
    public String password() {
        return password;
    }

    /**
     * Returns the configured socket factory.
     *
     * @return configured socket factory
     */
    public SocketFactory socketFactory() {
        return socketFactory;
    }

    /**
     * Returns the resolved API port.
     *
     * @return resolved API port
     */
    public int port() {
        return port;
    }

    /**
     * Returns the socket connect timeout.
     *
     * @return connect timeout in milliseconds
     */
    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /**
     * Returns the finite command timeout.
     *
     * @return finite command timeout in milliseconds
     */
    public int commandTimeoutMillis() {
        return commandTimeoutMillis;
    }

    /**
     * Returns the configured number of bootstrap retries.
     *
     * @return number of bootstrap retries
     */
    public int bootstrapRetries() {
        return bootstrapRetries;
    }

    /**
     * Returns the caller-provided callback executor when one was configured.
     *
     * @return caller-provided callback executor, or {@code null}
     */
    public Executor callbackExecutor() {
        return callbackExecutor;
    }

    @Override
    public String toString() {
        return "BootstrapConfig{" +
                "host=" + host +
                ", credentials=<configured>" +
                ", port=" + port +
                ", connectTimeoutMillis=" + connectTimeoutMillis +
                ", commandTimeoutMillis=" + commandTimeoutMillis +
                ", bootstrapRetries=" + bootstrapRetries +
                ", callbackExecutor=" + (callbackExecutor == null ? "<default>" : "<configured>") +
                '}';
    }
}
