package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.transport.ApiTransport;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MikrotikRtrApiBuilderTest {

    @Test
    void builderUsesPlainTransportAndSixtySecondDefaults() {
        MikrotikRtrApiBuilder.ValidatedConfig config = MikrotikRtrApi.builder()
                .host("router.example")
                .credentials("admin", "secret")
                .validatedConfig();

        assertEquals(ApiTransport.Mode.PLAIN, config.transport().mode());
        assertEquals(8728, config.port());
        assertEquals(Duration.ofSeconds(60), config.connectTimeout());
        assertEquals(60_000, config.connectTimeoutMillis());
        assertEquals(Duration.ofSeconds(60), config.commandTimeout());
        assertEquals(60_000, config.commandTimeoutMillis());
        assertEquals(0, config.bootstrapRetries());
    }

    @Test
    void tlsUses8729UnlessPortIsExplicitlyOverridden() {
        MikrotikRtrApiBuilder.ValidatedConfig defaultTls = MikrotikRtrApi.builder()
                .host("router.example")
                .credentials("admin", "")
                .transport(ApiTransport.tlsVerified())
                .validatedConfig();
        MikrotikRtrApiBuilder.ValidatedConfig overridden = MikrotikRtrApi.builder()
                .host("router.example")
                .credentials("admin", "")
                .transport(ApiTransport.tlsUnverified())
                .port(18_729)
                .validatedConfig();

        assertEquals(8729, defaultTls.port());
        assertEquals(18_729, overridden.port());
    }

    @Test
    void invalidPortTimeoutAndRetryConfigurationFailsBeforeNetworking() {
        MikrotikRtrApiBuilder builder = MikrotikRtrApi.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.port(0));
        assertThrows(IllegalArgumentException.class, () -> builder.port(65_536));
        assertThrows(IllegalArgumentException.class, () -> builder.connectTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.connectTimeout(Duration.ofNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> builder.connectTimeout(Duration.ofNanos(1_500_000)));
        assertThrows(IllegalArgumentException.class,
                () -> builder.connectTimeout(Duration.ofMillis((long) Integer.MAX_VALUE + 1L)));
        assertThrows(IllegalArgumentException.class, () -> builder.commandTimeout(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> builder.bootstrapRetries(-1));
    }

    @Test
    void requiredHostAndCredentialsAreValidatedWithoutInventingPasswordPolicy() {
        assertThrows(IllegalArgumentException.class, () -> MikrotikRtrApi.builder().host("   "));
        assertThrows(IllegalArgumentException.class, () -> MikrotikRtrApi.builder().credentials("   ", "secret"));
        assertThrows(NullPointerException.class, () -> MikrotikRtrApi.builder().credentials("admin", null));
        assertThrows(IllegalStateException.class, () -> MikrotikRtrApi.builder()
                .credentials("admin", "")
                .validatedConfig());
        assertThrows(IllegalStateException.class, () -> MikrotikRtrApi.builder()
                .host("router.example")
                .validatedConfig());

        MikrotikRtrApiBuilder.ValidatedConfig config = MikrotikRtrApi.builder()
                .host(" router.example ")
                .credentials("admin", "")
                .validatedConfig();
        assertEquals("router.example", config.host());
        assertEquals("", config.password());
    }

    @Test
    void validationDoesNotCreateSockets() {
        CountingSocketFactory factory = new CountingSocketFactory();

        MikrotikRtrApi.builder()
                .host("router.example")
                .credentials("admin", "secret")
                .transport(ApiTransport.custom(factory, 19000))
                .validatedConfig();

        assertEquals(0, factory.creations.get());
    }

    @Test
    void callbackExecutorIsReferencedButNotOwnedByBuilder() {
        Executor executor = Runnable::run;

        MikrotikRtrApiBuilder.ValidatedConfig config = MikrotikRtrApi.builder()
                .host("router.example")
                .credentials("admin", "secret")
                .callbackExecutor(executor)
                .validatedConfig();

        assertSame(executor, config.callbackExecutor());
    }

    @Test
    void validatedConfigurationIsSnapshotIndependentFromLaterBuilderChanges() {
        MikrotikRtrApiBuilder builder = MikrotikRtrApi.builder()
                .host("first.example")
                .credentials("admin", "secret")
                .port(10000);
        MikrotikRtrApiBuilder.ValidatedConfig first = builder.validatedConfig();

        builder.host("second.example").port(10001).bootstrapRetries(2);
        MikrotikRtrApiBuilder.ValidatedConfig second = builder.validatedConfig();

        assertEquals("first.example", first.host());
        assertEquals(10000, first.port());
        assertEquals(0, first.bootstrapRetries());
        assertEquals("second.example", second.host());
        assertEquals(10001, second.port());
        assertEquals(2, second.bootstrapRetries());
    }

    @Test
    void diagnosticsNeverExposePasswordValues() {
        String password = "highly-secret-password";
        MikrotikRtrApiBuilder builder = MikrotikRtrApi.builder()
                .host("router.example")
                .credentials("admin", password)
                .transport(ApiTransport.tlsUnverified())
                .port(18729);

        String builderText = builder.toString();
        String configText = builder.validatedConfig().toString();

        assertFalse(builderText.contains(password));
        assertFalse(configText.contains(password));
        assertTrue(builderText.contains("credentials=<configured>"));
        assertTrue(configText.contains("credentials=<configured>"));
        assertTrue(builderText.contains("TLS_UNVERIFIED"));
    }

    private static final class CountingSocketFactory extends SocketFactory {
        private final AtomicInteger creations = new AtomicInteger();

        @Override
        public Socket createSocket() {
            creations.incrementAndGet();
            return new Socket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            creations.incrementAndGet();
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            creations.incrementAndGet();
            Socket socket = new Socket();
            socket.bind(new java.net.InetSocketAddress(localHost, localPort));
            socket.connect(new java.net.InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            creations.incrementAndGet();
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            creations.incrementAndGet();
            return new Socket(address, port, localAddress, localPort);
        }
    }
}
