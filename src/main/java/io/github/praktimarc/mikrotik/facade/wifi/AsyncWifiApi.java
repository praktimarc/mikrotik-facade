package io.github.praktimarc.mikrotik.facade.wifi;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityRegistry;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityState;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.compat.DataSourcePlan;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.wifi.internal.RegisteredClientsSourceResolver;
import io.github.praktimarc.mikrotik.facade.wifi.internal.RemoteCapsSourceResolver;
import io.github.praktimarc.mikrotik.facade.wifi.internal.WifiRegistrationMapper;
import io.github.praktimarc.mikrotik.facade.wifi.internal.WifiRemoteCapMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/** Asynchronous mirror of the compatibility-aware WiFi/CAPsMAN facade. */
public final class AsyncWifiApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor callbackExecutor;
    private final RegisteredClientsSourceResolver resolver;
    private final WifiRegistrationMapper mapper;
    private final RemoteCapsSourceResolver remoteCapsResolver;
    private final WifiRemoteCapMapper remoteCapMapper;

    /**
     * Creates the asynchronous WiFi facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param callbackExecutor public completion executor
     * @param environment immutable RouterOS bootstrap environment
     * @param capabilities session-scoped capability registry
     */
    public AsyncWifiApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            RouterOsEnvironment environment,
            CapabilityRegistry capabilities) {
        this(engine, lifecycle, callbackExecutor,
                new RegisteredClientsSourceResolver(environment, capabilities),
                new WifiRegistrationMapper(),
                new RemoteCapsSourceResolver(environment, capabilities),
                new WifiRemoteCapMapper());
    }

    AsyncWifiApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            RegisteredClientsSourceResolver resolver,
            WifiRegistrationMapper mapper) {
        this(engine, lifecycle, callbackExecutor, resolver, mapper,
                new RemoteCapsSourceResolver(resolver),
                new WifiRemoteCapMapper());
    }

    AsyncWifiApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor callbackExecutor,
            RegisteredClientsSourceResolver resolver,
            WifiRegistrationMapper mapper,
            RemoteCapsSourceResolver remoteCapsResolver,
            WifiRemoteCapMapper remoteCapMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.remoteCapsResolver = Objects.requireNonNull(remoteCapsResolver, "remoteCapsResolver");
        this.remoteCapMapper = Objects.requireNonNull(remoteCapMapper, "remoteCapMapper");
    }

    /**
     * Resolves relevant WiFi/CAPsMAN sources and reads their registration tables without blocking.
     *
     * @return cancellable future containing the immutable normalized registration list
     */
    public CompletableFuture<List<WifiRegistration>> registrationTable() {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }

        CompositeFuture<List<WifiRegistration>> result = new CompositeFuture<>();
        Map<String, Boolean> sourceRelevant = new LinkedHashMap<>();
        Map<String, List<RouterOsRecord>> sourceResults = new LinkedHashMap<>();

        CompletableFuture<Map<String, Boolean>> managerChain =
                CompletableFuture.completedFuture(sourceRelevant);
        for (RegisteredClientsSourceResolver.SourceCandidate candidate : resolver.candidates()) {
            managerChain = managerChain.thenCompose(states -> {
                if (result.isCancelled()) {
                    return CompletableFuture.failedFuture(new CancellationException());
                }
                return probeManager(candidate, result).thenApply(managerState -> {
                    managerState.ifPresent(value -> states.put(candidate.source(), value));
                    return states;
                });
            });
        }

        CompletableFuture<Map<String, Boolean>> relevanceChain = managerChain.thenCompose(states ->
                ensureModernLocalSource(states, sourceResults, result));

        CompletableFuture<List<WifiRegistration>> chain = relevanceChain.thenCompose(states -> {
            Optional<DataSourcePlan> plan = resolver.plan(states);
            if (plan.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            return querySources(plan.orElseThrow(), 0, sourceResults, result)
                    .thenApply(ignored -> {
                        try {
                            return resolver.map(plan.orElseThrow(), sourceResults, mapper);
                        } catch (Exception failure) {
                            throw new CompletionException(failure);
                        }
                    });
        });

        chain.whenComplete((value, failure) -> callbackExecutor.execute(() -> {
            if (result.isCancelled()) {
                return;
            }
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(unwrap(failure));
            }
        }));
        return result;
    }

    private CompletableFuture<Optional<Boolean>> probeManager(
            RegisteredClientsSourceResolver.SourceCandidate candidate,
            CompositeFuture<?> result) {
        if (resolver.capabilityState(candidate) == CapabilityState.UNSUPPORTED) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Boolean> raw = engine.executeAsync(
                resolver.managerEnabledOperation(candidate));
        result.track(raw);
        CompletableFuture<Optional<Boolean>> mapped = new CompletableFuture<>();
        raw.whenComplete((enabled, failure) -> {
            result.clear(raw);
            if (failure == null) {
                if (resolver.capabilityState(candidate) == CapabilityState.UNKNOWN) {
                    resolver.recordSupported(candidate);
                }
                mapped.complete(Optional.of(enabled));
                return;
            }

            Throwable cause = unwrap(failure);
            if (cause instanceof MikrotikCommandException commandFailure
                    && resolver.capabilityState(candidate) != CapabilityState.SUPPORTED
                    && resolver.isMissingManagerCommand(commandFailure)) {
                resolver.recordUnsupported(candidate);
                mapped.complete(Optional.empty());
                return;
            }
            mapped.completeExceptionally(cause);
        });
        return mapped;
    }

    private CompletableFuture<Map<String, Boolean>> ensureModernLocalSource(
            Map<String, Boolean> sourceRelevant,
            Map<String, List<RouterOsRecord>> sourceResults,
            CompositeFuture<?> result) {
        if (Boolean.TRUE.equals(sourceRelevant.get(RegisteredClientsSourceResolver.MODERN_SOURCE))
                || !resolver.modernLocalStackHint()
                || resolver.modernRegistrationCapabilityState() == CapabilityState.UNSUPPORTED) {
            return CompletableFuture.completedFuture(sourceRelevant);
        }
        if (result.isCancelled()) {
            return CompletableFuture.failedFuture(new CancellationException());
        }

        CompletableFuture<List<RouterOsRecord>> raw = engine.executeAsync(
                resolver.registrationOperation(RegisteredClientsSourceResolver.MODERN_SOURCE));
        result.track(raw);
        CompletableFuture<Map<String, Boolean>> stage = new CompletableFuture<>();
        raw.whenComplete((records, failure) -> {
            result.clear(raw);
            if (failure == null) {
                if (resolver.modernRegistrationCapabilityState() == CapabilityState.UNKNOWN) {
                    resolver.recordModernRegistrationSupported();
                }
                sourceRelevant.put(RegisteredClientsSourceResolver.MODERN_SOURCE, true);
                sourceResults.put(RegisteredClientsSourceResolver.MODERN_SOURCE, records);
                stage.complete(sourceRelevant);
                return;
            }

            Throwable cause = unwrap(failure);
            if (cause instanceof MikrotikCommandException commandFailure
                    && resolver.modernRegistrationCapabilityState() != CapabilityState.SUPPORTED
                    && resolver.isMissingRegistrationCommand(commandFailure)) {
                resolver.recordModernRegistrationUnsupported();
                stage.complete(sourceRelevant);
                return;
            }
            stage.completeExceptionally(cause);
        });
        return stage;
    }

    private CompletableFuture<Void> querySources(
            DataSourcePlan plan,
            int index,
            Map<String, List<RouterOsRecord>> sourceResults,
            CompositeFuture<?> result) {
        if (result.isCancelled()) {
            return CompletableFuture.failedFuture(new CancellationException());
        }
        if (index >= plan.sources().size()) {
            return CompletableFuture.completedFuture(null);
        }

        String source = plan.sources().get(index);
        if (sourceResults.containsKey(source)) {
            return querySources(plan, index + 1, sourceResults, result);
        }
        CompletableFuture<List<RouterOsRecord>> raw = engine.executeAsync(
                resolver.registrationOperation(source));
        result.track(raw);
        CompletableFuture<Void> stage = new CompletableFuture<>();
        raw.whenComplete((records, failure) -> {
            result.clear(raw);
            if (failure != null) {
                stage.completeExceptionally(unwrap(failure));
                return;
            }
            sourceResults.put(source, records);
            if (RegisteredClientsSourceResolver.MODERN_SOURCE.equals(source)
                    && resolver.modernRegistrationCapabilityState() == CapabilityState.UNKNOWN) {
                resolver.recordModernRegistrationSupported();
            }
            querySources(plan, index + 1, sourceResults, result)
                    .whenComplete((ignored, nextFailure) -> {
                        if (nextFailure == null) {
                            stage.complete(null);
                        } else {
                            stage.completeExceptionally(unwrap(nextFailure));
                        }
                    });
        });
        return stage;
    }

    /** Reads all remote CAPs from currently enabled manager sources. */
    public CompletableFuture<List<WifiRemoteCap>> remoteCaps() {
        return resolveRemoteCaps(Optional.empty());
    }

    /** Finds one remote CAP by exact base MAC across relevant manager sources. */
    public CompletableFuture<Optional<WifiRemoteCap>> findRemoteCapByBaseMac(String baseMac) {
        String requested = requireNonBlank(baseMac, "baseMac");
        CompletableFuture<List<WifiRemoteCap>> source = resolveRemoteCaps(Optional.of(requested));
        CompositeFuture<Optional<WifiRemoteCap>> result = new CompositeFuture<>();
        result.track(source);
        source.whenComplete((caps, failure) -> callbackExecutor.execute(() -> {
            result.clear(source);
            if (result.isCancelled()) {
                return;
            }
            if (failure != null) {
                result.completeExceptionally(unwrap(failure));
                return;
            }
            if (caps.size() > 1) {
                result.completeExceptionally(new MikrotikDataException(
                        "Expected at most one remote CAP but RouterOS returned "
                                + caps.size() + " rows across relevant sources"));
                return;
            }
            result.complete(caps.stream().findFirst());
        }));
        return result;
    }

    private CompletableFuture<List<WifiRemoteCap>> resolveRemoteCaps(Optional<String> baseMac) {
        try {
            lifecycle.ensureOpen();
        } catch (MikrotikConnectionException broken) {
            return failedAsync(broken);
        }

        CompositeFuture<List<WifiRemoteCap>> result = new CompositeFuture<>();
        Map<String, Boolean> sourceRelevant = new LinkedHashMap<>();
        Map<String, List<RouterOsRecord>> sourceResults = new LinkedHashMap<>();

        CompletableFuture<Map<String, Boolean>> managerChain =
                CompletableFuture.completedFuture(sourceRelevant);
        for (RemoteCapsSourceResolver.SourceCandidate candidate : remoteCapsResolver.candidates()) {
            managerChain = managerChain.thenCompose(states -> {
                if (result.isCancelled()) {
                    return CompletableFuture.failedFuture(new CancellationException());
                }
                return probeRemoteManager(candidate, result).thenApply(managerState -> {
                    managerState.ifPresent(value -> states.put(candidate.source(), value));
                    return states;
                });
            });
        }

        CompletableFuture<List<WifiRemoteCap>> chain = managerChain.thenCompose(states -> {
            Optional<DataSourcePlan> plan = remoteCapsResolver.plan(states);
            if (plan.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            return queryRemoteSources(
                    plan.orElseThrow(), 0, baseMac, sourceResults, result)
                    .thenApply(ignored -> {
                        try {
                            return remoteCapsResolver.map(
                                    plan.orElseThrow(), sourceResults, remoteCapMapper);
                        } catch (Exception failure) {
                            throw new CompletionException(failure);
                        }
                    });
        });

        chain.whenComplete((value, failure) -> callbackExecutor.execute(() -> {
            if (result.isCancelled()) {
                return;
            }
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(unwrap(failure));
            }
        }));
        return result;
    }

    private CompletableFuture<Optional<Boolean>> probeRemoteManager(
            RemoteCapsSourceResolver.SourceCandidate candidate,
            CompositeFuture<?> result) {
        if (remoteCapsResolver.capabilityState(candidate) == CapabilityState.UNSUPPORTED) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Boolean> raw = engine.executeAsync(
                remoteCapsResolver.managerEnabledOperation(candidate));
        result.track(raw);
        CompletableFuture<Optional<Boolean>> mapped = new CompletableFuture<>();
        raw.whenComplete((enabled, failure) -> {
            result.clear(raw);
            if (failure == null) {
                if (remoteCapsResolver.capabilityState(candidate) == CapabilityState.UNKNOWN) {
                    remoteCapsResolver.recordSupported(candidate);
                }
                mapped.complete(Optional.of(enabled));
                return;
            }

            Throwable cause = unwrap(failure);
            if (cause instanceof MikrotikCommandException commandFailure
                    && remoteCapsResolver.capabilityState(candidate) != CapabilityState.SUPPORTED
                    && remoteCapsResolver.isMissingManagerCommand(commandFailure)) {
                remoteCapsResolver.recordUnsupported(candidate);
                mapped.complete(Optional.empty());
                return;
            }
            mapped.completeExceptionally(cause);
        });
        return mapped;
    }

    private CompletableFuture<Void> queryRemoteSources(
            DataSourcePlan plan,
            int index,
            Optional<String> baseMac,
            Map<String, List<RouterOsRecord>> sourceResults,
            CompositeFuture<?> result) {
        if (result.isCancelled()) {
            return CompletableFuture.failedFuture(new CancellationException());
        }
        if (index >= plan.sources().size()) {
            return CompletableFuture.completedFuture(null);
        }

        String source = plan.sources().get(index);
        CompletableFuture<List<RouterOsRecord>> raw = engine.executeAsync(
                remoteCapsResolver.remoteCapsOperation(source, baseMac));
        result.track(raw);
        CompletableFuture<Void> stage = new CompletableFuture<>();
        raw.whenComplete((records, failure) -> {
            result.clear(raw);
            if (failure != null) {
                stage.completeExceptionally(unwrap(failure));
                return;
            }
            sourceResults.put(source, records);
            queryRemoteSources(plan, index + 1, baseMac, sourceResults, result)
                    .whenComplete((ignored, nextFailure) -> {
                        if (nextFailure == null) {
                            stage.complete(null);
                        } else {
                            stage.completeExceptionally(unwrap(nextFailure));
                        }
                    });
        });
        return stage;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private <T> CompletableFuture<T> failedAsync(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callbackExecutor.execute(() -> future.completeExceptionally(failure));
        return future;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class CompositeFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<CompletableFuture<?>> active = new AtomicReference<>();

        private void track(CompletableFuture<?> child) {
            active.set(Objects.requireNonNull(child, "child"));
            if (isCancelled()) {
                child.cancel(false);
            }
        }

        private void clear(CompletableFuture<?> child) {
            active.compareAndSet(child, null);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(false);
            if (cancelled) {
                CompletableFuture<?> child = active.getAndSet(null);
                if (child != null) {
                    child.cancel(false);
                }
            }
            return cancelled;
        }
    }
}
