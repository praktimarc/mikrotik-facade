package io.github.praktimarc.mikrotik.facade.transport;

import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTransportTest {

    @Test
    void plainDefaultsTo8728() {
        ApiTransport transport = ApiTransport.plain();

        assertEquals(ApiTransport.Mode.PLAIN, transport.mode());
        assertEquals(8728, transport.defaultPort());
    }

    @Test
    void tlsModesDefaultTo8729AndUnverifiedIsExplicit() {
        ApiTransport transport = ApiTransport.tlsVerified();
        ApiTransport unverified = ApiTransport.tlsUnverified();

        assertEquals(ApiTransport.Mode.TLS_VERIFIED, transport.mode());
        assertEquals(8729, transport.defaultPort());
        assertEquals(ApiTransport.Mode.TLS_UNVERIFIED, unverified.mode());
        assertEquals(8729, unverified.defaultPort());
        assertTrue(unverified.toString().contains("TLS_UNVERIFIED"));
    }

    @Test
    void verifiedTlsEnablesHostnameVerification() throws Exception {
        ApiTransport transport = ApiTransport.tlsVerified();
        Socket socket = transport.socketFactory().createSocket();
        try {
            assertTrue(socket instanceof SSLSocket);
            assertEquals("HTTPS", ((SSLSocket) socket).getSSLParameters().getEndpointIdentificationAlgorithm());
        } finally {
            socket.close();
        }
    }

    @Test
    void verifiedTlsAcceptsCustomSslContextWithoutDroppingHostnameVerification() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);

        ApiTransport transport = ApiTransport.tlsVerified(context);
        Socket socket = transport.socketFactory().createSocket();
        try {
            assertTrue(socket instanceof SSLSocket);
            assertEquals("HTTPS", ((SSLSocket) socket).getSSLParameters().getEndpointIdentificationAlgorithm());
        } finally {
            socket.close();
        }
    }

    @Test
    void customSocketFactoryIsPreservedAndRequiresValidDefaultPort() {
        SocketFactory factory = SocketFactory.getDefault();

        ApiTransport custom = ApiTransport.custom(factory, 19000);

        assertEquals(ApiTransport.Mode.CUSTOM_SOCKET_FACTORY, custom.mode());
        assertEquals(19000, custom.defaultPort());
        assertSame(factory, custom.socketFactory());
        assertThrows(IllegalArgumentException.class, () -> ApiTransport.custom(factory, 0));
        assertThrows(IllegalArgumentException.class, () -> ApiTransport.custom(factory, 65_536));
    }
}
