package io.github.praktimarc.mikrotik.facade.system;

import io.github.praktimarc.mikrotik.facade.system.internal.PingResultMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SystemApiTest {
    @Test
    void pingRequestIsFiniteAndSerializesExplicitInterval() {
        PingRequest request = new PingRequest("192.0.2.1", 10, Duration.ofMillis(500));
        var operation = SystemApi.pingOperation(request, new PingResultMapper());

        assertEquals("/ping", operation.command().path());
        assertEquals("192.0.2.1", operation.command().arguments().get("address"));
        assertEquals("10", operation.command().arguments().get("count"));
        assertEquals("500ms", operation.command().arguments().get("interval"));
    }

    @Test
    void pingRejectsUnboundedOrSubMillisecondRequestShapes() {
        assertThrows(IllegalArgumentException.class, () -> new PingRequest("192.0.2.1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PingRequest("192.0.2.1", 1, Duration.ofNanos(500_000)));
        assertThrows(IllegalArgumentException.class, () -> new PingRequest(" ", 1));
    }
}
