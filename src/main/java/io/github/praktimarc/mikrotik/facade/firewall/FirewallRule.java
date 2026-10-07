package io.github.praktimarc.mikrotik.facade.firewall;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable typed view of a RouterOS firewall filter or Mangle rule. */
public final class FirewallRule implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final Optional<String> id;
    private final Optional<String> chain;
    private final Optional<String> protocol;
    private final Optional<String> srcAddress;
    private final Optional<String> dstAddress;
    private final Optional<String> srcPort;
    private final Optional<String> dstPort;
    private final Optional<String> inInterface;
    private final Optional<String> outInterface;
    private final Optional<String> inInterfaceList;
    private final Optional<String> outInterfaceList;
    private final OptionalLong bytes;
    private final OptionalLong packets;
    private final Optional<Boolean> invalid;
    private final Optional<Boolean> dynamic;
    private final Optional<Boolean> disabled;
    private final Optional<String> comment;

    public FirewallRule(
            RouterOsRecord raw,
            Optional<String> id,
            Optional<String> chain,
            Optional<String> protocol,
            Optional<String> srcAddress,
            Optional<String> dstAddress,
            Optional<String> srcPort,
            Optional<String> dstPort,
            Optional<String> inInterface,
            Optional<String> outInterface,
            Optional<String> inInterfaceList,
            Optional<String> outInterfaceList,
            OptionalLong bytes,
            OptionalLong packets,
            Optional<Boolean> invalid,
            Optional<Boolean> dynamic,
            Optional<Boolean> disabled,
            Optional<String> comment) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.id = Objects.requireNonNull(id, "id");
        this.chain = Objects.requireNonNull(chain, "chain");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.srcAddress = Objects.requireNonNull(srcAddress, "srcAddress");
        this.dstAddress = Objects.requireNonNull(dstAddress, "dstAddress");
        this.srcPort = Objects.requireNonNull(srcPort, "srcPort");
        this.dstPort = Objects.requireNonNull(dstPort, "dstPort");
        this.inInterface = Objects.requireNonNull(inInterface, "inInterface");
        this.outInterface = Objects.requireNonNull(outInterface, "outInterface");
        this.inInterfaceList = Objects.requireNonNull(inInterfaceList, "inInterfaceList");
        this.outInterfaceList = Objects.requireNonNull(outInterfaceList, "outInterfaceList");
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.invalid = Objects.requireNonNull(invalid, "invalid");
        this.dynamic = Objects.requireNonNull(dynamic, "dynamic");
        this.disabled = Objects.requireNonNull(disabled, "disabled");
        this.comment = Objects.requireNonNull(comment, "comment");
    }

    public Optional<String> id() { return id; }
    public Optional<String> chain() { return chain; }
    public Optional<String> protocol() { return protocol; }
    public Optional<String> srcAddress() { return srcAddress; }
    public Optional<String> dstAddress() { return dstAddress; }
    public Optional<String> srcPort() { return srcPort; }
    public Optional<String> dstPort() { return dstPort; }
    public Optional<String> inInterface() { return inInterface; }
    public Optional<String> outInterface() { return outInterface; }
    public Optional<String> inInterfaceList() { return inInterfaceList; }
    public Optional<String> outInterfaceList() { return outInterfaceList; }
    public OptionalLong bytes() { return bytes; }
    public OptionalLong packets() { return packets; }
    public Optional<Boolean> invalid() { return invalid; }
    public Optional<Boolean> dynamic() { return dynamic; }
    public Optional<Boolean> disabled() { return disabled; }
    public Optional<String> comment() { return comment; }
    @Override public RouterOsRecord raw() { return raw; }
}
