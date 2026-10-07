package io.github.praktimarc.mikrotik.facade.firewall;

import io.github.praktimarc.mikrotik.facade.firewall.addresslist.AsyncAddressListApi;
import io.github.praktimarc.mikrotik.facade.firewall.filter.AsyncFilterApi;
import io.github.praktimarc.mikrotik.facade.firewall.mangle.AsyncMangleApi;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the RouterOS firewall facade. */
public final class AsyncFirewallApi {
    private final AsyncFilterApi filter;
    private final AsyncMangleApi mangle;
    private final AsyncAddressListApi addressList;

    /**
     * Creates the asynchronous firewall facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param callbackExecutor public completion executor
     */
    public AsyncFirewallApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.filter = new AsyncFilterApi(engine, lifecycle, callbackExecutor);
        this.mangle = new AsyncMangleApi(engine, lifecycle, callbackExecutor);
        this.addressList = new AsyncAddressListApi(engine, lifecycle, callbackExecutor);
    }

    /**
     * Returns the asynchronous firewall filter API.
     *
     * @return asynchronous firewall filter API
     */
    public AsyncFilterApi filter() { return filter; }

    /**
     * Returns the asynchronous firewall Mangle API.
     *
     * @return asynchronous firewall Mangle API
     */
    public AsyncMangleApi mangle() { return mangle; }

    /**
     * Returns the asynchronous firewall address-list API.
     *
     * @return asynchronous firewall address-list API
     */
    public AsyncAddressListApi addressList() { return addressList; }
}
