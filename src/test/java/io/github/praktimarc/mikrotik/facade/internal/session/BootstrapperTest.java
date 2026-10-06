package io.github.praktimarc.mikrotik.facade.internal.session;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikAuthenticationException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.ConnectionListener;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapperTest {

    @Test
    void createsEnvironmentOnlyAfterSuccessfulCompleteBootstrap() throws Exception {
        FakeConnection connection = successfulConnection();

        Bootstrapper.BootstrapResult result = bootstrapper(connection).bootstrap(config(0));

        assertEquals("7.20.2", result.environment().systemInfo().version());
        assertEquals("arm64", result.environment().systemInfo().architectureName().orElseThrow());
        assertEquals("preserve-me", result.environment().systemInfo().raw().require("future-property"));
        assertEquals(1, result.environment().packages().orElseThrow().size());
        assertEquals(60_000, connection.timeout);
        assertEquals(SessionState.OPEN, result.lifecycle().state());
    }

    @Test
    void malformedSystemResourceFailsBootstrapAndClosesPartialConnection() {
        FakeConnection connection = successfulConnection();
        connection.resourceRows = List.of(Map.of("architecture-name", "arm64"));

        assertThrows(MikrotikDataException.class, () -> bootstrapper(connection).bootstrap(config(0)));

        assertTrue(connection.closed);
    }

    @Test
    void explicitMissingPackageCommandProducesValidEnvironmentWithoutPackageInformation() throws Exception {
        FakeConnection connection = successfulConnection();
        connection.packageFailure = new TestCommandException("no such command prefix", 0);

        RouterOsEnvironment environment = bootstrapper(connection).bootstrap(config(0)).environment();

        assertTrue(environment.packages().isEmpty());
        assertFalse(environment.packageInformationAvailable());
    }

    @Test
    void packageCommandFailureOtherThanExplicitMissingCommandRemainsFatal() {
        FakeConnection connection = successfulConnection();
        connection.packageFailure = new TestCommandException("permission denied", 4);

        assertThrows(MikrotikCommandException.class, () -> bootstrapper(connection).bootstrap(config(0)));

        assertTrue(connection.closed);
    }

    @Test
    void transportLossDuringEnvironmentDiscoveryBecomesConnectionFailure() {
        FakeConnection connection = successfulConnection();
        connection.resourceFailure = new ApiConnectionException("wire lost");

        assertThrows(MikrotikConnectionException.class, () -> bootstrapper(connection).bootstrap(config(0)));

        assertTrue(connection.closed);
    }

    @Test
    void authenticationRejectIsNotRetried() {
        FakeConnection first = successfulConnection();
        first.loginFailure = new TestCommandException("invalid user name or password", 4);
        QueueFactory factory = new QueueFactory(first, successfulConnection());

        assertThrows(MikrotikAuthenticationException.class,
                () -> new Bootstrapper(factory).bootstrap(config(3)));

        assertEquals(1, factory.opens);
        assertTrue(first.closed);
    }

    @Test
    void technicalConnectFailureRetriesWithFreshConnection() throws Exception {
        FakeConnection second = successfulConnection();
        Bootstrapper.ConnectionFactory factory = new Bootstrapper.ConnectionFactory() {
            private int opens;

            @Override
            public ApiConnection connect(SocketFactory socketFactory, String host, int port, int timeout)
                    throws MikrotikApiException {
                opens++;
                if (opens == 1) {
                    throw new ApiConnectionException("temporary connect failure");
                }
                return second;
            }
        };

        Bootstrapper.BootstrapResult result = new Bootstrapper(factory).bootstrap(config(1));

        assertSame(second, result.connection());
    }

    @Test
    void technicalLoginFailureRetriesWithFreshConnection() throws Exception {
        FakeConnection first = successfulConnection();
        first.loginFailure = new ApiConnectionException("temporary transport failure");
        FakeConnection second = successfulConnection();
        QueueFactory factory = new QueueFactory(first, second);

        Bootstrapper.BootstrapResult result = new Bootstrapper(factory).bootstrap(config(1));

        assertEquals(2, factory.opens);
        assertTrue(first.closed);
        assertSame(second, result.connection());
    }

    @Test
    void connectionListenerIsRegisteredBeforeLogin() throws Exception {
        FakeConnection connection = successfulConnection();
        connection.requireListenerBeforeLogin = true;

        bootstrapper(connection).bootstrap(config(0));

        assertTrue(connection.listenerRegisteredBeforeLogin);
    }

    @Test
    void fatalIdleConnectionLossTransitionsOpenSessionToBroken() throws Exception {
        FakeConnection connection = successfulConnection();
        Bootstrapper.BootstrapResult result = bootstrapper(connection).bootstrap(config(0));

        connection.fireConnectionLoss();

        assertEquals(SessionState.BROKEN, result.lifecycle().state());
        assertTrue(result.lifecycle().failure().isPresent());
    }

    private static Bootstrapper bootstrapper(FakeConnection connection) {
        return new Bootstrapper(new QueueFactory(connection));
    }

    private static BootstrapConfig config(int retries) {
        return new BootstrapConfig(
                "router.example",
                "admin",
                "secret",
                SocketFactory.getDefault(),
                8728,
                60_000,
                60_000,
                retries,
                null);
    }

    private static FakeConnection successfulConnection() {
        FakeConnection connection = new FakeConnection();
        Map<String, String> resource = new LinkedHashMap<>();
        resource.put("version", "7.20.2");
        resource.put("architecture-name", "arm64");
        resource.put("board-name", "RB5009UG+S+");
        resource.put("future-property", "preserve-me");
        connection.resourceRows = new ArrayList<>(List.of(resource));
        connection.packageRows = new ArrayList<>(List.of(Map.of("name", "routeros", "version", "7.20.2")));
        return connection;
    }

    private static final class QueueFactory implements Bootstrapper.ConnectionFactory {
        private final Queue<FakeConnection> connections = new ArrayDeque<>();
        private int opens;

        private QueueFactory(FakeConnection... connections) {
            this.connections.addAll(List.of(connections));
        }

        @Override
        public ApiConnection connect(SocketFactory socketFactory, String host, int port, int timeout)
                throws MikrotikApiException {
            opens++;
            FakeConnection connection = connections.poll();
            if (connection == null) {
                throw new ApiConnectionException("no fake connection available");
            }
            return connection;
        }
    }

    private static final class TestCommandException extends ApiCommandException {
        private static final long serialVersionUID = 1L;

        private TestCommandException(String message, Integer category) {
            super(message, "test", category);
        }
    }

    private static final class FakeConnection extends ApiConnection {
        private ConnectionListener listener;
        private MikrotikApiException loginFailure;
        private MikrotikApiException resourceFailure;
        private ApiCommandException packageFailure;
        private List<Map<String, String>> resourceRows = List.of();
        private List<Map<String, String>> packageRows = List.of();
        private int timeout;
        private boolean closed;
        private boolean requireListenerBeforeLogin;
        private boolean listenerRegisteredBeforeLogin;

        @Override
        public boolean isConnected() {
            return !closed;
        }

        @Override
        public void login(String username, String password) throws MikrotikApiException {
            listenerRegisteredBeforeLogin = listener != null;
            if (requireListenerBeforeLogin && listener == null) {
                throw new AssertionError("ConnectionListener was not registered before login");
            }
            if (loginFailure != null) {
                throw loginFailure;
            }
        }

        @Override
        public List<Map<String, String>> execute(String command) throws MikrotikApiException {
            if ("/system/resource/print".equals(command)) {
                if (resourceFailure != null) {
                    throw resourceFailure;
                }
                return resourceRows;
            }
            if ("/system/package/print".equals(command)) {
                if (packageFailure != null) {
                    throw packageFailure;
                }
                return packageRows;
            }
            throw new AssertionError("Unexpected command: " + command);
        }

        @Override
        public String execute(String command, ResultListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addConnectionListener(ConnectionListener listener) {
            this.listener = listener;
        }

        @Override
        public void removeConnectionListener(ConnectionListener listener) {
            if (this.listener == listener) {
                this.listener = null;
            }
        }

        @Override
        public long downloadFile(String remoteFile, Path localFile) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(String tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void fireConnectionLoss() {
            if (listener == null) {
                throw new AssertionError("No ConnectionListener registered");
            }
            listener.connectionLost(new ApiConnectionException("idle link lost"));
        }
    }
}
