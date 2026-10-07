package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityRegistry;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityState;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.compat.DataSourcePlan;
import io.github.praktimarc.mikrotik.facade.internal.compat.FeatureSourceResolver;
import io.github.praktimarc.mikrotik.facade.internal.compat.ResolvedRecord;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.wifi.WifiRemoteCap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves legacy and modern CAPsMAN remote-CAP sources from manager relevance. */
public final class RemoteCapsSourceResolver {
    public static final String LEGACY_SOURCE = "/caps-man/remote-cap";
    public static final String MODERN_SOURCE = "/interface/wifi/capsman/remote-cap";

    private final RegisteredClientsSourceResolver managers;
    private final FeatureSourceResolver sourceResolver;

    /** Creates a session-aware remote-CAP resolver. */
    public RemoteCapsSourceResolver(
            RouterOsEnvironment environment,
            CapabilityRegistry capabilities) {
        this(new RegisteredClientsSourceResolver(environment, capabilities));
    }

    /** Creates a resolver reusing the registration module's manager capability knowledge. */
    public RemoteCapsSourceResolver(RegisteredClientsSourceResolver managers) {
        this(managers, new FeatureSourceResolver(
                Objects.requireNonNull(managers, "managers").diagnostics()));
    }

    RemoteCapsSourceResolver(
            RegisteredClientsSourceResolver managers,
            FeatureSourceResolver sourceResolver) {
        this.managers = Objects.requireNonNull(managers, "managers");
        this.sourceResolver = Objects.requireNonNull(sourceResolver, "sourceResolver");
    }

    /** Returns deterministic manager-backed remote-CAP candidates. */
    public List<SourceCandidate> candidates() {
        return managers.candidates().stream()
                .map(manager -> new SourceCandidate(
                        remoteSource(manager.source()),
                        manager))
                .toList();
    }

    public CapabilityState capabilityState(SourceCandidate candidate) {
        return managers.capabilityState(requireCandidate(candidate).manager());
    }

    public void recordSupported(SourceCandidate candidate) {
        managers.recordSupported(requireCandidate(candidate).manager());
    }

    public void recordUnsupported(SourceCandidate candidate) {
        managers.recordUnsupported(requireCandidate(candidate).manager());
    }

    public RouterOsOperation<Boolean> managerEnabledOperation(SourceCandidate candidate) {
        return managers.managerEnabledOperation(requireCandidate(candidate).manager());
    }

    public boolean isMissingManagerCommand(MikrotikCommandException failure) {
        return managers.isMissingManagerCommand(failure);
    }

    /** Creates one remote-CAP read, optionally restricted by exact base MAC. */
    public RouterOsOperation<List<RouterOsRecord>> remoteCapsOperation(
            String source,
            Optional<String> baseMac) {
        String checked = requireSource(source);
        Objects.requireNonNull(baseMac, "baseMac");
        RouterOsCommand.Builder builder = RouterOsCommand.builder(checked + "/print");
        baseMac.ifPresent(value -> builder.query("base-mac", requireNonBlank(value, "baseMac")));
        RouterOsCommand command = builder.build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "read WiFi remote CAPs from " + checked; }
            @Override public RouterOsCommand command() { return command; }
            @Override public List<RouterOsRecord> map(CommandResult result) { return result.records(); }
        };
    }

    /** Resolves zero, one, or both manager-authoritative remote-CAP sources. */
    public Optional<DataSourcePlan> plan(Map<String, Boolean> sourceRelevant) {
        Objects.requireNonNull(sourceRelevant, "sourceRelevant");
        List<String> selected = new ArrayList<>(2);
        if (Boolean.TRUE.equals(sourceRelevant.get(LEGACY_SOURCE))) selected.add(LEGACY_SOURCE);
        if (Boolean.TRUE.equals(sourceRelevant.get(MODERN_SOURCE))) selected.add(MODERN_SOURCE);
        if (selected.isEmpty()) return Optional.empty();
        if (selected.size() == 1) {
            String chosen = selected.get(0);
            for (SourceCandidate candidate : candidates()) {
                if (!candidate.source().equals(chosen)
                        && capabilityState(candidate) == CapabilityState.UNSUPPORTED) {
                    managers.diagnostics().compatibilityFallback(
                            "wifi.remote-caps",
                            candidate.source(),
                            chosen);
                }
            }
            return Optional.of(DataSourcePlan.single("wifi.remote-caps", chosen));
        }
        return Optional.of(DataSourcePlan.composite("wifi.remote-caps", selected));
    }

    /** Maps selected raw source rows while preserving exact source provenance. */
    public List<WifiRemoteCap> map(
            DataSourcePlan plan,
            Map<String, ? extends List<RouterOsRecord>> successfulResults,
            WifiRemoteCapMapper mapper) throws MikrotikDataException {
        Objects.requireNonNull(mapper, "mapper");
        List<ResolvedRecord> resolved = sourceResolver.resolve(plan, successfulResults);
        ArrayList<WifiRemoteCap> result = new ArrayList<>(resolved.size());
        for (ResolvedRecord record : resolved) {
            result.add(mapper.map(record.record(), record.source()));
        }
        return List.copyOf(result);
    }

    /** Remote source plus its associated manager capability probe. */
    public record SourceCandidate(
            String source,
            RegisteredClientsSourceResolver.SourceCandidate manager) {
        public SourceCandidate {
            source = requireSource(source);
            Objects.requireNonNull(manager, "manager");
        }
    }

    private static SourceCandidate requireCandidate(SourceCandidate candidate) {
        return Objects.requireNonNull(candidate, "candidate");
    }

    private static String remoteSource(String registrationSource) {
        if (RegisteredClientsSourceResolver.LEGACY_SOURCE.equals(registrationSource)) {
            return LEGACY_SOURCE;
        }
        if (RegisteredClientsSourceResolver.MODERN_SOURCE.equals(registrationSource)) {
            return MODERN_SOURCE;
        }
        throw new IllegalArgumentException("Unknown registration source");
    }

    private static String requireSource(String source) {
        Objects.requireNonNull(source, "source");
        if (!LEGACY_SOURCE.equals(source) && !MODERN_SOURCE.equals(source)) {
            throw new IllegalArgumentException("Unknown remote-CAP source");
        }
        return source;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
