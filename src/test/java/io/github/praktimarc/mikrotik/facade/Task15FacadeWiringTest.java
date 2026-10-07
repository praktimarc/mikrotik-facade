package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class Task15FacadeWiringTest {
    @Test
    void queueSnmpAndSystemTreesAreExposedSynchronouslyAndAsynchronously() throws Exception {
        RouterOsEnvironment environment = RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo(
                        "7.20.4", "arm64", "test-board", "MikroTik", RouterOsRecord.empty()),
                List.of());
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                new NoOpConnection(),
                new SessionLifecycle(),
                environment,
                Runnable::run)) {
            assertNotNull(api.queue());
            assertNotNull(api.queue().type());
            assertNotNull(api.snmp());
            assertNotNull(api.system());
            assertNotNull(api.async().queue());
            assertNotNull(api.async().queue().type());
            assertNotNull(api.async().snmp());
            assertNotNull(api.async().system());
        }
    }

    private static final class NoOpConnection extends StubApiConnection {}
}
