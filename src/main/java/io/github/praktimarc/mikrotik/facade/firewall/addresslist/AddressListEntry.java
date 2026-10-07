package io.github.praktimarc.mikrotik.facade.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Immutable typed view of one RouterOS firewall address-list entry. */
public final class AddressListEntry implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final Optional<String> id;
    private final Optional<String> list;
    private final Optional<String> address;
    private final Optional<Duration> timeout;
    private final Optional<String> creationTime;
    private final Optional<Boolean> dynamic;
    private final Optional<Boolean> disabled;
    private final Optional<String> comment;

    public AddressListEntry(
            RouterOsRecord raw,
            Optional<String> id,
            Optional<String> list,
            Optional<String> address,
            Optional<Duration> timeout,
            Optional<String> creationTime,
            Optional<Boolean> dynamic,
            Optional<Boolean> disabled,
            Optional<String> comment) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.id = Objects.requireNonNull(id, "id");
        this.list = Objects.requireNonNull(list, "list");
        this.address = Objects.requireNonNull(address, "address");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.creationTime = Objects.requireNonNull(creationTime, "creationTime");
        this.dynamic = Objects.requireNonNull(dynamic, "dynamic");
        this.disabled = Objects.requireNonNull(disabled, "disabled");
        this.comment = Objects.requireNonNull(comment, "comment");
    }

    public Optional<String> id() { return id; }
    public Optional<String> list() { return list; }
    public Optional<String> address() { return address; }
    public Optional<Duration> timeout() { return timeout; }
    public Optional<String> creationTime() { return creationTime; }
    public Optional<Boolean> dynamic() { return dynamic; }
    public Optional<Boolean> disabled() { return disabled; }
    public Optional<String> comment() { return comment; }
    @Override public RouterOsRecord raw() { return raw; }
}
