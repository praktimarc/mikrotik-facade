package io.github.praktimarc.mikrotik.facade.integration;

import io.github.praktimarc.mikrotik.facade.MikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.MikrotikRtrApiBuilder;
import io.github.praktimarc.mikrotik.facade.transport.ApiTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Credential-gated read-only smoke/integration test for a real RouterOS target.
 *
 * <p>This class is intentionally named {@code *RouterIT}; normal Surefire runs
 * do not execute it. The Maven {@code router-it} profile activates it through
 * Failsafe.</p>
 */
class RouterReadOnlyRouterIT {

    @Test
    void realRouterBootstrapAndParallelReadOnlyCommands() throws Exception {
        RouterTarget target = RouterTarget.fromEnvironment();
        MikrotikRtrApiBuilder builder = MikrotikRtrApi.builder()
                .host(target.host())
                .credentials(target.username(), target.password())
                .transport(target.transport())
                .connectTimeout(Duration.ofSeconds(10))
                .commandTimeout(Duration.ofSeconds(15));
        target.port().ifPresent(builder::port);

        try (MikrotikRtrApi api = builder.connect()) {
            assertFalse(api.environment().systemInfo().version().isBlank());

            // Universal read-only path, also proving the synchronous facade remains usable.
            assertNotNull(api.interfaces().list());

            // Multiple finite commands are intentionally active on one authenticated session.
            CompletableFuture<?> interfacesA = api.async().interfaces().list();
            CompletableFuture<?> interfacesB = api.async().interfaces().list();
            CompletableFuture<?> addressesA = api.async().interfaces().addresses();
            CompletableFuture<?> addressesB = api.async().interfaces().addresses();

            CompletableFuture.allOf(
                            interfacesA,
                            interfacesB,
                            addressesA,
                            addressesB)
                    .get(20, TimeUnit.SECONDS);

            assertTrue(interfacesA.isDone());
            assertTrue(interfacesB.isDone());
            assertTrue(addressesA.isDone());
            assertTrue(addressesB.isDone());
        }
    }

    private enum RouterProfile {
        ROS6_LEGACY,
        ROS7_NO_WIFI,
        ROS7_LEGACY_WIRELESS,
        ROS7_MODERN_WIFI,
        LEGACY_CAPSMAN,
        MODERN_WIFI_CAPSMAN,
        PARALLEL_CAPSMAN
    }

    private record RouterTarget(
            String host,
            String username,
            String password,
            RouterProfile profile,
            ApiTransport transport,
            java.util.OptionalInt port) {

        private RouterTarget {
            requireNonBlank(host, "MIKROTIK_IT_HOST");
            requireNonBlank(username, "MIKROTIK_IT_USERNAME");
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(transport, "transport");
            Objects.requireNonNull(port, "port");
        }

        static RouterTarget fromEnvironment() {
            String host = requireEnvironmentNonBlank("MIKROTIK_IT_HOST");
            String username = requireEnvironmentNonBlank("MIKROTIK_IT_USERNAME");
            String password = requireEnvironmentPresent("MIKROTIK_IT_PASSWORD");
            RouterProfile profile = parseProfile(
                    requireEnvironmentNonBlank("MIKROTIK_IT_PROFILE"));
            ApiTransport transport = parseTransport(
                    System.getenv("MIKROTIK_IT_TRANSPORT"));
            java.util.OptionalInt port = parsePort(System.getenv("MIKROTIK_IT_PORT"));
            return new RouterTarget(host, username, password, profile, transport, port);
        }

        @Override
        public String toString() {
            return "RouterTarget{"
                    + "host=<configured>"
                    + ", credentials=<configured>"
                    + ", profile=" + profile
                    + ", transport=" + transport.mode()
                    + ", port=" + (port.isPresent() ? port.getAsInt() : "<default>")
                    + '}';
        }

        private static RouterProfile parseProfile(String value) {
            try {
                return RouterProfile.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "MIKROTIK_IT_PROFILE must be one of "
                                + java.util.Arrays.toString(RouterProfile.values()),
                        exception);
            }
        }

        private static ApiTransport parseTransport(String raw) {
            String value = raw == null || raw.isBlank()
                    ? "PLAIN"
                    : raw.trim().toUpperCase(Locale.ROOT);
            return switch (value) {
                case "PLAIN" -> ApiTransport.plain();
                case "TLS_VERIFIED" -> ApiTransport.tlsVerified();
                case "TLS_UNVERIFIED" -> ApiTransport.tlsUnverified();
                default -> throw new IllegalStateException(
                        "MIKROTIK_IT_TRANSPORT must be PLAIN, TLS_VERIFIED, or TLS_UNVERIFIED");
            };
        }

        private static java.util.OptionalInt parsePort(String raw) {
            if (raw == null || raw.isBlank()) {
                return java.util.OptionalInt.empty();
            }
            final int port;
            try {
                port = Integer.parseInt(raw.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("MIKROTIK_IT_PORT must be an integer", exception);
            }
            if (port < 1 || port > 65_535) {
                throw new IllegalStateException("MIKROTIK_IT_PORT must be between 1 and 65535");
            }
            return java.util.OptionalInt.of(port);
        }

        private static String requireEnvironmentNonBlank(String name) {
            String value = requireEnvironmentPresent(name);
            if (value.isBlank()) {
                throw new IllegalStateException(name + " must not be blank");
            }
            return value;
        }

        private static String requireEnvironmentPresent(String name) {
            String value = System.getenv(name);
            if (value == null) {
                throw new IllegalStateException(
                        name + " is required when the router-it Maven profile is enabled");
            }
            return value;
        }

        private static String requireNonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
