package io.github.praktimarc.mikrotik.facade.ip;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.address.IpAddressApi;
import io.github.praktimarc.mikrotik.facade.ip.pool.IpPoolApi;
import io.github.praktimarc.mikrotik.facade.ip.dhcpserver.DhcpServerApi;
import io.github.praktimarc.mikrotik.facade.ip.firewall.FirewallApi;
import java.util.Objects;

/** RouterOS /ip menu. */
public final class IpApi {
    private final IpAddressApi address;
    private final IpPoolApi pool;
    private final DhcpServerApi dhcpServer;
    private final FirewallApi firewall;

    public IpApi(CommandEngine engine, SessionLifecycle lifecycle) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(lifecycle);
        this.address = new IpAddressApi(engine, lifecycle);
        this.pool = new IpPoolApi(engine, lifecycle);
        this.dhcpServer = new DhcpServerApi(engine, lifecycle);
        this.firewall = new FirewallApi(engine, lifecycle);
    }

    public IpAddressApi address() { return address; }
    public IpPoolApi pool() { return pool; }
    public DhcpServerApi dhcpServer() { return dhcpServer; }
    public FirewallApi firewall() { return firewall; }
}
