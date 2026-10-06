package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.session.BootstrapConfig;
import io.github.praktimarc.mikrotik.facade.internal.session.Bootstrapper;
import io.github.praktimarc.mikrotik.facade.transport.ApiTransport;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Builder for a RouterOS facade session.
 */
public final class MikrotikRtrApiBuilder {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(60);

    private String host;
    private String username;
    private String password;
    private ApiTransport transport = ApiTransport.plain();
    private Integer portOverride;
    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private Duration commandTimeout = DEFAULT_COMMAND_TIMEOUT;
    private int bootstrapRetries;
    private Executor callbackExecutor;

    MikrotikRtrApiBuilder() {
    }

    /**
     * Sets the RouterOS host name or IP address.
     *
     * @param host host name or IP address
     * @return this builder
     */
    public MikrotikRtrApiBuilder host(String host) {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        this.host = host.trim();
        return this;
    }

    /**
     * Sets the credentials supplied by the consumer for bootstrap.
     *
     * <p>The facade does not persist credentials outside the in-memory builder
     * and resulting session configuration. Empty passwords remain valid because
     * password policy belongs to RouterOS, not the facade.</p>
     *
     * @param username RouterOS user name
     * @param password RouterOS password, which may be empty but not null
     * @return this builder
     */
    public MikrotikRtrApiBuilder credentials(String username, String password) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        this.username = username;
        this.password = password;
        return this;
    }

    /**
     * Selects the API transport.
     *
     * @param transport transport configuration
     * @return this builder
     */
    public MikrotikRtrApiBuilder transport(ApiTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        return this;
    }

    /**
     * Overrides the transport's default TCP port.
     *
     * @param port TCP port in the range 1..65535
     * @return this builder
     */
    public MikrotikRtrApiBuilder port(int port) {
        this.portOverride = validatePort(port);
        return this;
    }

    /**
     * Sets the socket connect timeout.
     *
     * @param timeout positive duration representable in whole milliseconds and a signed 32-bit integer
     * @return this builder
     */
    public MikrotikRtrApiBuilder connectTimeout(Duration timeout) {
        validateTimeout(timeout, "connectTimeout");
        this.connectTimeout = timeout;
        return this;
    }

    /**
     * Sets the facade command timeout for finite commands.
     *
     * @param timeout positive duration representable in whole milliseconds and a signed 32-bit integer
     * @return this builder
     */
    public MikrotikRtrApiBuilder commandTimeout(Duration timeout) {
        validateTimeout(timeout, "commandTimeout");
        this.commandTimeout = timeout;
        return this;
    }

    /**
     * Sets the number of retries permitted only during bootstrap.
     *
     * @param retries number of retries, zero or greater
     * @return this builder
     */
    public MikrotikRtrApiBuilder bootstrapRetries(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException("bootstrapRetries must not be negative");
        }
        this.bootstrapRetries = retries;
        return this;
    }

    /**
     * Supplies the executor used later for public callbacks.
     *
     * <p>Caller-provided executors remain caller-owned and are never shut down by the facade.</p>
     *
     * @param executor callback executor
     * @return this builder
     */
    public MikrotikRtrApiBuilder callbackExecutor(Executor executor) {
        this.callbackExecutor = Objects.requireNonNull(executor, "executor");
        return this;
    }

    /**
     * Connects, authenticates and discovers the RouterOS environment before returning a usable facade session.
     *
     * @return fully bootstrapped authenticated facade session
     * @throws MikrotikFacadeException if bootstrap cannot complete successfully
     */
    public MikrotikRtrApi connect() throws MikrotikFacadeException {
        ValidatedConfig validated = validatedConfig();
        BootstrapConfig config = new BootstrapConfig(
                validated.host(),
                validated.username(),
                validated.password(),
                validated.transport().socketFactory(),
                validated.port(),
                validated.connectTimeoutMillis(),
                validated.commandTimeoutMillis(),
                validated.bootstrapRetries(),
                validated.callbackExecutor());

        Bootstrapper.BootstrapResult result = new Bootstrapper().bootstrap(config);
        return new MikrotikRtrApi(
                result.connection(),
                result.lifecycle(),
                result.environment(),
                result.callbackExecutor());
    }

    @Override
    public String toString() {
        return "MikrotikRtrApiBuilder{" +
                "host=" + (host == null ? "<unset>" : host) +
                ", credentials=" + (username == null ? "<unset>" : "<configured>") +
                ", transport=" + transport +
                ", port=" + (portOverride == null ? "<default>" : portOverride) +
                ", connectTimeout=" + connectTimeout +
                ", commandTimeout=" + commandTimeout +
                ", bootstrapRetries=" + bootstrapRetries +
                ", callbackExecutor=" + (callbackExecutor == null ? "<default>" : "<configured>") +
                '}';
    }

    ValidatedConfig validatedConfig() {
        if (host == null) {
            throw new IllegalStateException("host must be configured before connect");
        }
        if (username == null || password == null) {
            throw new IllegalStateException("credentials must be configured before connect");
        }

        int resolvedPort = portOverride == null ? transport.defaultPort() : portOverride;
        return new ValidatedConfig(
                host,
                username,
                password,
                transport,
                resolvedPort,
                connectTimeout,
                validateTimeout(connectTimeout, "connectTimeout"),
                commandTimeout,
                validateTimeout(commandTimeout, "commandTimeout"),
                bootstrapRetries,
                callbackExecutor);
    }

    private static int validatePort(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return port;
    }

    private static int validateTimeout(Duration timeout, String name) {
        Objects.requireNonNull(timeout, name);
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }

        final long millis;
        try {
            millis = timeout.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " is too large", exception);
        }
        if (millis < 1) {
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        }
        if (!timeout.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException(name + " must use whole milliseconds");
        }
        if (millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must fit in a signed 32-bit millisecond value");
        }
        return (int) millis;
    }

    static final class ValidatedConfig {

        private final String host;
        private final String username;
        private final String password;
        private final ApiTransport transport;
        private final int port;
        private final Duration connectTimeout;
        private final int connectTimeoutMillis;
        private final Duration commandTimeout;
        private final int commandTimeoutMillis;
        private final int bootstrapRetries;
        private final Executor callbackExecutor;

        private ValidatedConfig(
                String host,
                String username,
                String password,
                ApiTransport transport,
                int port,
                Duration connectTimeout,
                int connectTimeoutMillis,
                Duration commandTimeout,
                int commandTimeoutMillis,
                int bootstrapRetries,
                Executor callbackExecutor) {
            this.host = host;
            this.username = username;
            this.password = password;
            this.transport = transport;
            this.port = port;
            this.connectTimeout = connectTimeout;
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.commandTimeout = commandTimeout;
            this.commandTimeoutMillis = commandTimeoutMillis;
            this.bootstrapRetries = bootstrapRetries;
            this.callbackExecutor = callbackExecutor;
        }

        String host() {
            return host;
        }

        String username() {
            return username;
        }

        String password() {
            return password;
        }

        ApiTransport transport() {
            return transport;
        }

        int port() {
            return port;
        }

        Duration connectTimeout() {
            return connectTimeout;
        }

        int connectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        Duration commandTimeout() {
            return commandTimeout;
        }

        int commandTimeoutMillis() {
            return commandTimeoutMillis;
        }

        int bootstrapRetries() {
            return bootstrapRetries;
        }

        Executor callbackExecutor() {
            return callbackExecutor;
        }

        @Override
        public String toString() {
            return "ValidatedConfig{" +
                    "host=" + host +
                    ", credentials=<configured>" +
                    ", transport=" + transport +
                    ", port=" + port +
                    ", connectTimeout=" + connectTimeout +
                    ", commandTimeout=" + commandTimeout +
                    ", bootstrapRetries=" + bootstrapRetries +
                    ", callbackExecutor=" + (callbackExecutor == null ? "<default>" : "<configured>") +
                    '}';
        }
    }
}
