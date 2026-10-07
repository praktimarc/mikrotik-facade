package io.github.praktimarc.mikrotik.facade.interfaces.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.interfaces.InterfaceInfo;

/** Maps generic RouterOS interface rows to typed facade entities. */
public final class InterfaceInfoMapper {
    public InterfaceInfo map(RouterOsRecord raw) throws MikrotikDataException {
        return new InterfaceInfo(
                raw,
                raw.require("name"),
                raw.find(".id"),
                raw.find("default-name"),
                raw.find("type"),
                raw.getLong("mtu"),
                raw.getLong("actual-mtu"),
                raw.getLong("l2mtu"),
                raw.getLong("max-l2mtu"),
                raw.find("mac-address"),
                raw.getBoolean("running"),
                raw.getBoolean("dynamic"),
                raw.getBoolean("disabled"),
                raw.find("comment"));
    }
}
