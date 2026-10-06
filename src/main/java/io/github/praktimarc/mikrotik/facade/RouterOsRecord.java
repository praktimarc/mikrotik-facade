package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable raw representation of one RouterOS record.
 *
 * <p>All property names and string values are retained exactly as received. Typed
 * accessors are convenience conversions and never remove the original value.</p>
 */
public final class RouterOsRecord {

    private static final Pattern CLOCK_DURATION = Pattern.compile(
            "^(?:(\\d+)w)?(?:(\\d+)d)?(\\d{1,2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?$");
    private static final Pattern DURATION_COMPONENT = Pattern.compile("(\\d+)(ns|us|ms|w|d|h|m|s)");
    private static final Pattern PLAIN_SECONDS = Pattern.compile("\\d+");

    private final Map<String, String> properties;

    private RouterOsRecord(Map<String, String> properties) {
        this.properties = immutableOrderedCopy(properties);
    }

    /**
     * Creates a record from a RouterOS property map.
     *
     * @param properties raw properties
     * @return immutable record
     */
    public static RouterOsRecord of(Map<String, String> properties) {
        return new RouterOsRecord(Objects.requireNonNull(properties, "properties"));
    }

    /**
     * Returns an empty record.
     *
     * @return empty immutable record
     */
    public static RouterOsRecord empty() {
        return new RouterOsRecord(Map.of());
    }

    /**
     * Looks up an optional raw string property.
     *
     * @param key property name
     * @return exact value when present
     */
    public Optional<String> find(String key) {
        return Optional.ofNullable(properties.get(Objects.requireNonNull(key, "key")));
    }

    /**
     * Returns a required raw string property.
     *
     * @param key property name
     * @return exact value
     * @throws MikrotikDataException if the property is absent
     */
    public String require(String key) throws MikrotikDataException {
        String value = properties.get(Objects.requireNonNull(key, "key"));
        if (value == null) {
            throw new MikrotikDataException("Missing required RouterOS property: " + key);
        }
        return value;
    }

    /**
     * Converts an optional property to a decimal long.
     *
     * @param key property name
     * @return empty when absent
     * @throws MikrotikDataException if the value is present but not a valid decimal long
     */
    public OptionalLong getLong(String key) throws MikrotikDataException {
        Optional<String> value = find(key);
        if (value.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value.orElseThrow()));
        } catch (NumberFormatException exception) {
            throw conversionError(key, value.orElseThrow(), "long", exception);
        }
    }

    /**
     * Converts a required property to a decimal long.
     *
     * @param key property name
     * @return converted value
     * @throws MikrotikDataException if the property is absent or malformed
     */
    public long requireLong(String key) throws MikrotikDataException {
        OptionalLong value = getLong(key);
        if (value.isEmpty()) {
            throw new MikrotikDataException("Missing required RouterOS property: " + key);
        }
        return value.getAsLong();
    }

    /**
     * Converts an optional property to a boolean.
     *
     * <p>RouterOS boolean representations {@code true}/{@code false} and
     * {@code yes}/{@code no} are accepted case-insensitively.</p>
     *
     * @param key property name
     * @return empty when absent
     * @throws MikrotikDataException if the value is present but not a known boolean
     */
    public Optional<Boolean> getBoolean(String key) throws MikrotikDataException {
        Optional<String> value = find(key);
        if (value.isEmpty()) {
            return Optional.empty();
        }

        return switch (value.orElseThrow().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes" -> Optional.of(Boolean.TRUE);
            case "false", "no" -> Optional.of(Boolean.FALSE);
            default -> throw conversionError(key, value.orElseThrow(), "boolean", null);
        };
    }

    /**
     * Converts a required property to a boolean.
     *
     * @param key property name
     * @return converted value
     * @throws MikrotikDataException if the property is absent or malformed
     */
    public boolean requireBoolean(String key) throws MikrotikDataException {
        return getBoolean(key).orElseThrow(
                () -> new MikrotikDataException("Missing required RouterOS property: " + key));
    }

    /**
     * Converts an optional RouterOS time interval to {@link Duration}.
     *
     * <p>Supported forms include component values such as {@code 2d20h12m20s},
     * sub-second values such as {@code 417us}, documented clock-style intervals
     * such as {@code 00:00:10} or {@code 1d00:00:00}, and plain integer seconds.</p>
     *
     * @param key property name
     * @return empty when absent
     * @throws MikrotikDataException if the value is present but cannot be parsed
     */
    public Optional<Duration> getDuration(String key) throws MikrotikDataException {
        Optional<String> value = find(key);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseDuration(key, value.orElseThrow()));
    }

    /**
     * Converts a required RouterOS time interval to {@link Duration}.
     *
     * @param key property name
     * @return converted duration
     * @throws MikrotikDataException if the property is absent or malformed
     */
    public Duration requireDuration(String key) throws MikrotikDataException {
        return getDuration(key).orElseThrow(
                () -> new MikrotikDataException("Missing required RouterOS property: " + key));
    }

    /**
     * Returns all exact raw properties in their original insertion order.
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

    private static Duration parseDuration(String key, String value) throws MikrotikDataException {
        try {
            Matcher clockMatcher = CLOCK_DURATION.matcher(value);
            if (clockMatcher.matches()) {
                long weeks = parsedLong(clockMatcher.group(1));
                long days = parsedLong(clockMatcher.group(2));
                long hours = parsedLong(clockMatcher.group(3));
                long minutes = parsedLong(clockMatcher.group(4));
                long seconds = parsedLong(clockMatcher.group(5));
                if (minutes >= 60 || seconds >= 60) {
                    throw new IllegalArgumentException("Invalid clock component");
                }

                Duration duration = Duration.ZERO
                        .plusDays(Math.addExact(Math.multiplyExact(weeks, 7), days))
                        .plusHours(hours)
                        .plusMinutes(minutes)
                        .plusSeconds(seconds);

                String fraction = clockMatcher.group(6);
                if (fraction != null) {
                    String nanos = (fraction + "000000000").substring(0, 9);
                    duration = duration.plusNanos(Long.parseLong(nanos));
                }
                return duration;
            }

            if (PLAIN_SECONDS.matcher(value).matches()) {
                return Duration.ofSeconds(Long.parseLong(value));
            }

            Matcher componentMatcher = DURATION_COMPONENT.matcher(value);
            int position = 0;
            Duration duration = Duration.ZERO;
            boolean matched = false;
            while (componentMatcher.find()) {
                if (componentMatcher.start() != position) {
                    throw new IllegalArgumentException("Unparsed duration content");
                }
                matched = true;
                long amount = Long.parseLong(componentMatcher.group(1));
                duration = addComponent(duration, amount, componentMatcher.group(2));
                position = componentMatcher.end();
            }
            if (!matched || position != value.length()) {
                throw new IllegalArgumentException("Unsupported RouterOS duration");
            }
            return duration;
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw conversionError(key, value, "duration", exception);
        }
    }

    private static Duration addComponent(Duration duration, long amount, String unit) {
        return switch (unit) {
            case "w" -> duration.plusDays(Math.multiplyExact(amount, 7));
            case "d" -> duration.plusDays(amount);
            case "h" -> duration.plusHours(amount);
            case "m" -> duration.plusMinutes(amount);
            case "s" -> duration.plusSeconds(amount);
            case "ms" -> duration.plusMillis(amount);
            case "us" -> duration.plusNanos(Math.multiplyExact(amount, 1_000));
            case "ns" -> duration.plusNanos(amount);
            default -> throw new IllegalArgumentException("Unsupported duration unit");
        };
    }

    private static long parsedLong(String value) {
        return value == null ? 0 : Long.parseLong(value);
    }

    private static MikrotikDataException conversionError(
            String key,
            String value,
            String targetType,
            Throwable cause) {
        String message = "RouterOS property '" + key + "' cannot be converted to " + targetType;
        return cause == null
                ? new MikrotikDataException(message)
                : new MikrotikDataException(message, cause);
    }
}
