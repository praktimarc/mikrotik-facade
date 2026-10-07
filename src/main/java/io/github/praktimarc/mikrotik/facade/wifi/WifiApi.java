package io.github.praktimarc.mikrotik.facade.wifi;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
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

/** Synchronous compatibility-aware WiFi/CAPsMAN facade. */
public final class WifiApi {
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final RegisteredClientsSourceResolver resolver;
    private final WifiRegistrationMapper mapper;
    private final RemoteCapsSourceResolver remoteCapsResolver;
    private final WifiRemoteCapMapper remoteCapMapper;

    /**
     * Creates the WiFi facade for session wiring.
     *
     * @param engine shared session command engine
     * @param lifecycle shared session lifecycle
     * @param environment immutable RouterOS bootstrap environment
     * @param capabilities session-scoped capability registry
     */
    public WifiApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            RouterOsEnvironment environment,
            CapabilityRegistry capabilities) {
        this(engine, lifecycle,
                new RegisteredClientsSourceResolver(environment, capabilities),
                new WifiRegistrationMapper(),
                new RemoteCapsSourceResolver(environment, capabilities),
                new WifiRemoteCapMapper());
    }

    WifiApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            RegisteredClientsSourceResolver resolver,
            WifiRegistrationMapper mapper) {
        this(engine, lifecycle, resolver, mapper,
                new RemoteCapsSourceResolver(resolver),
                new WifiRemoteCapMapper());
    }

    WifiApi(
            CommandEngine engine,
            SessionLifecycle lifecycle,
            RegisteredClientsSourceResolver resolver,
            WifiRegistrationMapper mapper,
            RemoteCapsSourceResolver remoteCapsResolver,
            WifiRemoteCapMapper remoteCapMapper) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.remoteCapsResolver = Objects.requireNonNull(remoteCapsResolver, "remoteCapsResolver");
        this.remoteCapMapper = Objects.requireNonNull(remoteCapMapper, "remoteCapMapper");
    }

    /**
     * Reads registrations from the currently relevant legacy CAPsMAN and/or modern WiFi source.
     *
     * <p>An enabled legacy and modern manager produces a composite result retaining source
     * provenance. A successfully empty registration table is a valid empty result and never
     * triggers source fallback. No generic MAC-address or RouterOS-id deduplication is performed.</p>
     *
     * @return immutable normalized registration list
     * @throws MikrotikFacadeException when source resolution, execution, or typed mapping fails
     */
    public List<WifiRegistration> registrationTable() throws MikrotikFacadeException {
        lifecycle.ensureOpen();

        Map<String, Boolean> sourceRelevant = new LinkedHashMap<>();
        Map<String, List<io.github.praktimarc.mikrotik.facade.RouterOsRecord>> results = new LinkedHashMap<>();
        for (RegisteredClientsSourceResolver.SourceCandidate candidate : resolver.candidates()) {
            if (resolver.capabilityState(candidate) == CapabilityState.UNSUPPORTED) {
                continue;
            }

            final boolean managerEnabled;
            try {
                managerEnabled = engine.executeSync(resolver.managerEnabledOperation(candidate));
            } catch (MikrotikCommandException failure) {
                if (resolver.capabilityState(candidate) != CapabilityState.SUPPORTED
                        && resolver.isMissingManagerCommand(failure)) {
                    resolver.recordUnsupported(candidate);
                    continue;
                }
                throw failure;
            }

            if (resolver.capabilityState(candidate) == CapabilityState.UNKNOWN) {
                resolver.recordSupported(candidate);
            }
            sourceRelevant.put(candidate.source(), managerEnabled);
        }

        if (!Boolean.TRUE.equals(sourceRelevant.get(RegisteredClientsSourceResolver.MODERN_SOURCE))
                && resolver.modernLocalStackHint()
                && resolver.modernRegistrationCapabilityState() != CapabilityState.UNSUPPORTED) {
            try {
                List<io.github.praktimarc.mikrotik.facade.RouterOsRecord> localModern =
                        engine.executeSync(resolver.registrationOperation(RegisteredClientsSourceResolver.MODERN_SOURCE));
                if (resolver.modernRegistrationCapabilityState() == CapabilityState.UNKNOWN) {
                    resolver.recordModernRegistrationSupported();
                }
                sourceRelevant.put(RegisteredClientsSourceResolver.MODERN_SOURCE, true);
                results.put(RegisteredClientsSourceResolver.MODERN_SOURCE, localModern);
            } catch (MikrotikCommandException failure) {
                if (resolver.modernRegistrationCapabilityState() != CapabilityState.SUPPORTED
                        && resolver.isMissingRegistrationCommand(failure)) {
                    resolver.recordModernRegistrationUnsupported();
                } else {
                    throw failure;
                }
            }
        }

        Optional<DataSourcePlan> plan = resolver.plan(sourceRelevant);
        if (plan.isEmpty()) {
            return List.of();
        }

        for (String source : plan.orElseThrow().sources()) {
            if (results.containsKey(source)) {
                continue;
            }
            List<io.github.praktimarc.mikrotik.facade.RouterOsRecord> records =
                    engine.executeSync(resolver.registrationOperation(source));
            results.put(source, records);
            if (RegisteredClientsSourceResolver.MODERN_SOURCE.equals(source)
                    && resolver.modernRegistrationCapabilityState() == CapabilityState.UNKNOWN) {
                resolver.recordModernRegistrationSupported();
            }
        }
        return resolver.map(plan.orElseThrow(), results, mapper);
    }

    /**
     * Reads all remote CAPs from every currently enabled CAPsMAN manager source.
     *
     * <p>When legacy and modern managers are both enabled the result is composite
     * and retains exact source provenance. Empty successful sources stay empty.</p>
     */
    public List<WifiRemoteCap> remoteCaps() throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return resolveRemoteCaps(Optional.empty());
    }

    /**
     * Finds one remote CAP by exact base MAC across the currently relevant sources.
     *
     * @return empty when no relevant source contains the requested CAP
     * @throws MikrotikDataException when more than one source row matches
     */
    public Optional<WifiRemoteCap> findRemoteCapByBaseMac(String baseMac)
            throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        String requested = requireNonBlank(baseMac, "baseMac");
        List<WifiRemoteCap> matches = resolveRemoteCaps(Optional.of(requested));
        if (matches.size() > 1) {
            throw new MikrotikDataException(
                    "Expected at most one remote CAP but RouterOS returned "
                            + matches.size() + " rows across relevant sources");
        }
        return matches.stream().findFirst();
    }

    private List<WifiRemoteCap> resolveRemoteCaps(Optional<String> baseMac)
            throws MikrotikFacadeException {
        Map<String, Boolean> sourceRelevant = new LinkedHashMap<>();
        for (RemoteCapsSourceResolver.SourceCandidate candidate : remoteCapsResolver.candidates()) {
            if (remoteCapsResolver.capabilityState(candidate) == CapabilityState.UNSUPPORTED) {
                continue;
            }

            final boolean enabled;
            try {
                enabled = engine.executeSync(remoteCapsResolver.managerEnabledOperation(candidate));
            } catch (MikrotikCommandException failure) {
                if (remoteCapsResolver.capabilityState(candidate) != CapabilityState.SUPPORTED
                        && remoteCapsResolver.isMissingManagerCommand(failure)) {
                    remoteCapsResolver.recordUnsupported(candidate);
                    continue;
                }
                throw failure;
            }

            if (remoteCapsResolver.capabilityState(candidate) == CapabilityState.UNKNOWN) {
                remoteCapsResolver.recordSupported(candidate);
            }
            sourceRelevant.put(candidate.source(), enabled);
        }

        Optional<DataSourcePlan> plan = remoteCapsResolver.plan(sourceRelevant);
        if (plan.isEmpty()) {
            return List.of();
        }

        Map<String, List<io.github.praktimarc.mikrotik.facade.RouterOsRecord>> results =
                new LinkedHashMap<>();
        for (String source : plan.orElseThrow().sources()) {
            results.put(source, engine.executeSync(
                    remoteCapsResolver.remoteCapsOperation(source, baseMac)));
        }
        return remoteCapsResolver.map(plan.orElseThrow(), results, remoteCapMapper);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

}
