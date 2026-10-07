package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.wifi.WifiRemoteCap;

import java.util.Optional;

/** Maps legacy and modern remote-CAP rows to one provenance-preserving entity. */
public final class WifiRemoteCapMapper {
    public WifiRemoteCap map(RouterOsRecord raw, String source) throws MikrotikDataException {
        Optional<String> board = raw.find("board-name").or(() -> raw.find("board"));
        return new WifiRemoteCap(
                raw,
                source,
                raw.find(".id"),
                raw.find("address"),
                raw.find("identity"),
                board,
                raw.find("serial"),
                raw.find("version"),
                raw.find("base-mac"),
                raw.find("common-name"),
                raw.find("state"),
                raw.getDuration("connected-time"),
                raw.getDuration("uptime"));
    }
}
