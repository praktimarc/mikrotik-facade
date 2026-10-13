package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ping.PingRequest;
import io.github.praktimarc.mikrotik.facade.ping.PingResult;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class PublicApiHierarchyTest {
    @Test
    void syncAndAsyncEntrypointsFollowRouterOsMenus() throws Exception {
        var environment = RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo("7.24.5", "arm64", "test-board", "MikroTik", RouterOsRecord.empty()),
                List.of());
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                new StubApiConnection(), new SessionLifecycle(), environment, Runnable::run)) {
            assertNotNull(api.ip().address());
            assertNotNull(api.ip().pool());
            assertNotNull(api.ip().dhcpServer().lease());
            assertNotNull(api.ip().firewall().filter());
            assertNotNull(api.ip().firewall().mangle());
            assertNotNull(api.ip().firewall().addressList());
            assertNotNull(api.interfaces());
            assertNotNull(api.snmp().community());
            assertNotNull(api.file());
            assertNotNull(api.async().ip().address());
            assertNotNull(api.async().ip().pool());
            assertNotNull(api.async().ip().dhcpServer().lease());
            assertNotNull(api.async().ip().firewall().filter());
            assertNotNull(api.async().snmp().community());
            assertNotNull(api.async().file());
        }
    }

    @Test
    void pingIsExposedAtRootAndOldEntryPointsAreRemoved() throws Exception {
        assertEquals(PingResult.class,
                MikrotikRtrApi.class.getMethod("ping", PingRequest.class).getReturnType());
        assertEquals(CompletableFuture.class,
                io.github.praktimarc.mikrotik.facade.async.AsyncMikrotikRtrApi.class
                        .getMethod("ping", PingRequest.class).getReturnType());
        for (String oldName : List.of("firewall", "dhcpServer", "system", "files")) {
            assertThrows(NoSuchMethodException.class,
                    () -> MikrotikRtrApi.class.getMethod(oldName));
        }
    }
}
