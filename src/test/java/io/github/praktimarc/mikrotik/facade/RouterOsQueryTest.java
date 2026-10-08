package io.github.praktimarc.mikrotik.facade;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouterOsQueryTest {

    @Test
    void supportsRepeatedPropertiesAndBooleanComposition() {
        RouterOsQuery query = RouterOsQuery.eq("interface", "cap-a")
                .or(RouterOsQuery.eq("interface", "cap-b"))
                .and(RouterOsQuery.gt("signal", "-80"));

        assertEquals(
                "(interface='cap-a' or interface='cap-b') and signal>'-80'",
                query.expression());
        assertEquals(List.of("interface", "signal"), query.propertyNames());
        assertEquals(List.of("cap-a", "cap-b", "-80"), query.values());
    }

    @Test
    void supportsNotEqualsLessThanGreaterThanAndNegation() {
        assertEquals(
                "disabled!='true'",
                RouterOsQuery.notEq("disabled", "true").expression());
        assertEquals(
                "signal<'-40'",
                RouterOsQuery.lt("signal", "-40").expression());
        assertEquals(
                "signal>'-90'",
                RouterOsQuery.gt("signal", "-90").expression());
        assertEquals(
                "not (disabled='true')",
                RouterOsQuery.eq("disabled", "true").not().expression());
    }

    @Test
    void toStringIsStructuralAndDoesNotExposeValues() {
        RouterOsQuery query = RouterOsQuery.eq("comment", "super-secret-value");

        assertTrue(query.toString().contains("comment"));
        assertFalse(query.toString().contains("super-secret-value"));
    }

    @Test
    void valuesContainingBothQuoteTypesAreRejectedInsteadOfMisserialized() {
        RouterOsQuery query = RouterOsQuery.eq("comment", "both ' and \"");

        assertThrows(IllegalArgumentException.class, query::expression);
    }
}
