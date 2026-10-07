package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable typed view of one generic RouterOS interface row. */
public final class InterfaceInfo implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String name;
    private final Optional<String> id;
    private final Optional<String> defaultName;
    private final Optional<String> type;
    private final OptionalLong mtu;
    private final OptionalLong actualMtu;
    private final OptionalLong l2Mtu;
    private final OptionalLong maxL2Mtu;
    private final Optional<String> macAddress;
    private final Optional<Boolean> running;
    private final Optional<Boolean> dynamic;
    private final Optional<Boolean> disabled;
    private final Optional<String> comment;

    /** Creates one immutable normalized interface row. */
    public InterfaceInfo(
            RouterOsRecord raw,
            String name,
            Optional<String> id,
            Optional<String> defaultName,
            Optional<String> type,
            OptionalLong mtu,
            OptionalLong actualMtu,
            OptionalLong l2Mtu,
            OptionalLong maxL2Mtu,
            Optional<String> macAddress,
            Optional<Boolean> running,
            Optional<Boolean> dynamic,
            Optional<Boolean> disabled,
            Optional<String> comment) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.name = requireNonBlank(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        this.defaultName = Objects.requireNonNull(defaultName, "defaultName");
        this.type = Objects.requireNonNull(type, "type");
        this.mtu = Objects.requireNonNull(mtu, "mtu");
        this.actualMtu = Objects.requireNonNull(actualMtu, "actualMtu");
        this.l2Mtu = Objects.requireNonNull(l2Mtu, "l2Mtu");
        this.maxL2Mtu = Objects.requireNonNull(maxL2Mtu, "maxL2Mtu");
        this.macAddress = Objects.requireNonNull(macAddress, "macAddress");
        this.running = Objects.requireNonNull(running, "running");
        this.dynamic = Objects.requireNonNull(dynamic, "dynamic");
        this.disabled = Objects.requireNonNull(disabled, "disabled");
        this.comment = Objects.requireNonNull(comment, "comment");
    }

    public String name() { return name; }
    public Optional<String> id() { return id; }
    public Optional<String> defaultName() { return defaultName; }
    public Optional<String> type() { return type; }
    public OptionalLong mtu() { return mtu; }
    public OptionalLong actualMtu() { return actualMtu; }
    public OptionalLong l2Mtu() { return l2Mtu; }
    public OptionalLong maxL2Mtu() { return maxL2Mtu; }
    public Optional<String> macAddress() { return macAddress; }
    public Optional<Boolean> running() { return running; }
    public Optional<Boolean> dynamic() { return dynamic; }
    public Optional<Boolean> disabled() { return disabled; }
    public Optional<String> comment() { return comment; }

    @Override
    public RouterOsRecord raw() { return raw; }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
