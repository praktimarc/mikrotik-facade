package io.github.praktimarc.mikrotik.facade.firewall.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.firewall.FirewallRule;

import java.util.Objects;

/** Maps raw RouterOS firewall rows to typed firewall rules. */
public final class FirewallRuleMapper {

    /** Creates a stateless firewall-rule mapper. */
    public FirewallRuleMapper() {
    }

    /**
     * Maps one raw firewall row.
     *
     * @param raw complete raw rule record
     * @return typed firewall rule retaining the same raw record
     * @throws MikrotikDataException when a present typed numeric or boolean field is malformed
     */
    public FirewallRule map(RouterOsRecord raw) throws MikrotikDataException {
        Objects.requireNonNull(raw, "raw");
        return new FirewallRule(
                raw,
                raw.find(".id"),
                raw.find("chain"),
                raw.find("protocol"),
                raw.find("src-address"),
                raw.find("dst-address"),
                raw.find("src-port"),
                raw.find("dst-port"),
                raw.find("in-interface"),
                raw.find("out-interface"),
                raw.find("in-interface-list"),
                raw.find("out-interface-list"),
                raw.getLong("bytes"),
                raw.getLong("packets"),
                raw.getBoolean("invalid"),
                raw.getBoolean("dynamic"),
                raw.getBoolean("disabled"),
                raw.find("comment"));
    }
}
