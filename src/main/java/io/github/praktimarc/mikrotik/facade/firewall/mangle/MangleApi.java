package io.github.praktimarc.mikrotik.facade.firewall.mangle;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.firewall.FirewallRule;
import io.github.praktimarc.mikrotik.facade.firewall.internal.FirewallOperations;
import io.github.praktimarc.mikrotik.facade.firewall.internal.FirewallRuleMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Synchronous typed RouterOS firewall Mangle facade. */
public final class MangleApi {
    static final String PRINT_PATH = "/ip/firewall/mangle/print";
    static final String ADD_PATH = "/ip/firewall/mangle/add";
    static final String SET_PATH = "/ip/firewall/mangle/set";
    static final String REMOVE_PATH = "/ip/firewall/mangle/remove";

    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final FirewallRuleMapper mapper;

    public MangleApi(CommandEngine engine, SessionLifecycle lifecycle) {
        this(engine, lifecycle, new FirewallRuleMapper());
    }

    MangleApi(CommandEngine engine, SessionLifecycle lifecycle, FirewallRuleMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public List<FirewallRule> find(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(findOperation(queries, mapper));
    }

    public List<FirewallRule> findBySourceAddress(String srcAddress) throws MikrotikFacadeException {
        return find(FirewallOperations.query("src-address", Objects.requireNonNull(srcAddress, "srcAddress")));
    }

    public List<FirewallRule> findByDestinationAddress(String dstAddress) throws MikrotikFacadeException {
        return find(FirewallOperations.query("dst-address", Objects.requireNonNull(dstAddress, "dstAddress")));
    }

    public List<FirewallRule> findByComment(String comment) throws MikrotikFacadeException {
        return find(FirewallOperations.query("comment", Objects.requireNonNull(comment, "comment")));
    }

    public Optional<String> add(RouterOsProperties properties) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(addOperation(properties));
    }

    public void setDisabled(String id, boolean disabled) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        engine.executeSync(setDisabledOperation(id, disabled));
    }

    public void remove(String id) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        engine.executeSync(removeOperation(id));
    }

    static RouterOsOperation<List<FirewallRule>> findOperation(RouterOsProperties queries, FirewallRuleMapper mapper) {
        return FirewallOperations.find("find firewall Mangle rules", PRINT_PATH, queries, mapper::map);
    }

    static RouterOsOperation<Optional<String>> addOperation(RouterOsProperties properties) {
        return FirewallOperations.add("add firewall Mangle rule", ADD_PATH, properties);
    }

    static RouterOsOperation<Void> setDisabledOperation(String id, boolean disabled) {
        return FirewallOperations.set("set firewall Mangle disabled", SET_PATH, id,
                FirewallOperations.booleanProperty("disabled", disabled));
    }

    static RouterOsOperation<Void> removeOperation(String id) {
        return FirewallOperations.remove("remove firewall Mangle rule", REMOVE_PATH, id);
    }
}
