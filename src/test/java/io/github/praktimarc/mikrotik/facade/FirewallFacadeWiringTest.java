package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FirewallFacadeWiringTest {

    @Test
    void synchronousAndAsynchronousFirewallTreesAreExposedFromSessionRoot() throws Exception {
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                new NoOpConnection(),
                new SessionLifecycle(),
                new RouterOsEnvironment(),
                Runnable::run)) {
            assertNotNull(api.firewall());
            assertNotNull(api.firewall().filter());
            assertNotNull(api.firewall().mangle());
            assertNotNull(api.firewall().addressList());
            assertNotNull(api.async().firewall());
            assertNotNull(api.async().firewall().filter());
            assertNotNull(api.async().firewall().mangle());
            assertNotNull(api.async().firewall().addressList());
        }
    }

    private static final class NoOpConnection extends ApiConnection {
        @Override public void close() {}
    }
}
