package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;

/** Immutable typed view of one RouterOS IP address assignment. */
public final class InterfaceAddress implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String address;
    private final Optional<String> id;
    private final Optional<String> network;
    private final Optional<String> interfaceName;
    private final Optional<String> actualInterface;
    private final Optional<String> vrf;
    private final Optional<String> comment;
    private final Optional<Boolean> dynamic;
    private final Optional<Boolean> disabled;
    private final Optional<Boolean> invalid;
    private final Optional<Boolean> slave;

    /** Creates one immutable normalized address row. */
    public InterfaceAddress(
            RouterOsRecord raw,
            String address,
            Optional<String> id,
            Optional<String> network,
            Optional<String> interfaceName,
            Optional<String> actualInterface,
            Optional<String> vrf,
            Optional<String> comment,
            Optional<Boolean> dynamic,
            Optional<Boolean> disabled,
            Optional<Boolean> invalid,
            Optional<Boolean> slave) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.address = requireNonBlank(address, "address");
        this.id = Objects.requireNonNull(id, "id");
        this.network = Objects.requireNonNull(network, "network");
        this.interfaceName = Objects.requireNonNull(interfaceName, "interfaceName");
        this.actualInterface = Objects.requireNonNull(actualInterface, "actualInterface");
        this.vrf = Objects.requireNonNull(vrf, "vrf");
        this.comment = Objects.requireNonNull(comment, "comment");
        this.dynamic = Objects.requireNonNull(dynamic, "dynamic");
        this.disabled = Objects.requireNonNull(disabled, "disabled");
        this.invalid = Objects.requireNonNull(invalid, "invalid");
        this.slave = Objects.requireNonNull(slave, "slave");
    }

    public String address() { return address; }
    public Optional<String> id() { return id; }
    public Optional<String> network() { return network; }
    public Optional<String> interfaceName() { return interfaceName; }
    public Optional<String> actualInterface() { return actualInterface; }
    public Optional<String> vrf() { return vrf; }
    public Optional<String> comment() { return comment; }
    public Optional<Boolean> dynamic() { return dynamic; }
    public Optional<Boolean> disabled() { return disabled; }
    public Optional<Boolean> invalid() { return invalid; }
    public Optional<Boolean> slave() { return slave; }

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
