package io.github.praktimarc.mikrotik.facade.dhcp;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable typed view of one RouterOS IP pool. */
public final class DhcpPool implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String name;
    private final Optional<String> id;
    private final List<String> ranges;
    private final Optional<String> nextPool;

    /** Creates one immutable RouterOS pool entity. */
    public DhcpPool(
            RouterOsRecord raw,
            String name,
            Optional<String> id,
            List<String> ranges,
            Optional<String> nextPool) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.name = requireNonBlank(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        this.ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
        this.nextPool = Objects.requireNonNull(nextPool, "nextPool");
    }

    public String name() { return name; }
    public Optional<String> id() { return id; }
    public List<String> ranges() { return ranges; }
    public Optional<String> nextPool() { return nextPool; }

    @Override
    public RouterOsRecord raw() { return raw; }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
