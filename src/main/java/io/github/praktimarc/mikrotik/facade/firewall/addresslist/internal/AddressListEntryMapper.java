package io.github.praktimarc.mikrotik.facade.firewall.addresslist.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.firewall.addresslist.AddressListEntry;

import java.util.Objects;

/** Maps raw RouterOS firewall address-list rows to typed entries. */
public final class AddressListEntryMapper {

    /** Creates a stateless address-list mapper. */
    public AddressListEntryMapper() {
    }

    /**
     * Maps one raw address-list row.
     *
     * @param raw complete raw address-list record
     * @return typed entry retaining the same raw record
     * @throws MikrotikDataException when a present typed boolean or duration is malformed
     */
    public AddressListEntry map(RouterOsRecord raw) throws MikrotikDataException {
        Objects.requireNonNull(raw, "raw");
        return new AddressListEntry(
                raw,
                raw.find(".id"),
                raw.find("list"),
                raw.find("address"),
                raw.getDuration("timeout"),
                raw.find("creation-time"),
                raw.getBoolean("dynamic"),
                raw.getBoolean("disabled"),
                raw.find("comment"));
    }
}
