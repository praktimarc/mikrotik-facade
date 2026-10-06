package io.github.praktimarc.mikrotik.facade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable insertion-ordered RouterOS property set used for flexible writes.
 */
public final class RouterOsProperties {

    private final Map<String, String> properties;

    private RouterOsProperties(Map<String, String> properties) {
        this.properties = immutableOrderedCopy(properties);
    }

    /**
     * Creates a new property builder.
     *
     * @return empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the properties in deterministic insertion order.
     *
     * @return unmodifiable property map
     */
    public Map<String, String> asMap() {
        return properties;
    }

    private static Map<String, String> immutableOrderedCopy(Map<String, String> source) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "RouterOS property key must not be null"),
                Objects.requireNonNull(value, "RouterOS property value must not be null")));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Mutable builder for an immutable RouterOS property set.
     */
    public static final class Builder {

        private final LinkedHashMap<String, String> properties = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds or replaces a RouterOS property.
         *
         * <p>Empty strings are valid RouterOS values and are preserved. Null keys and
         * values are rejected.</p>
         *
         * @param key property name
         * @param value exact property value
         * @return this builder
         */
        public Builder set(String key, String value) {
            properties.put(
                    Objects.requireNonNull(key, "RouterOS property key must not be null"),
                    Objects.requireNonNull(value, "RouterOS property value must not be null"));
            return this;
        }

        /**
         * Builds an immutable snapshot of the current properties.
         *
         * @return immutable property set
         */
        public RouterOsProperties build() {
            return new RouterOsProperties(properties);
        }
    }
}
