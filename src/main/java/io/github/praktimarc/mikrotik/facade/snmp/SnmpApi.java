package io.github.praktimarc.mikrotik.facade.snmp;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.snmp.community.SnmpCommunityApi;

/** RouterOS /snmp subtree. */
public final class SnmpApi {
    private final SnmpCommunityApi community;
    public SnmpApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this.community = new SnmpCommunityApi(engine, lifecycle);
    }
    public SnmpCommunityApi community() { return community; }
}
