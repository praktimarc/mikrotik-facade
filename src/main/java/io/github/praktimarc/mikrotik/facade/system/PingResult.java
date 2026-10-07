package io.github.praktimarc.mikrotik.facade.system;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable aggregate result of one finite RouterOS ping command. */
public final class PingResult implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final List<PingReply> replies;
    private final long sent;
    private final long received;
    private final int packetLossPercent;
    private final Optional<Duration> minRtt;
    private final Optional<Duration> avgRtt;
    private final Optional<Duration> maxRtt;

    /** Creates one aggregate finite ping result. */
    public PingResult(
            RouterOsRecord raw,
            List<PingReply> replies,
            long sent,
            long received,
            int packetLossPercent,
            Optional<Duration> minRtt,
            Optional<Duration> avgRtt,
            Optional<Duration> maxRtt) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.replies = List.copyOf(Objects.requireNonNull(replies, "replies"));
        if (sent < 0 || received < 0) {
            throw new IllegalArgumentException("ping packet counters must not be negative");
        }
        this.sent = sent;
        this.received = received;
        this.packetLossPercent = packetLossPercent;
        this.minRtt = Objects.requireNonNull(minRtt, "minRtt");
        this.avgRtt = Objects.requireNonNull(avgRtt, "avgRtt");
        this.maxRtt = Objects.requireNonNull(maxRtt, "maxRtt");
    }

    public List<PingReply> replies() { return replies; }
    public long sent() { return sent; }
    public long received() { return received; }
    public int packetLossPercent() { return packetLossPercent; }
    public Optional<Duration> minRtt() { return minRtt; }
    public Optional<Duration> avgRtt() { return avgRtt; }
    public Optional<Duration> maxRtt() { return maxRtt; }

    @Override
    public RouterOsRecord raw() { return raw; }
}
