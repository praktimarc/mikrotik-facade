package io.github.praktimarc.mikrotik.facade.async;

import io.github.praktimarc.mikrotik.facade.dhcp.AsyncDhcpServerApi;
import io.github.praktimarc.mikrotik.facade.firewall.AsyncFirewallApi;
import io.github.praktimarc.mikrotik.facade.interfaces.AsyncInterfacesApi;
import io.github.praktimarc.mikrotik.facade.raw.AsyncRawApi;
import io.github.praktimarc.mikrotik.facade.wifi.AsyncWifiApi;

import java.util.Objects;

/**
 * Mirrored asynchronous facade tree for one RouterOS session.
 */
public final class AsyncMikrotikRtrApi {

    private final AsyncRawApi raw;
    private final AsyncDhcpServerApi dhcpServer;
    private final AsyncFirewallApi firewall;
    private final AsyncWifiApi wifi;
    private final AsyncInterfacesApi interfaces;

    /**
     * Creates the asynchronous facade tree for session wiring.
     *
     * @param raw asynchronous raw API
     * @param dhcpServer asynchronous DHCP server API
     * @param firewall asynchronous firewall API
     * @param wifi asynchronous WiFi/CAPsMAN API
     * @param interfaces asynchronous interface/address API
     */
    public AsyncMikrotikRtrApi(
            AsyncRawApi raw,
            AsyncDhcpServerApi dhcpServer,
            AsyncFirewallApi firewall,
            AsyncWifiApi wifi,
            AsyncInterfacesApi interfaces) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.dhcpServer = Objects.requireNonNull(dhcpServer, "dhcpServer");
        this.firewall = Objects.requireNonNull(firewall, "firewall");
        this.wifi = Objects.requireNonNull(wifi, "wifi");
        this.interfaces = Objects.requireNonNull(interfaces, "interfaces");
    }

    /**
     * Returns the asynchronous raw RouterOS escape hatch.
     *
     * @return asynchronous raw API
     */
    public AsyncRawApi raw() {
        return raw;
    }

    /**
     * Returns the asynchronous typed DHCP server API.
     *
     * @return asynchronous DHCP server API
     */
    public AsyncDhcpServerApi dhcpServer() {
        return dhcpServer;
    }

    /**
     * Returns the asynchronous typed firewall API.
     *
     * @return asynchronous firewall API
     */
    public AsyncFirewallApi firewall() {
        return firewall;
    }

    /**
     * Returns the asynchronous compatibility-aware WiFi/CAPsMAN API.
     *
     * @return asynchronous WiFi/CAPsMAN API
     */
    public AsyncWifiApi wifi() {
        return wifi;
    }

    /** Returns the asynchronous typed interface/address API. */
    public AsyncInterfacesApi interfaces() {
        return interfaces;
    }
}
