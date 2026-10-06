package io.github.praktimarc.mikrotik.facade.dhcp.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves DHCP relay identifiers from explicitly known RouterOS lease schemas only.
 */
public final class DhcpCircuitIdResolver {

    /** Creates a stateless resolver. */
    public DhcpCircuitIdResolver() {
    }

    /**
     * Resolves the normalized agent circuit id from known lease fields.
     *
     * <p>The active form is preferred when present. No fuzzy property-name matching is used.</p>
     *
     * @param raw complete lease record
     * @return known circuit id or empty when no supported representation is present
     */
    public Optional<String> circuitId(RouterOsRecord raw) {
        Objects.requireNonNull(raw, "raw");
        return raw.find("active-agent-circuit-id")
                .or(() -> raw.find("agent-circuit-id"));
    }

    /**
     * Resolves the normalized agent remote id from known lease fields.
     *
     * <p>The active form is preferred when present. No fuzzy property-name matching is used.</p>
     *
     * @param raw complete lease record
     * @return known remote id or empty when no supported representation is present
     */
    public Optional<String> remoteId(RouterOsRecord raw) {
        Objects.requireNonNull(raw, "raw");
        return raw.find("active-agent-remote-id")
                .or(() -> raw.find("agent-remote-id"));
    }
}
