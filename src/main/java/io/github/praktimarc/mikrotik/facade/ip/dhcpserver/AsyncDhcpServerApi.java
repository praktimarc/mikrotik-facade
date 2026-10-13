package io.github.praktimarc.mikrotik.facade.ip.dhcpserver;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.ip.dhcpserver.lease.AsyncDhcpLeaseApi;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Asynchronous /ip/dhcp-server subtree. */
public final class AsyncDhcpServerApi {
    private final AsyncDhcpLeaseApi lease;

    public AsyncDhcpServerApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        this.lease = new AsyncDhcpLeaseApi(Objects.requireNonNull(engine),
                Objects.requireNonNull(lifecycle), Objects.requireNonNull(callbackExecutor));
    }

    public AsyncDhcpLeaseApi lease() { return lease; }
}
