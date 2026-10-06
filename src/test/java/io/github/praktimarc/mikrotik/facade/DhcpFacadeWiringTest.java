package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DhcpFacadeWiringTest {

    @Test
    void synchronousAndAsynchronousDhcpTreesAreExposedFromSessionRoot() throws Exception {
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                new NoOpConnection(),
                new SessionLifecycle(),
                new RouterOsEnvironment(),
                Runnable::run)) {
            assertNotNull(api.dhcpServer());
            assertNotNull(api.async().dhcpServer());
        }
    }

    private static final class NoOpConnection extends ApiConnection {
        @Override
        public void close() {
        }
    }
}
