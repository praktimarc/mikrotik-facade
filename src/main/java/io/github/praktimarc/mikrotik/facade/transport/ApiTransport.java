package io.github.praktimarc.mikrotik.facade.transport;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Immutable transport selection for RouterOS API connections.
 *
 * <p>Security-sensitive modes are intentionally explicit. Verified TLS enables
 * both normal certificate-chain validation and HTTPS-style endpoint
 * identification. Unverified TLS is deliberately named as unsafe.</p>
 */
public final class ApiTransport {

    /** Default RouterOS classic API port. */
    public static final int DEFAULT_PLAIN_PORT = 8728;

    /** Default RouterOS API-SSL port. */
    public static final int DEFAULT_TLS_PORT = 8729;

    /**
     * Public transport mode.
     */
    public enum Mode {
        /** Unencrypted classic RouterOS API transport. */
        PLAIN,
        /** TLS with certificate-chain and hostname verification. */
        TLS_VERIFIED,
        /** TLS without server certificate or hostname verification. */
        TLS_UNVERIFIED,
        /** Caller-provided socket factory with caller-defined security semantics. */
        CUSTOM_SOCKET_FACTORY
    }

    private final Mode mode;
    private final SocketFactory socketFactory;
    private final int defaultPort;

    private ApiTransport(Mode mode, SocketFactory socketFactory, int defaultPort) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.socketFactory = Objects.requireNonNull(socketFactory, "socketFactory");
        this.defaultPort = validatePort(defaultPort, "defaultPort");
    }

    /**
     * Uses the classic unencrypted RouterOS API transport.
     *
     * @return plain transport using port 8728 by default
     */
    public static ApiTransport plain() {
        return new ApiTransport(Mode.PLAIN, SocketFactory.getDefault(), DEFAULT_PLAIN_PORT);
    }

    /**
     * Uses TLS with the JVM default trust configuration and hostname
     * verification enabled.
     *
     * @return verified TLS transport using port 8729 by default
     */
    public static ApiTransport tlsVerified() {
        return new ApiTransport(
                Mode.TLS_VERIFIED,
                new EndpointVerifyingSslSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()),
                DEFAULT_TLS_PORT);
    }

    /**
     * Uses TLS with a caller-provided SSL context and hostname verification
     * enabled.
     *
     * @param sslContext caller-provided SSL context, for example with custom trust
     * @return verified TLS transport using port 8729 by default
     */
    public static ApiTransport tlsVerified(SSLContext sslContext) {
        Objects.requireNonNull(sslContext, "sslContext");
        return new ApiTransport(
                Mode.TLS_VERIFIED,
                new EndpointVerifyingSslSocketFactory(sslContext.getSocketFactory()),
                DEFAULT_TLS_PORT);
    }

    /**
     * Uses TLS while accepting every server certificate and without hostname
     * verification.
     *
     * <p>This mode is intentionally unsafe and should only be used when the
     * caller explicitly accepts that loss of server authentication.</p>
     *
     * @return unverified TLS transport using port 8729 by default
     */
    public static ApiTransport tlsUnverified() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new TrustAllManager()}, new SecureRandom());
            return new ApiTransport(Mode.TLS_UNVERIFIED, context.getSocketFactory(), DEFAULT_TLS_PORT);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TLS is not available in the current JVM", exception);
        }
    }

    /**
     * Uses a caller-provided socket factory as an escape hatch.
     *
     * <p>The facade does not modify or infer the security properties of the
     * supplied factory. The caller therefore also supplies the transport's
     * default port.</p>
     *
     * @param socketFactory socket factory to use unchanged
     * @param defaultPort default port used when the facade builder has no override
     * @return custom transport
     */
    public static ApiTransport custom(SocketFactory socketFactory, int defaultPort) {
        return new ApiTransport(
                Mode.CUSTOM_SOCKET_FACTORY,
                Objects.requireNonNull(socketFactory, "socketFactory"),
                defaultPort);
    }

    /**
     * Returns the explicit transport mode.
     *
     * @return transport mode
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Returns the socket factory used by the low-level API connection.
     *
     * @return configured socket factory
     */
    public SocketFactory socketFactory() {
        return socketFactory;
    }

    /**
     * Returns the default port associated with this transport.
     *
     * @return default port
     */
    public int defaultPort() {
        return defaultPort;
    }

    @Override
    public String toString() {
        return "ApiTransport{" +
                "mode=" + mode +
                ", defaultPort=" + defaultPort +
                '}';
    }

    private static int validatePort(int port, String name) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(name + " must be between 1 and 65535");
        }
        return port;
    }

    private static final class EndpointVerifyingSslSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate;

        private EndpointVerifyingSslSocketFactory(SSLSocketFactory delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return configure(delegate.createSocket());
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return configure(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return configure(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return configure(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return configure(delegate.createSocket(address, port, localAddress, localPort));
        }

        private static Socket configure(Socket socket) throws IOException {
            if (!(socket instanceof SSLSocket sslSocket)) {
                socket.close();
                throw new IOException("Verified TLS socket factory did not create an SSLSocket");
            }
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
            return sslSocket;
        }
    }

    private static final class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
