package io.github.praktimarc.mikrotik.facade.wifi;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Immutable normalized view of one legacy or modern CAPsMAN remote CAP. */
public final class WifiRemoteCap implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String source;
    private final Optional<String> id;
    private final Optional<String> address;
    private final Optional<String> identity;
    private final Optional<String> boardName;
    private final Optional<String> serial;
    private final Optional<String> version;
    private final Optional<String> baseMac;
    private final Optional<String> commonName;
    private final Optional<String> state;
    private final Optional<Duration> connectedTime;
    private final Optional<Duration> uptime;

    /** Creates one immutable source-preserving remote-CAP entity. */
    public WifiRemoteCap(
            RouterOsRecord raw,
            String source,
            Optional<String> id,
            Optional<String> address,
            Optional<String> identity,
            Optional<String> boardName,
            Optional<String> serial,
            Optional<String> version,
            Optional<String> baseMac,
            Optional<String> commonName,
            Optional<String> state,
            Optional<Duration> connectedTime,
            Optional<Duration> uptime) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.source = requireNonBlank(source, "source");
        this.id = Objects.requireNonNull(id, "id");
        this.address = Objects.requireNonNull(address, "address");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.boardName = Objects.requireNonNull(boardName, "boardName");
        this.serial = Objects.requireNonNull(serial, "serial");
        this.version = Objects.requireNonNull(version, "version");
        this.baseMac = Objects.requireNonNull(baseMac, "baseMac");
        this.commonName = Objects.requireNonNull(commonName, "commonName");
        this.state = Objects.requireNonNull(state, "state");
        this.connectedTime = Objects.requireNonNull(connectedTime, "connectedTime");
        this.uptime = Objects.requireNonNull(uptime, "uptime");
    }

    public String source() { return source; }
    public Optional<String> id() { return id; }
    public Optional<String> address() { return address; }
    public Optional<String> identity() { return identity; }
    public Optional<String> boardName() { return boardName; }
    public Optional<String> serial() { return serial; }
    public Optional<String> version() { return version; }
    public Optional<String> baseMac() { return baseMac; }
    public Optional<String> commonName() { return commonName; }
    public Optional<String> state() { return state; }
    public Optional<Duration> connectedTime() { return connectedTime; }
    public Optional<Duration> uptime() { return uptime; }

    @Override
    public RouterOsRecord raw() { return raw; }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
