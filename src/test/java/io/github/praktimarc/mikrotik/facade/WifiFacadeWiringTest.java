package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WifiFacadeWiringTest {

    @Test
    void synchronousAndAsynchronousWifiTreesAreExposedFromSessionRoot() throws Exception {
        RouterOsEnvironment environment = RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo(
                        "7.20.4", "arm64", "test-board", "MikroTik", RouterOsRecord.empty()),
                List.of());
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                new NoOpConnection(),
                new SessionLifecycle(),
                environment,
                Runnable::run)) {
            assertNotNull(api.wifi());
            assertNotNull(api.async().wifi());
        }
    }

    private static final class NoOpConnection extends ApiConnection {
        @Override public void close() {}
    }
}
