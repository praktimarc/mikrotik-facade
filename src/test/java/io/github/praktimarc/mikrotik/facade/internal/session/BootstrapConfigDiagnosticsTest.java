package io.github.praktimarc.mikrotik.facade.internal.session;

import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapConfigDiagnosticsTest {
    @Test
    void toStringHidesHostAndCredentials() {
        BootstrapConfig config = new BootstrapConfig(
                "router.internal.example",
                "admin-user",
                "super-secret",
                SocketFactory.getDefault(),
                8728,
                5000,
                60000,
                1,
                null);

        String rendered = config.toString();

        assertTrue(rendered.contains("host=<configured>"));
        assertTrue(rendered.contains("credentials=<configured>"));
        assertFalse(rendered.contains("router.internal.example"));
        assertFalse(rendered.contains("admin-user"));
        assertFalse(rendered.contains("super-secret"));
    }
}
