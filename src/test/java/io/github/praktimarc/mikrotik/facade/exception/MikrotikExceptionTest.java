package io.github.praktimarc.mikrotik.facade.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MikrotikExceptionTest {

    @Test
    void exceptionHierarchyMatchesPublicContract() {
        assertEquals(Exception.class, MikrotikFacadeException.class.getSuperclass());
        assertInstanceOf(MikrotikFacadeException.class, new MikrotikConnectionException("connection"));
        assertInstanceOf(MikrotikFacadeException.class, new MikrotikAuthenticationException("auth"));
        assertInstanceOf(MikrotikCommandException.class, new MikrotikTimeoutException("timeout"));
        assertInstanceOf(MikrotikCommandException.class, new MikrotikBackpressureException("overflow"));
        assertInstanceOf(MikrotikFacadeException.class, new MikrotikUnsupportedFeatureException("unsupported"));
        assertInstanceOf(MikrotikFacadeException.class, new MikrotikDataException("data"));
        assertInstanceOf(MikrotikFacadeException.class, new MikrotikFileException("file"));
    }

    @Test
    void preservesOriginalCause() {
        IllegalArgumentException cause = new IllegalArgumentException("root cause");
        MikrotikDataException exception = new MikrotikDataException("invalid data", cause);

        assertSame(cause, exception.getCause());
    }

    @Test
    void commandCategoryDistinguishesMissingFromLegitimateZero() {
        MikrotikCommandException missing = new MikrotikCommandException(
                "command failed", "read leases", "/ip/dhcp-server/lease/print",
                null, "failure", null);
        MikrotikCommandException zero = new MikrotikCommandException(
                "command failed", "read leases", "/ip/dhcp-server/lease/print",
                0, "failure", null);

        assertFalse(missing.category().isPresent());
        assertEquals(OptionalInt.of(0), zero.category());
    }

    @Test
    void commandContextDoesNotExposeTransportTagConcept() {
        MikrotikCommandException exception = new MikrotikCommandException(
                "command failed", "read leases", "/ip/dhcp-server/lease/print",
                2, "no such item", null);

        assertEquals("read leases", exception.operation().orElseThrow());
        assertEquals("/ip/dhcp-server/lease/print", exception.commandPath().orElseThrow());
        assertEquals(2, exception.category().orElseThrow());
        assertEquals("no such item", exception.routerOsMessage().orElseThrow());
        assertFalse(Arrays.stream(exception.getClass().getDeclaredFields())
                .anyMatch(field -> field.getName().equals("tag")));
    }
}
