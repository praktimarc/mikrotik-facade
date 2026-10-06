package io.github.praktimarc.mikrotik.facade.async;

import io.github.praktimarc.mikrotik.facade.dhcp.AsyncDhcpServerApi;
import io.github.praktimarc.mikrotik.facade.raw.AsyncRawApi;

import java.util.Objects;

/**
 * Mirrored asynchronous facade tree for one RouterOS session.
 */
public final class AsyncMikrotikRtrApi {

    private final AsyncRawApi raw;
    private final AsyncDhcpServerApi dhcpServer;

    /**
     * Creates the asynchronous facade tree for session wiring.
     *
     * @param raw asynchronous raw API
     * @param dhcpServer asynchronous DHCP server API
     */
    public AsyncMikrotikRtrApi(AsyncRawApi raw, AsyncDhcpServerApi dhcpServer) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.dhcpServer = Objects.requireNonNull(dhcpServer, "dhcpServer");
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
}
