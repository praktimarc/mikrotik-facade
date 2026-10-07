package io.github.praktimarc.mikrotik.facade.async;

import io.github.praktimarc.mikrotik.facade.dhcp.AsyncDhcpServerApi;
import io.github.praktimarc.mikrotik.facade.firewall.AsyncFirewallApi;
import io.github.praktimarc.mikrotik.facade.files.AsyncFilesApi;
import io.github.praktimarc.mikrotik.facade.interfaces.AsyncInterfacesApi;
import io.github.praktimarc.mikrotik.facade.queue.AsyncQueueApi;
import io.github.praktimarc.mikrotik.facade.raw.AsyncRawApi;
import io.github.praktimarc.mikrotik.facade.snmp.AsyncSnmpApi;
import io.github.praktimarc.mikrotik.facade.system.AsyncSystemApi;
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
    private final AsyncQueueApi queue;
    private final AsyncSnmpApi snmp;
    private final AsyncSystemApi system;
    private final AsyncFilesApi files;

    /**
     * Creates the asynchronous facade tree for session wiring.
     *
     * @param raw asynchronous raw API
     * @param dhcpServer asynchronous DHCP server API
     * @param firewall asynchronous firewall API
     * @param wifi asynchronous WiFi/CAPsMAN API
     * @param interfaces asynchronous interface/address API
     * @param queue asynchronous queue API
     * @param snmp asynchronous SNMP API
     * @param system asynchronous system/diagnostic API
     * @param files asynchronous files API
     */
    public AsyncMikrotikRtrApi(
            AsyncRawApi raw,
            AsyncDhcpServerApi dhcpServer,
            AsyncFirewallApi firewall,
            AsyncWifiApi wifi,
            AsyncInterfacesApi interfaces,
            AsyncQueueApi queue,
            AsyncSnmpApi snmp,
            AsyncSystemApi system,
            AsyncFilesApi files) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.dhcpServer = Objects.requireNonNull(dhcpServer, "dhcpServer");
        this.firewall = Objects.requireNonNull(firewall, "firewall");
        this.wifi = Objects.requireNonNull(wifi, "wifi");
        this.interfaces = Objects.requireNonNull(interfaces, "interfaces");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.snmp = Objects.requireNonNull(snmp, "snmp");
        this.system = Objects.requireNonNull(system, "system");
        this.files = Objects.requireNonNull(files, "files");
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

    /** Returns the asynchronous typed queue API. */
    public AsyncQueueApi queue() {
        return queue;
    }

    /** Returns the asynchronous typed SNMP API. */
    public AsyncSnmpApi snmp() {
        return snmp;
    }

    /** Returns the asynchronous typed system/diagnostic API. */
    public AsyncSystemApi system() {
        return system;
    }

    /** Returns the asynchronous typed files API. */
    public AsyncFilesApi files() {
        return files;
    }
}
