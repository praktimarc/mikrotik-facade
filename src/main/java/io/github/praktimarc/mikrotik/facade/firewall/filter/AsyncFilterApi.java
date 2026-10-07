package io.github.praktimarc.mikrotik.facade.firewall.filter;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.firewall.FirewallRule;
import io.github.praktimarc.mikrotik.facade.firewall.internal.FirewallOperations;
import io.github.praktimarc.mikrotik.facade.firewall.internal.FirewallRuleMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Asynchronous mirror of the RouterOS firewall filter facade. */
public final class AsyncFilterApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final FirewallRuleMapper mapper;

    public AsyncFilterApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor) {
        this(engine, lifecycle, callbackExecutor, new FirewallRuleMapper());
    }

    AsyncFilterApi(CommandEngine engine, SessionLifecycle lifecycle, Executor callbackExecutor, FirewallRuleMapper mapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public CompletableFuture<List<FirewallRule>> find(RouterOsProperties queries) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }
        return engine.executeAsync(FilterApi.findOperation(queries, mapper));
    }

    public CompletableFuture<List<FirewallRule>> findByComment(String comment) {
        return find(FirewallOperations.query("comment", Objects.requireNonNull(comment, "comment")));
    }

    public CompletableFuture<Optional<String>> add(RouterOsProperties properties) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }
        return engine.executeAsync(FilterApi.addOperation(properties));
    }

    public CompletableFuture<Void> setDisabled(String id, boolean disabled) {
        try { lifecycle.ensureOpen(); } catch (MikrotikConnectionException broken) { return failedAsync(broken); }
        return engine.executeAsync(FilterApi.setDisabledOperation(id, disabled));
    }

    private <T> CompletableFuture<T> failedAsync(MikrotikConnectionException failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }
}
