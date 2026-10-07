package io.github.praktimarc.mikrotik.facade.queue.type.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.queue.type.QueueType;

/** Maps RouterOS queue-type rows to typed facade entities. */
public final class QueueTypeMapper {
    public QueueType map(RouterOsRecord raw) throws MikrotikDataException {
        return new QueueType(
                raw,
                raw.require("name"),
                raw.find(".id"),
                raw.find("kind"),
                raw.getBoolean("default"),
                raw.find("pcq-rate"),
                raw.find("pcq-limit"),
                raw.find("pcq-classifier"),
                raw.find("pcq-total-limit"),
                raw.find("pcq-burst-rate"),
                raw.find("pcq-burst-threshold"),
                raw.getDuration("pcq-burst-time"),
                raw.getLong("pcq-src-address-mask"),
                raw.getLong("pcq-dst-address-mask"),
                raw.getLong("pcq-src-address6-mask"),
                raw.getLong("pcq-dst-address6-mask"));
    }
}
