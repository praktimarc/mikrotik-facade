package io.github.praktimarc.mikrotik.facade.dhcp.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.dhcp.DhcpLease;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;

import java.util.Objects;

/** Maps raw RouterOS DHCP lease rows to the typed public entity. */
public final class DhcpLeaseMapper {

    private final DhcpCircuitIdResolver circuitIdResolver;

    /** Creates a mapper using the explicit v1 relay-id resolver. */
    public DhcpLeaseMapper() {
        this(new DhcpCircuitIdResolver());
    }

    DhcpLeaseMapper(DhcpCircuitIdResolver circuitIdResolver) {
        this.circuitIdResolver = Objects.requireNonNull(circuitIdResolver, "circuitIdResolver");
    }

    /**
     * Maps one immutable raw lease record.
     *
     * @param raw complete raw lease record
     * @return typed lease retaining the same raw record
     * @throws MikrotikDataException when a present typed boolean or duration is malformed
     */
    public DhcpLease map(RouterOsRecord raw) throws MikrotikDataException {
        Objects.requireNonNull(raw, "raw");
        return new DhcpLease(
                raw,
                raw.find("address"),
                raw.find("mac-address"),
                raw.find("client-id"),
                raw.find("address-lists"),
                raw.find("server"),
                raw.find("dhcp-option"),
                raw.find("status"),
                raw.getDuration("expires-after"),
                raw.getDuration("last-seen"),
                raw.find("active-address"),
                raw.find("active-mac-address"),
                raw.find("active-client-id"),
                raw.find("active-server"),
                raw.find("host-name"),
                circuitIdResolver.circuitId(raw),
                circuitIdResolver.remoteId(raw),
                raw.getBoolean("radius"),
                raw.getBoolean("dynamic"),
                raw.getBoolean("blocked"),
                raw.getBoolean("disabled"),
                raw.find("comment"));
    }
}
