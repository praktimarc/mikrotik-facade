package io.github.praktimarc.mikrotik.facade.system;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable RouterOS ping reply/status record. */
public final class PingReply implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final OptionalLong sequence;
    private final Optional<String> host;
    private final OptionalLong size;
    private final OptionalLong ttl;
    private final Optional<Duration> time;
    private final Optional<String> status;

    /** Creates one typed ping reply/status record. */
    public PingReply(
            RouterOsRecord raw,
            OptionalLong sequence,
            Optional<String> host,
            OptionalLong size,
            OptionalLong ttl,
            Optional<Duration> time,
            Optional<String> status) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.sequence = Objects.requireNonNull(sequence, "sequence");
        this.host = Objects.requireNonNull(host, "host");
        this.size = Objects.requireNonNull(size, "size");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.time = Objects.requireNonNull(time, "time");
        this.status = Objects.requireNonNull(status, "status");
    }

    public OptionalLong sequence() { return sequence; }
    public Optional<String> host() { return host; }
    public OptionalLong size() { return size; }
    public OptionalLong ttl() { return ttl; }
    public Optional<Duration> time() { return time; }
    public Optional<String> status() { return status; }

    @Override
    public RouterOsRecord raw() { return raw; }
}
