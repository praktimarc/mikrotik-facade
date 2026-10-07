package io.github.praktimarc.mikrotik.facade.firewall;

import io.github.praktimarc.mikrotik.facade.firewall.addresslist.AddressListApi;
import io.github.praktimarc.mikrotik.facade.firewall.filter.FilterApi;
import io.github.praktimarc.mikrotik.facade.firewall.mangle.MangleApi;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.Objects;

/** Synchronous RouterOS firewall facade with typed submodules. */
public final class FirewallApi {

    private final FilterApi filter;
    private final MangleApi mangle;
    private final AddressListApi addressList;

    /**
     * Creates the firewall facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     */
    public FirewallApi(CommandEngine engine, SessionLifecycle lifecycle) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(lifecycle, "lifecycle");
        this.filter = new FilterApi(engine, lifecycle);
        this.mangle = new MangleApi(engine, lifecycle);
        this.addressList = new AddressListApi(engine, lifecycle);
    }

    /**
     * Returns the typed firewall filter API.
     *
     * @return typed firewall filter API
     */
    public FilterApi filter() { return filter; }

    /**
     * Returns the typed firewall Mangle API.
     *
     * @return typed firewall Mangle API
     */
    public MangleApi mangle() { return mangle; }

    /**
     * Returns the typed firewall address-list API.
     *
     * @return typed firewall address-list API
     */
    public AddressListApi addressList() { return addressList; }
}
