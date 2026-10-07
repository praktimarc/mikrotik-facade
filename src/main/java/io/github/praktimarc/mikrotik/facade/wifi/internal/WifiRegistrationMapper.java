package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.wifi.WifiRegistration;

import java.util.Objects;
import java.util.OptionalLong;

/** Maps source-aware RouterOS registration rows to one normalized WiFi entity. */
public final class WifiRegistrationMapper {

    /** Creates a stateless WiFi registration mapper. */
    public WifiRegistrationMapper() {
    }

    /**
     * Maps one complete raw registration row while retaining its exact source.
     *
     * @param raw complete RouterOS row
     * @param source exact compatibility source identifier
     * @return normalized typed registration
     * @throws MikrotikDataException when a present typed field is malformed
     */
    public WifiRegistration map(RouterOsRecord raw, String source) throws MikrotikDataException {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(source, "source");

        return new WifiRegistration(
                raw,
                source,
                raw.find(".id"),
                raw.find("interface"),
                raw.find("ssid"),
                raw.find("mac-address"),
                raw.getDuration("uptime"),
                signal(raw, source),
                raw.find("tx-rate"),
                raw.find("rx-rate"),
                raw.find("packets"),
                raw.find("bytes"),
                raw.getLong("tx-bits-per-second"),
                raw.getLong("rx-bits-per-second"),
                raw.getLong("vlan-id"),
                raw.getBoolean("authorized"),
                raw.find("eap-identity"));
    }

    private static OptionalLong signal(RouterOsRecord raw, String source) throws MikrotikDataException {
        String primary = RegisteredClientsSourceResolver.LEGACY_SOURCE.equals(source)
                ? "rx-signal"
                : "signal";
        String alternate = "signal".equals(primary) ? "rx-signal" : "signal";

        OptionalLong value = raw.getLong(primary);
        return value.isPresent() ? value : raw.getLong(alternate);
    }
}
