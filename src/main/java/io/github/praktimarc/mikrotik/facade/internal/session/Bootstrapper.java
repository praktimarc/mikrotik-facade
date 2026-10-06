package io.github.praktimarc.mikrotik.facade.internal.session;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsPackage;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikAuthenticationException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.ApiDataException;
import me.legrange.mikrotik.MikrotikApiException;

import javax.net.SocketFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a complete authenticated session and environment snapshot before a facade instance can escape.
 */
public final class Bootstrapper {

    private static final String SYSTEM_RESOURCE_PATH = "/system/resource/print";
    private static final String SYSTEM_PACKAGE_PATH = "/system/package/print";

    private final ConnectionFactory connectionFactory;

    /**
     * Creates a bootstrapper backed by the public low-level {@link ApiConnection} factory.
     */
    public Bootstrapper() {
        this(ApiConnection::connect);
    }

    Bootstrapper(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    /**
     * Performs connect, listener registration, login and environment discovery.
     *
     * @param config validated bootstrap configuration
     * @return successful bootstrap result
     * @throws MikrotikFacadeException if bootstrap cannot produce a complete usable session
     */
    public BootstrapResult bootstrap(BootstrapConfig config) throws MikrotikFacadeException {
        Objects.requireNonNull(config, "config");

        int attempts = config.bootstrapRetries() + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            ApiConnection connection;
            try {
                connection = connectionFactory.connect(
                        config.socketFactory(),
                        config.host(),
                        config.port(),
                        config.connectTimeoutMillis());
            } catch (MikrotikApiException exception) {
                if (attempt + 1 < attempts) {
                    continue;
                }
                throw mapConnectFailure(exception);
            }

            SessionLifecycle lifecycle = new SessionLifecycle();
            connection.addConnectionListener(lifecycle);

            try {
                connection.setTimeout(config.commandTimeoutMillis());
            } catch (MikrotikApiException exception) {
                closeAfterFailure(connection, exception);
                throw new MikrotikFacadeException("Unable to configure RouterOS command timeout", exception);
            }

            try {
                connection.login(config.username(), config.password());
                ensureConnected(lifecycle, "RouterOS connection was lost during authentication");
            } catch (ApiCommandException exception) {
                closeAfterFailure(connection, exception);
                throw new MikrotikAuthenticationException("RouterOS authentication was rejected", exception);
            } catch (MikrotikApiException exception) {
                closeAfterFailure(connection, exception);
                if (attempt + 1 < attempts) {
                    continue;
                }
                throw mapTechnicalLoginFailure(exception);
            }

            try {
                RouterOsEnvironment environment = readEnvironment(connection, lifecycle);
                ensureConnected(lifecycle, "RouterOS connection was lost during bootstrap");
                return new BootstrapResult(connection, lifecycle, environment, config.callbackExecutor());
            } catch (MikrotikFacadeException exception) {
                closeAfterFailure(connection, exception);
                throw exception;
            }
        }

        throw new IllegalStateException("Bootstrap attempt loop terminated unexpectedly");
    }

    private static RouterOsEnvironment readEnvironment(
            ApiConnection connection,
            SessionLifecycle lifecycle) throws MikrotikFacadeException {
        List<Map<String, String>> resourceRows = executeBootstrapCommand(connection, SYSTEM_RESOURCE_PATH);
        ensureConnected(lifecycle, "RouterOS connection was lost while reading system resources");
        RouterOsSystemInfo systemInfo = parseSystemInfo(resourceRows);

        try {
            List<Map<String, String>> packageRows = connection.execute(SYSTEM_PACKAGE_PATH);
            ensureConnected(lifecycle, "RouterOS connection was lost while reading package information");
            return RouterOsEnvironment.withPackages(systemInfo, parsePackages(packageRows));
        } catch (ApiCommandException exception) {
            if (isExplicitPackageCommandUnavailable(exception)) {
                return RouterOsEnvironment.withoutPackageInformation(systemInfo);
            }
            throw mapCommandFailure(SYSTEM_PACKAGE_PATH, exception);
        } catch (ApiConnectionException exception) {
            throw new MikrotikConnectionException("RouterOS connection failed while reading package information", exception);
        } catch (ApiDataException exception) {
            throw new MikrotikDataException("RouterOS package information could not be decoded", exception);
        } catch (MikrotikApiException exception) {
            throw new MikrotikFacadeException("RouterOS package discovery failed", exception);
        }
    }

    private static List<Map<String, String>> executeBootstrapCommand(ApiConnection connection, String path)
            throws MikrotikFacadeException {
        try {
            return connection.execute(path);
        } catch (ApiCommandException exception) {
            throw mapCommandFailure(path, exception);
        } catch (ApiConnectionException exception) {
            throw new MikrotikConnectionException("RouterOS connection failed during bootstrap command", exception);
        } catch (ApiDataException exception) {
            throw new MikrotikDataException("RouterOS bootstrap response could not be decoded", exception);
        } catch (MikrotikApiException exception) {
            throw new MikrotikFacadeException("RouterOS bootstrap command failed", exception);
        }
    }

    private static RouterOsSystemInfo parseSystemInfo(List<Map<String, String>> rows) throws MikrotikDataException {
        if (rows == null || rows.size() != 1) {
            throw new MikrotikDataException("Expected exactly one /system/resource record");
        }

        RouterOsRecord raw = toRecord(rows.get(0), SYSTEM_RESOURCE_PATH);
        String version = requireNonBlank(raw, "version", SYSTEM_RESOURCE_PATH);
        return new RouterOsSystemInfo(
                version,
                optionalNonBlank(raw, "architecture-name"),
                optionalNonBlank(raw, "board-name"),
                optionalNonBlank(raw, "platform"),
                raw);
    }

    private static List<RouterOsPackage> parsePackages(List<Map<String, String>> rows) throws MikrotikDataException {
        if (rows == null) {
            throw new MikrotikDataException("RouterOS package response was null");
        }
        List<RouterOsPackage> packages = new ArrayList<>(rows.size());
        for (Map<String, String> row : rows) {
            RouterOsRecord raw = toRecord(row, SYSTEM_PACKAGE_PATH);
            packages.add(new RouterOsPackage(
                    requireNonBlank(raw, "name", SYSTEM_PACKAGE_PATH),
                    optionalNonBlank(raw, "version"),
                    raw));
        }
        return List.copyOf(packages);
    }

    private static RouterOsRecord toRecord(Map<String, String> row, String path) throws MikrotikDataException {
        if (row == null) {
            throw new MikrotikDataException("RouterOS bootstrap command returned a null record: " + path);
        }
        try {
            return RouterOsRecord.of(row);
        } catch (RuntimeException exception) {
            throw new MikrotikDataException("RouterOS bootstrap record contains invalid properties: " + path, exception);
        }
    }

    private static String requireNonBlank(RouterOsRecord record, String property, String path)
            throws MikrotikDataException {
        String value = record.require(property);
        if (value.isBlank()) {
            throw new MikrotikDataException("RouterOS bootstrap property is blank: " + path + " " + property);
        }
        return value;
    }

    private static String optionalNonBlank(RouterOsRecord record, String property) {
        return record.find(property).filter(value -> !value.isBlank()).orElse(null);
    }

    private static boolean isExplicitPackageCommandUnavailable(ApiCommandException exception) {
        if (!exception.hasCategory() || exception.getCategory() != 0) {
            return false;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("command");
    }

    private static MikrotikCommandException mapCommandFailure(String path, ApiCommandException exception) {
        Integer category = exception.hasCategory() ? exception.getCategory() : null;
        return new MikrotikCommandException(
                "RouterOS bootstrap command was rejected",
                "bootstrap",
                path,
                category,
                null,
                exception);
    }

    private static MikrotikFacadeException mapConnectFailure(MikrotikApiException exception) {
        if (exception instanceof ApiConnectionException) {
            return new MikrotikConnectionException("Unable to connect to RouterOS", exception);
        }
        if (exception instanceof ApiDataException) {
            return new MikrotikDataException("RouterOS connection setup returned invalid API data", exception);
        }
        return new MikrotikFacadeException("Unable to create RouterOS API connection", exception);
    }

    private static MikrotikFacadeException mapTechnicalLoginFailure(MikrotikApiException exception) {
        if (exception instanceof ApiConnectionException) {
            return new MikrotikConnectionException("RouterOS connection failed during authentication", exception);
        }
        if (exception instanceof ApiDataException) {
            return new MikrotikDataException("RouterOS authentication returned invalid API data", exception);
        }
        return new MikrotikFacadeException("RouterOS authentication failed for a technical reason", exception);
    }

    private static void ensureConnected(SessionLifecycle lifecycle, String message) throws MikrotikConnectionException {
        if (lifecycle.state() != SessionState.BROKEN) {
            return;
        }
        ApiConnectionException cause = lifecycle.failure().orElse(null);
        throw cause == null
                ? new MikrotikConnectionException(message)
                : new MikrotikConnectionException(message, cause);
    }

    private static void closeAfterFailure(ApiConnection connection, Throwable primary) {
        try {
            connection.close();
        } catch (ApiConnectionException closeFailure) {
            primary.addSuppressed(closeFailure);
        }
    }

    @FunctionalInterface
    interface ConnectionFactory {
        ApiConnection connect(SocketFactory socketFactory, String host, int port, int timeout)
                throws MikrotikApiException;
    }

    /**
     * Internal successful bootstrap result. This type is not part of the supported public facade API.
     */
    public static final class BootstrapResult {

        private final ApiConnection connection;
        private final SessionLifecycle lifecycle;
        private final RouterOsEnvironment environment;
        private final java.util.concurrent.Executor callbackExecutor;

        private BootstrapResult(
                ApiConnection connection,
                SessionLifecycle lifecycle,
                RouterOsEnvironment environment,
                java.util.concurrent.Executor callbackExecutor) {
            this.connection = connection;
            this.lifecycle = lifecycle;
            this.environment = environment;
            this.callbackExecutor = callbackExecutor;
        }

        /**
         * Returns the authenticated low-level connection.
         *
         * @return authenticated low-level connection
         */
        public ApiConnection connection() {
            return connection;
        }

        /**
         * Returns the lifecycle registered as the low-level connection listener.
         *
         * @return registered session lifecycle
         */
        public SessionLifecycle lifecycle() {
            return lifecycle;
        }

        /**
         * Returns the immutable environment snapshot.
         *
         * @return immutable environment snapshot
         */
        public RouterOsEnvironment environment() {
            return environment;
        }

        /**
         * Returns the caller-provided callback executor when configured.
         *
         * @return caller-provided callback executor, or {@code null} when the facade must create its default later
         */
        public java.util.concurrent.Executor callbackExecutor() {
            return callbackExecutor;
        }
    }
}
