package io.github.praktimarc.mikrotik.facade.snmp;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.snmp.community.AsyncSnmpCommunityApi;
import java.util.concurrent.Executor;

/** Asynchronous RouterOS /snmp subtree. */
public final class AsyncSnmpApi {
    private final AsyncSnmpCommunityApi community;
    public AsyncSnmpApi(CommandEngine engine, SessionLifecycle lifecycle, Executor executor) {
        this.community = new AsyncSnmpCommunityApi(engine, lifecycle, executor);
    }
    public AsyncSnmpCommunityApi community() { return community; }
}
