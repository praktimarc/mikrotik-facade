package io.github.praktimarc.mikrotik.facade.wifi;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsPackage;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityRegistry;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.wifi.internal.RemoteCapsSourceResolver;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WifiApiIntegrationTest {

    @Test
    void localModernWifiUsesVerifiedRegistrationPathWhenCapsmanManagerIsDisabled() throws Exception {
        ScriptedConnection connection = new ScriptedConnection(Map.of(
                "/interface/wifi/capsman/print", rows(row("enabled", "false")),
                "/interface/wifi/registration-table/print", rows(row(
                        ".id", "*1",
                        "mac-address", "11:22:33:44:55:66",
                        "signal", "-42"))));

        try (Runtime runtime = new Runtime(connection)) {
            WifiApi api = new WifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    environment("7.20.4", List.of("wifi-qcom")),
                    new CapabilityRegistry());

            List<WifiRegistration> registrations = api.registrationTable();

            assertEquals(1, registrations.size());
            assertEquals(-42L, registrations.get(0).signalDbm().orElseThrow());
            assertEquals(List.of(
                    "/interface/wifi/capsman/print",
                    "/interface/wifi/registration-table/print"), connection.commands());
        }
    }

    @Test
    void bothEnabledManagersProduceCompositeRowsWithoutMacDeduplication() throws Exception {
        ScriptedConnection connection = new ScriptedConnection(Map.of(
                "/caps-man/manager/print", rows(row("enabled", "true")),
                "/interface/wifi/capsman/print", rows(row("enabled", "true")),
                "/caps-man/registration-table/print", rows(row(
                        ".id", "*A",
                        "mac-address", "AA:BB:CC:DD:EE:FF",
                        "rx-signal", "-50")),
                "/interface/wifi/registration-table/print", rows(row(
                        ".id", "*A",
                        "mac-address", "AA:BB:CC:DD:EE:FF",
                        "signal", "-51"))));

        try (Runtime runtime = new Runtime(connection)) {
            WifiApi api = new WifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    environment("7.20.4", List.of("wireless")),
                    new CapabilityRegistry());

            List<WifiRegistration> registrations = api.registrationTable();

            assertEquals(2, registrations.size());
            assertEquals("/caps-man/registration-table", registrations.get(0).source());
            assertEquals("/interface/wifi/registration-table", registrations.get(1).source());
        }
    }

    @Test
    void successfulEmptyModernTableDoesNotTriggerLegacyRegistrationFallback() throws Exception {
        ScriptedConnection connection = new ScriptedConnection(Map.of(
                "/caps-man/manager/print", rows(row("enabled", "false")),
                "/interface/wifi/capsman/print", rows(row("enabled", "true")),
                "/interface/wifi/registration-table/print", rows()));

        try (Runtime runtime = new Runtime(connection)) {
            WifiApi api = new WifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    environment("7.20.4", List.of("wireless")),
                    new CapabilityRegistry());

            assertTrue(api.registrationTable().isEmpty());
            assertFalse(connection.commands().contains("/caps-man/registration-table/print"));
        }
    }

    @Test
    void asyncCompositeFutureCancellationPropagatesToCurrentRouterOsOperation() throws Exception {
        ScriptedConnection connection = new ScriptedConnection(Map.of());
        connection.blockTagReturn = true;

        try (Runtime runtime = new Runtime(connection)) {
            AsyncWifiApi api = new AsyncWifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    Runnable::run,
                    environment("7.20.4", List.of("wifi-qcom")),
                    new CapabilityRegistry());

            CompletableFuture<List<WifiRegistration>> future = api.registrationTable();
            assertTrue(connection.executeEntered.await(2, TimeUnit.SECONDS));
            assertTrue(future.cancel(false));
            connection.allowTagReturn.countDown();
            assertTrue(connection.cancelCalled.await(2, TimeUnit.SECONDS));
            assertEquals("tag-1", connection.cancelledTag.get());
        }
    }



    @Test
    void remoteCapsUseOnlyEnabledManagerSourcesAndEmptyModernDoesNotFallback() throws Exception {
        ScriptedConnection connection = new ScriptedConnection(Map.of(
                "/caps-man/manager/print", rows(row("enabled", "false")),
                "/interface/wifi/capsman/print", rows(row("enabled", "true")),
                "/interface/wifi/capsman/remote-cap/print", rows()));

        try (Runtime runtime = new Runtime(connection)) {
            WifiApi api = new WifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    environment("7.20.4", List.of("wireless")),
                    new CapabilityRegistry());

            assertTrue(api.remoteCaps().isEmpty());
            assertFalse(connection.commands().contains("/caps-man/remote-cap/print"));
        }
    }

    @Test
    void bothEnabledManagersProduceCompositeRemoteCapsWithProvenance() throws Exception {
        ScriptedConnection connection = new ScriptedConnection(Map.of(
                "/caps-man/manager/print", rows(row("enabled", "true")),
                "/interface/wifi/capsman/print", rows(row("enabled", "true")),
                "/caps-man/remote-cap/print", rows(row(
                        ".id", "*L",
                        "identity", "legacy-cap",
                        "base-mac", "AA:BB:CC:DD:EE:01",
                        "board", "legacy-board")),
                "/interface/wifi/capsman/remote-cap/print", rows(row(
                        ".id", "*M",
                        "identity", "modern-cap",
                        "base-mac", "AA:BB:CC:DD:EE:02",
                        "board-name", "modern-board"))));

        try (Runtime runtime = new Runtime(connection)) {
            WifiApi api = new WifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    environment("7.20.4", List.of("wireless")),
                    new CapabilityRegistry());

            List<WifiRemoteCap> caps = api.remoteCaps();

            assertEquals(2, caps.size());
            assertEquals("/caps-man/remote-cap", caps.get(0).source());
            assertEquals("/interface/wifi/capsman/remote-cap", caps.get(1).source());
            assertEquals("legacy-board", caps.get(0).boardName().orElseThrow());
            assertEquals("modern-board", caps.get(1).boardName().orElseThrow());
        }
    }

    @Test
    void expectedSingleRemoteCapRejectsAmbiguousCompositeMatch() throws Exception {
        String queryLegacy =
                "/caps-man/remote-cap/print where base-mac='AA:BB:CC:DD:EE:FF'";
        String queryModern =
                "/interface/wifi/capsman/remote-cap/print where base-mac='AA:BB:CC:DD:EE:FF'";
        ScriptedConnection connection = new ScriptedConnection(Map.of(
                "/caps-man/manager/print", rows(row("enabled", "true")),
                "/interface/wifi/capsman/print", rows(row("enabled", "true")),
                queryLegacy, rows(row("identity", "legacy", "base-mac", "AA:BB:CC:DD:EE:FF")),
                queryModern, rows(row("identity", "modern", "base-mac", "AA:BB:CC:DD:EE:FF"))));

        try (Runtime runtime = new Runtime(connection)) {
            WifiApi api = new WifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    environment("7.20.4", List.of("wireless")),
                    new CapabilityRegistry());

            assertThrows(
                    MikrotikDataException.class,
                    () -> api.findRemoteCapByBaseMac("AA:BB:CC:DD:EE:FF"));
        }
    }

    @Test
    void asyncRemoteCapsUsesSameCompositeResolution() throws Exception {
        ScriptedConnection connection = new ScriptedConnection(Map.of(
                "/caps-man/manager/print", rows(row("enabled", "false")),
                "/interface/wifi/capsman/print", rows(row("enabled", "true")),
                "/interface/wifi/capsman/remote-cap/print", rows(row(
                        "identity", "modern-cap",
                        "base-mac", "AA:BB:CC:DD:EE:02"))));

        try (Runtime runtime = new Runtime(connection)) {
            AsyncWifiApi api = new AsyncWifiApi(
                    runtime.engine,
                    new SessionLifecycle(),
                    Runnable::run,
                    environment("7.20.4", List.of("wireless")),
                    new CapabilityRegistry());

            List<WifiRemoteCap> caps = api.remoteCaps().get(2, TimeUnit.SECONDS);

            assertEquals(1, caps.size());
            assertEquals(RemoteCapsSourceResolver.MODERN_SOURCE, caps.get(0).source());
        }
    }
    private static RouterOsEnvironment environment(String version, List<String> packages) {
        RouterOsSystemInfo system = new RouterOsSystemInfo(
                version, "arm64", "test-board", "MikroTik", RouterOsRecord.empty());
        return RouterOsEnvironment.withPackages(system, packages.stream()
                .map(name -> new RouterOsPackage(
                        name,
                        version,
                        RouterOsRecord.of(Map.of("name", name))))
                .toList());
    }

    private static Map<String, String> row(String... keyValues) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    @SafeVarargs
    private static List<Map<String, String>> rows(Map<String, String>... rows) {
        List<Map<String, String>> result = new ArrayList<>(rows.length);
        for (Map<String, String> row : rows) {
            result.add(row);
        }
        return List.copyOf(result);
    }

    private static final class Runtime implements AutoCloseable {
        private final ExecutorService dispatch = Executors.newSingleThreadExecutor();
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private final CommandEngine engine;

        private Runtime(ApiConnection connection) {
            engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(10),
                    dispatch,
                    Runnable::run,
                    scheduler);
        }

        @Override
        public void close() {
            dispatch.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    private static final class ScriptedConnection extends ApiConnection {
        private final Map<String, List<Map<String, String>>> script;
        private final List<String> commands = new ArrayList<>();
        private final AtomicInteger tags = new AtomicInteger();
        private final AtomicReference<String> cancelledTag = new AtomicReference<>();
        private final CountDownLatch executeEntered = new CountDownLatch(1);
        private final CountDownLatch allowTagReturn = new CountDownLatch(1);
        private final CountDownLatch cancelCalled = new CountDownLatch(1);
        private volatile boolean blockTagReturn;

        private ScriptedConnection(Map<String, List<Map<String, String>>> script) {
            this.script = script;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String execute(String command, ResultListener listener) throws MikrotikApiException {
            synchronized (commands) {
                commands.add(command);
            }
            executeEntered.countDown();
            if (blockTagReturn) {
                try {
                    allowTagReturn.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ApiConnectionException("interrupted", interrupted);
                }
            }

            List<Map<String, String>> rows = script.get(command);
            if (rows == null) {
                if (blockTagReturn) {
                    return "tag-" + tags.incrementAndGet();
                }
                throw new AssertionError("Unexpected RouterOS command: " + command);
            }
            rows.forEach(listener::receive);
            listener.completed(Map.of());
            return "tag-" + tags.incrementAndGet();
        }

        @Override
        public void cancel(String tag) {
            cancelledTag.set(tag);
            cancelCalled.countDown();
        }

        @Override
        public void close() {
        }

        private List<String> commands() {
            synchronized (commands) {
                return List.copyOf(commands);
            }
        }
    }
}
