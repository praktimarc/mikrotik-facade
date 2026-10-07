package io.github.praktimarc.mikrotik.facade.internal.error;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.ApiDataException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionMapperTest {

    @Test
    void mapsConnectionDataAndCommandFailuresToFacadeHierarchy() {
        ApiConnectionException connection = new ApiConnectionException("wire failed");
        TestDataException data = new TestDataException("bad data");
        TestCommandException command = new TestCommandException("rejected", 4);

        assertInstanceOf(MikrotikConnectionException.class,
                ExceptionMapper.map(connection, "read", "/x/print", Map.of(), Map.of()));
        assertInstanceOf(MikrotikDataException.class,
                ExceptionMapper.map(data, "read", "/x/print", Map.of(), Map.of()));
        assertInstanceOf(MikrotikCommandException.class,
                ExceptionMapper.map(command, "read", "/x/print", Map.of(), Map.of()));
    }

    @Test
    void originalLowLevelCauseSurvivesMapping() {
        ApiConnectionException lowLevel = new ApiConnectionException("wire failed");

        MikrotikFacadeException mapped = ExceptionMapper.map(
                lowLevel, "read", "/x/print", Map.of(), Map.of());

        assertSame(lowLevel, mapped.getCause());
    }

    @Test
    void commandCategoryDistinguishesMissingFromRealZero() {
        MikrotikCommandException missing = ExceptionMapper.mapCommand(
                new TestCommandException("missing category", null),
                "read",
                "/x/print",
                Map.of(),
                Map.of());
        MikrotikCommandException zero = ExceptionMapper.mapCommand(
                new TestCommandException("real zero", 0),
                "read",
                "/x/print",
                Map.of(),
                Map.of());

        assertFalse(missing.category().isPresent());
        assertEquals(0, zero.category().orElseThrow());
    }

    @Test
    void commandContextContainsOnlySafePathAndSanitizedRouterOsMessage() {
        TestCommandException lowLevel = new TestCommandException(
                "invalid password=INLINE while checking REFLECTED",
                4);
        Map<String, String> args = Map.of("password", "REFLECTED");

        MikrotikCommandException mapped = ExceptionMapper.mapCommand(
                lowLevel,
                "create user",
                "/user/add =password=INLINE",
                args,
                Map.of());

        assertEquals("create user", mapped.operation().orElseThrow());
        assertEquals("/user/add", mapped.commandPath().orElseThrow());
        assertTrue(mapped.routerOsMessage().orElseThrow().contains("<redacted>"));
        assertFalse(mapped.routerOsMessage().orElseThrow().contains("INLINE"));
        assertFalse(mapped.routerOsMessage().orElseThrow().contains("REFLECTED"));
        assertSame(lowLevel, mapped.getCause());
    }

    @Test
    void commandExceptionRedactsSnmpCommunityNameByPath() {
        MikrotikCommandException mapped = ExceptionMapper.mapCommand(
                new TestCommandException("community private-community rejected", 4),
                "set SNMP community",
                "/snmp/community/set",
                Map.of("name", "private-community"),
                Map.of());

        assertFalse(mapped.routerOsMessage().orElseThrow().contains("private-community"));
        assertTrue(mapped.routerOsMessage().orElseThrow().contains("<redacted>"));
    }


    @Test
    void commandExceptionRedactsReflectedUnknownRawValues() {
        String opaque = "opaque-value-that-must-not-escape";
        String address = "192.0.2.123";
        MikrotikCommandException mapped = ExceptionMapper.mapCommand(
                new TestCommandException(
                        "failure for " + opaque + " at " + address,
                        4),
                "raw future operation",
                "/future/service/set",
                Map.of("future-auth-material", opaque),
                Map.of("address", address));

        String visible = mapped.routerOsMessage().orElseThrow();
        assertFalse(visible.contains(opaque));
        assertFalse(visible.contains(address));
        assertTrue(visible.contains("<redacted>"));
        assertFalse(mapped.toString().contains(opaque));
        assertFalse(mapped.toString().contains(address));
    }

    private static final class TestCommandException extends ApiCommandException {
        private TestCommandException(String message, Integer category) {
            super(message, "transport-tag", category);
        }
    }

    private static final class TestDataException extends ApiDataException {
        private TestDataException(String message) {
            super(message);
        }
    }
}
