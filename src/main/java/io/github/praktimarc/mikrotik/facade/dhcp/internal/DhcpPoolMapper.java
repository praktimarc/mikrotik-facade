package io.github.praktimarc.mikrotik.facade.dhcp.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.dhcp.DhcpPool;

import java.util.Arrays;
import java.util.List;

/** Maps RouterOS IP-pool rows without embedding consumer capacity policy. */
public final class DhcpPoolMapper {
    public DhcpPool map(RouterOsRecord raw) throws MikrotikDataException {
        List<String> ranges = raw.find("ranges")
                .map(value -> Arrays.stream(value.split(",", -1))
                        .map(String::trim)
                        .filter(part -> !part.isEmpty())
                        .toList())
                .orElseGet(List::of);
        return new DhcpPool(
                raw,
                raw.require("name"),
                raw.find(".id"),
                ranges,
                raw.find("next-pool"));
    }
}
