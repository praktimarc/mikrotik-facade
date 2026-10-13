package io.github.praktimarc.mikrotik.facade.ip;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.address.AsyncIpAddressApi;
import io.github.praktimarc.mikrotik.facade.ip.pool.AsyncIpPoolApi;
import io.github.praktimarc.mikrotik.facade.ip.dhcpserver.AsyncDhcpServerApi;
import io.github.praktimarc.mikrotik.facade.ip.firewall.AsyncFirewallApi;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Asynchronous RouterOS /ip menu. */
public final class AsyncIpApi {
    private final AsyncIpAddressApi address;
    private final AsyncIpPoolApi pool;
    private final AsyncDhcpServerApi dhcpServer;
    private final AsyncFirewallApi firewall;

    public AsyncIpApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(callbackExecutor);
        this.address = new AsyncIpAddressApi(engine, lifecycle, callbackExecutor);
        this.pool = new AsyncIpPoolApi(engine, lifecycle, callbackExecutor);
        this.dhcpServer = new AsyncDhcpServerApi(engine, lifecycle, callbackExecutor);
        this.firewall = new AsyncFirewallApi(engine, lifecycle, callbackExecutor);
    }

    public AsyncIpAddressApi address() { return address; }
    public AsyncIpPoolApi pool() { return pool; }
    public AsyncDhcpServerApi dhcpServer() { return dhcpServer; }
    public AsyncFirewallApi firewall() { return firewall; }
}
