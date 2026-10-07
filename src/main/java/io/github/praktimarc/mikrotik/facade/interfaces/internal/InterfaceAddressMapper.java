package io.github.praktimarc.mikrotik.facade.interfaces.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.interfaces.InterfaceAddress;

/** Maps RouterOS IP address rows to typed facade entities. */
public final class InterfaceAddressMapper {
    public InterfaceAddress map(RouterOsRecord raw) throws MikrotikDataException {
        return new InterfaceAddress(
                raw,
                raw.require("address"),
                raw.find(".id"),
                raw.find("network"),
                raw.find("interface"),
                raw.find("actual-interface"),
                raw.find("vrf"),
                raw.find("comment"),
                raw.getBoolean("dynamic"),
                raw.getBoolean("disabled"),
                raw.getBoolean("invalid"),
                raw.getBoolean("slave"));
    }
}
