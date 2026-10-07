package io.github.praktimarc.mikrotik.facade.files.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.files.RouterFile;

/** Maps RouterOS file metadata rows to typed facade entities. */
public final class RouterFileMapper {
    public RouterFile map(RouterOsRecord raw) throws MikrotikDataException {
        return new RouterFile(
                raw,
                raw.require("name"),
                raw.find(".id"),
                raw.find("type"),
                raw.getLong("size"),
                raw.find("creation-time"),
                raw.find("last-modified"),
                raw.find("package"),
                raw.find("package-version"));
    }
}
