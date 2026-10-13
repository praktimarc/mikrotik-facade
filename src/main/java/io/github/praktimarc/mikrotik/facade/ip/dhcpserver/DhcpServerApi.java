package io.github.praktimarc.mikrotik.facade.ip.dhcpserver;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.dhcpserver.lease.DhcpLeaseApi;
import java.util.Objects;

/** RouterOS /ip/dhcp-server subtree. */
public final class DhcpServerApi {
    private final DhcpLeaseApi lease;

    public DhcpServerApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this.lease = new DhcpLeaseApi(Objects.requireNonNull(engine), Objects.requireNonNull(lifecycle));
    }

    public DhcpLeaseApi lease() { return lease; }
}
