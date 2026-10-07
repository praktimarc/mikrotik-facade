package io.github.praktimarc.mikrotik.facade.queue.type;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable typed view of one RouterOS queue type. */
public final class QueueType implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String name;
    private final Optional<String> id;
    private final Optional<String> kind;
    private final Optional<Boolean> defaultType;
    private final Optional<String> pcqRate;
    private final Optional<String> pcqLimit;
    private final Optional<String> pcqClassifier;
    private final Optional<String> pcqTotalLimit;
    private final Optional<String> pcqBurstRate;
    private final Optional<String> pcqBurstThreshold;
    private final Optional<Duration> pcqBurstTime;
    private final OptionalLong pcqSrcAddressMask;
    private final OptionalLong pcqDstAddressMask;
    private final OptionalLong pcqSrcAddress6Mask;
    private final OptionalLong pcqDstAddress6Mask;

    /** Creates one immutable queue-type entity. */
    public QueueType(
            RouterOsRecord raw,
            String name,
            Optional<String> id,
            Optional<String> kind,
            Optional<Boolean> defaultType,
            Optional<String> pcqRate,
            Optional<String> pcqLimit,
            Optional<String> pcqClassifier,
            Optional<String> pcqTotalLimit,
            Optional<String> pcqBurstRate,
            Optional<String> pcqBurstThreshold,
            Optional<Duration> pcqBurstTime,
            OptionalLong pcqSrcAddressMask,
            OptionalLong pcqDstAddressMask,
            OptionalLong pcqSrcAddress6Mask,
            OptionalLong pcqDstAddress6Mask) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.name = requireNonBlank(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.defaultType = Objects.requireNonNull(defaultType, "defaultType");
        this.pcqRate = Objects.requireNonNull(pcqRate, "pcqRate");
        this.pcqLimit = Objects.requireNonNull(pcqLimit, "pcqLimit");
        this.pcqClassifier = Objects.requireNonNull(pcqClassifier, "pcqClassifier");
        this.pcqTotalLimit = Objects.requireNonNull(pcqTotalLimit, "pcqTotalLimit");
        this.pcqBurstRate = Objects.requireNonNull(pcqBurstRate, "pcqBurstRate");
        this.pcqBurstThreshold = Objects.requireNonNull(pcqBurstThreshold, "pcqBurstThreshold");
        this.pcqBurstTime = Objects.requireNonNull(pcqBurstTime, "pcqBurstTime");
        this.pcqSrcAddressMask = Objects.requireNonNull(pcqSrcAddressMask, "pcqSrcAddressMask");
        this.pcqDstAddressMask = Objects.requireNonNull(pcqDstAddressMask, "pcqDstAddressMask");
        this.pcqSrcAddress6Mask = Objects.requireNonNull(pcqSrcAddress6Mask, "pcqSrcAddress6Mask");
        this.pcqDstAddress6Mask = Objects.requireNonNull(pcqDstAddress6Mask, "pcqDstAddress6Mask");
    }

    public String name() { return name; }
    public Optional<String> id() { return id; }
    public Optional<String> kind() { return kind; }
    public Optional<Boolean> defaultType() { return defaultType; }
    public Optional<String> pcqRate() { return pcqRate; }
    public Optional<String> pcqLimit() { return pcqLimit; }
    public Optional<String> pcqClassifier() { return pcqClassifier; }
    public Optional<String> pcqTotalLimit() { return pcqTotalLimit; }
    public Optional<String> pcqBurstRate() { return pcqBurstRate; }
    public Optional<String> pcqBurstThreshold() { return pcqBurstThreshold; }
    public Optional<Duration> pcqBurstTime() { return pcqBurstTime; }
    public OptionalLong pcqSrcAddressMask() { return pcqSrcAddressMask; }
    public OptionalLong pcqDstAddressMask() { return pcqDstAddressMask; }
    public OptionalLong pcqSrcAddress6Mask() { return pcqSrcAddress6Mask; }
    public OptionalLong pcqDstAddress6Mask() { return pcqDstAddress6Mask; }

    @Override
    public RouterOsRecord raw() { return raw; }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
