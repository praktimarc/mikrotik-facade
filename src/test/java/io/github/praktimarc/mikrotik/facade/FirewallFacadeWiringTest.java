package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FirewallFacadeWiringTest {

    @Test
    void synchronousAndAsynchronousFirewallTreesAreExposedFromSessionRoot() throws Exception {
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                new NoOpConnection(),
                new SessionLifecycle(),
                environment(),
                Runnable::run)) {
            assertNotNull(api.ip().firewall());
            assertNotNull(api.ip().firewall().filter());
            assertNotNull(api.ip().firewall().mangle());
            assertNotNull(api.ip().firewall().addressList());
            assertNotNull(api.async().ip().firewall());
            assertNotNull(api.async().ip().firewall().filter());
            assertNotNull(api.async().ip().firewall().mangle());
            assertNotNull(api.async().ip().firewall().addressList());
        }
    }

    private static RouterOsEnvironment environment() {
        return RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo(
                        "7.20.4", "arm64", "test-board", "MikroTik", RouterOsRecord.empty()),
                List.of());
    }

    private static final class NoOpConnection extends StubApiConnection {
        @Override public void close() {}
    }
}
