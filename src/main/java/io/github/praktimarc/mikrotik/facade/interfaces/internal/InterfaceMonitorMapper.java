package io.github.praktimarc.mikrotik.facade.interfaces.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.interfaces.InterfaceMonitorEntry;

/** Maps one RouterOS monitor-traffic sample without assuming driver-specific counters. */
public final class InterfaceMonitorMapper {
    public InterfaceMonitorEntry map(RouterOsRecord raw) throws MikrotikDataException {
        return new InterfaceMonitorEntry(
                raw,
                raw.find("name"),
                raw.getLong("rx-packets-per-second"),
                raw.getLong("tx-packets-per-second"),
                raw.getLong("rx-bits-per-second"),
                raw.getLong("tx-bits-per-second"),
                raw.getLong("fp-rx-packets-per-second"),
                raw.getLong("fp-tx-packets-per-second"),
                raw.getLong("fp-rx-bits-per-second"),
                raw.getLong("fp-tx-bits-per-second"),
                raw.getLong("rx-drops-per-second"),
                raw.getLong("tx-drops-per-second"),
                raw.getLong("rx-errors-per-second"),
                raw.getLong("tx-errors-per-second"),
                raw.getLong("tx-queue-drops-per-second"));
    }
}
