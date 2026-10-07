package io.github.praktimarc.mikrotik.facade.system;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Immutable finite RouterOS ping request. */
public final class PingRequest {
    private final String address;
    private final int count;
    private final Optional<Duration> interval;

    /** Creates a finite ping request using RouterOS's default interval. */
    public PingRequest(String address, int count) {
        this(address, count, Optional.empty());
    }

    /** Creates a finite ping request with an explicit interval. */
    public PingRequest(String address, int count, Duration interval) {
        this(address, count, Optional.of(Objects.requireNonNull(interval, "interval")));
    }

    private PingRequest(String address, int count, Optional<Duration> interval) {
        this.address = requireNonBlank(address, "address");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        this.count = count;
        this.interval = Objects.requireNonNull(interval, "interval");
        interval.ifPresent(PingRequest::validateInterval);
    }

    public String address() { return address; }
    public int count() { return count; }
    public Optional<Duration> interval() { return interval; }

    private static void validateInterval(Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        long millis;
        try {
            millis = interval.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("interval is too large", overflow);
        }
        if (millis < 1 || !interval.equals(Duration.ofMillis(millis))) {
            throw new IllegalArgumentException("interval must be positive whole milliseconds");
        }
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
