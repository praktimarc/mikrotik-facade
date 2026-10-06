package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionState;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ConnectionListener;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MikrotikRtrApiLifecycleTest {

    @Test
    void environmentAndConfiguredCallbackExecutorRemainSessionSnapshots() throws Exception {
        FakeConnection connection = new FakeConnection();
        SessionLifecycle lifecycle = new SessionLifecycle();
        RouterOsEnvironment environment = environment();
        Executor executor = Runnable::run;
        MikrotikRtrApi api = new MikrotikRtrApi(connection, lifecycle, environment, executor);

        assertSame(environment, api.environment());
        assertSame(executor, api.configuredCallbackExecutor());

        api.close();
        assertEquals(SessionState.CLOSED, lifecycle.state());
    }

    @Test
    void intentionalCloseIsIdempotentAndLateConnectionLossCannotMarkSessionBroken() throws Exception {
        FakeConnection connection = new FakeConnection();
        SessionLifecycle lifecycle = new SessionLifecycle();
        connection.addConnectionListener(lifecycle);
        connection.fireLossDuringClose = true;
        MikrotikRtrApi api = new MikrotikRtrApi(connection, lifecycle, environment(), null);

        api.close();
        api.close();

        assertEquals(1, connection.closeCalls);
        assertEquals(SessionState.CLOSED, lifecycle.state());
    }

    private static RouterOsEnvironment environment() {
        return RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo(
                        "7.20.2",
                        "arm64",
                        "RB5009UG+S+",
                        null,
                        RouterOsRecord.of(Map.of("version", "7.20.2"))),
                List.of());
    }

    private static final class FakeConnection extends ApiConnection {
        private ConnectionListener listener;
        private boolean fireLossDuringClose;
        private int closeCalls;

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void login(String username, String password) {
        }

        @Override
        public List<Map<String, String>> execute(String command) {
            return List.of();
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
            // Keep the reference deliberately to exercise a late/racing callback.
        }

        @Override
        public long downloadFile(String remoteFile, Path localFile) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(String tag) {
        }

        @Override
        public void setTimeout(int timeout) {
        }

        @Override
        public void close() {
            closeCalls++;
            if (fireLossDuringClose && listener != null) {
                listener.connectionLost(new me.legrange.mikrotik.ApiConnectionException("late close callback"));
            }
        }
    }
}
