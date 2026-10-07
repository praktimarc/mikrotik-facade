package io.github.praktimarc.mikrotik.facade.snmp.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.snmp.SnmpCommunity;

/** Maps RouterOS SNMP community rows to typed facade entities. */
public final class SnmpCommunityMapper {
    public SnmpCommunity map(RouterOsRecord raw) throws MikrotikDataException {
        return new SnmpCommunity(
                raw,
                raw.require("name"),
                raw.find(".id"),
                raw.find("address"),
                raw.find("security"),
                raw.getBoolean("read-access"),
                raw.getBoolean("write-access"),
                raw.find("authentication-protocol"),
                raw.find("encryption-protocol"),
                raw.getBoolean("disabled"));
    }
}
