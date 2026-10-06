package io.github.praktimarc.mikrotik.facade;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouterOsPropertiesTest {

    @Test
    void preservesInsertionOrderAndExactValues() {
        RouterOsProperties properties = RouterOsProperties.builder()
                .set("chain", "forward")
                .set("comment", "  exact value  ")
                .set("disabled", "")
                .build();

        assertEquals(List.of("chain", "comment", "disabled"),
                List.copyOf(properties.asMap().keySet()));
        assertEquals("  exact value  ", properties.asMap().get("comment"));
        assertEquals("", properties.asMap().get("disabled"));
    }

    @Test
    void rejectsNullKeysAndValues() {
        assertThrows(NullPointerException.class,
                () -> RouterOsProperties.builder().set(null, "value"));
        assertThrows(NullPointerException.class,
                () -> RouterOsProperties.builder().set("key", null));
    }

    @Test
    void resultIsImmutableAndBuilderChangesDoNotLeakIntoBuiltInstance() {
        RouterOsProperties.Builder builder = RouterOsProperties.builder().set("first", "1");
        RouterOsProperties properties = builder.build();
        builder.set("second", "2");

        assertEquals(List.of("first"), List.copyOf(properties.asMap().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> properties.asMap().put("third", "3"));
    }
}
