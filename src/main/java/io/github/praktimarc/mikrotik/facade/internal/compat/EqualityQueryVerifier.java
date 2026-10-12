package io.github.praktimarc.mikrotik.facade.internal.compat;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Map;
import java.util.Objects;

/**
 * Verifies typed equality-query results against the properties actually returned by RouterOS.
 *
 * <p>Some RouterOS menus ignore server-side queries. This check never treats a missing
 * property as a match, ignores case, and recognizes RouterOS boolean aliases.</p>
 */
public final class EqualityQueryVerifier {
    private EqualityQueryVerifier() {}

    /** Matches all expected properties against their actual raw record values. */
    public static boolean matches(RouterOsRecord record, RouterOsProperties expected) {
        Objects.requireNonNull(expected, "expected");
        return matches(record, expected.asMap());
    }

    /** Matches all expected properties against their actual raw record values. */
    public static boolean matches(RouterOsRecord record, Map<String, String> expected) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(expected, "expected");
        for (Map.Entry<String, String> criterion : expected.entrySet()) {
            String actual = record.find(criterion.getKey()).orElse(null);
            if (actual == null || !equivalent(actual, criterion.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean equivalent(String actual, String expected) {
        Objects.requireNonNull(expected, "expected");
        if (actual.equalsIgnoreCase(expected)) {
            return true;
        }
        Boolean actualBoolean = booleanValue(actual);
        Boolean expectedBoolean = booleanValue(expected);
        return actualBoolean != null && actualBoolean.equals(expectedBoolean);
    }

    private static Boolean booleanValue(String value) {
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
