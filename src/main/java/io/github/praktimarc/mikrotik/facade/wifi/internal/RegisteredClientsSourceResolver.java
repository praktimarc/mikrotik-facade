package io.github.praktimarc.mikrotik.facade.wifi.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsPackage;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityRegistry;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityState;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.compat.DataSourcePlan;
import io.github.praktimarc.mikrotik.facade.internal.compat.FeatureSourceResolver;
import io.github.praktimarc.mikrotik.facade.internal.compat.ResolvedRecord;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.wifi.WifiRegistration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the authoritative RouterOS registration source or composite sources for one session. */
public final class RegisteredClientsSourceResolver {
    /** Stable compatibility source identifier for legacy CAPsMAN registrations. */
    public static final String LEGACY_SOURCE = "/caps-man/registration-table";
    /** Stable compatibility source identifier for RouterOS 7.13+ WiFi registrations. */
    public static final String MODERN_SOURCE = "/interface/wifi/registration-table";

    private static final String LEGACY_MANAGER_PATH = "/caps-man/manager/print";
    private static final String MODERN_MANAGER_PATH = "/interface/wifi/capsman/print";
    private static final String LEGACY_CAPABILITY = "wifi.legacy.capsman-manager";
    private static final String MODERN_CAPABILITY = "wifi.modern.capsman-manager";
    private static final String MODERN_REGISTRATION_CAPABILITY = "wifi.modern.registration-table";
    private static final Pattern VERSION_PREFIX = Pattern.compile("^(\\d+)\\.(\\d+)");

    private final RouterOsEnvironment environment;
    private final CapabilityRegistry capabilities;
    private final FeatureSourceResolver sourceResolver;

    /**
     * Creates a session-aware resolver using immutable bootstrap knowledge and session capability cache.
     *
     * @param environment immutable bootstrap environment
     * @param capabilities session-scoped capability registry
     */
    public RegisteredClientsSourceResolver(
            RouterOsEnvironment environment,
            CapabilityRegistry capabilities) {
        this(environment, capabilities, new FeatureSourceResolver());
    }

    RegisteredClientsSourceResolver(
            RouterOsEnvironment environment,
            CapabilityRegistry capabilities,
            FeatureSourceResolver sourceResolver) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.sourceResolver = Objects.requireNonNull(sourceResolver, "sourceResolver");
    }

    /**
     * Returns the ordered source candidates that can plausibly exist for this session.
     *
     * @return immutable candidates in deterministic legacy/modern order
     */
    public List<SourceCandidate> candidates() {
        Version version = parseVersion(environment.systemInfo().version());
        Set<String> packages = packageNames(environment);

        boolean legacy = legacyPotential(version, packages);
        boolean modern = modernPotential(version, packages);

        List<SourceCandidate> result = new ArrayList<>(2);
        if (legacy) {
            result.add(new SourceCandidate(
                    LEGACY_SOURCE,
                    LEGACY_MANAGER_PATH,
                    LEGACY_CAPABILITY));
        }
        if (modern) {
            result.add(new SourceCandidate(
                    MODERN_SOURCE,
                    MODERN_MANAGER_PATH,
                    MODERN_CAPABILITY));
        }
        return List.copyOf(result);
    }

    /**
     * Returns cached manager-path capability knowledge for one candidate.
     *
     * @param candidate source candidate
     * @return cached capability state
     */
    public CapabilityState capabilityState(SourceCandidate candidate) {
        return capabilities.state(requireCandidate(candidate).capability());
    }

    /**
     * Records that a candidate manager path was successfully queried.
     *
     * @param candidate source candidate
     */
    public void recordSupported(SourceCandidate candidate) {
        capabilities.recordDefinitive(
                requireCandidate(candidate).capability(),
                CapabilityState.SUPPORTED);
    }

    /**
     * Records that a candidate manager path is definitively unavailable.
     *
     * @param candidate source candidate
     */
    public void recordUnsupported(SourceCandidate candidate) {
        capabilities.recordDefinitive(
                requireCandidate(candidate).capability(),
                CapabilityState.UNSUPPORTED);
    }

    /**
     * Reports whether bootstrap package knowledge proves that this router has a local modern WiFi driver stack.
     *
     * <p>This is only a source-relevance hint. The registration-table command must still succeed before
     * the local modern source is selected.</p>
     *
     * @return true for known RouterOS 7.13+ local modern WiFi driver packages
     */
    public boolean modernLocalStackHint() {
        Version version = parseVersion(environment.systemInfo().version());
        if (!version.known() || version.major() < 7 || version.major() == 7 && version.minor() < 13) {
            return false;
        }
        Set<String> packages = packageNames(environment);
        return packages.contains("wifi-qcom")
                || packages.contains("wifi-qcom-ac")
                || packages.contains("wifiwave2");
    }

    /**
     * Returns cached availability knowledge for the modern registration-table command.
     *
     * @return cached capability state
     */
    public CapabilityState modernRegistrationCapabilityState() {
        return capabilities.state(MODERN_REGISTRATION_CAPABILITY);
    }

    /** Records successful availability of the modern registration-table command. */
    public void recordModernRegistrationSupported() {
        capabilities.recordDefinitive(MODERN_REGISTRATION_CAPABILITY, CapabilityState.SUPPORTED);
    }

    /** Records definitive absence of the modern registration-table command. */
    public void recordModernRegistrationUnsupported() {
        capabilities.recordDefinitive(MODERN_REGISTRATION_CAPABILITY, CapabilityState.UNSUPPORTED);
    }

    /**
     * Creates the read-only manager-state operation used to decide source relevance.
     *
     * @param candidate source candidate
     * @return operation returning the current manager enabled state
     */
    public RouterOsOperation<Boolean> managerEnabledOperation(SourceCandidate candidate) {
        SourceCandidate checked = requireCandidate(candidate);
        RouterOsCommand command = RouterOsCommand.builder(checked.managerPath()).build();
        return new RouterOsOperation<>() {
            @Override
            public String name() {
                return "read " + checked.capability();
            }

            @Override
            public RouterOsCommand command() {
                return command;
            }

            @Override
            public Boolean map(CommandResult result) throws MikrotikFacadeException {
                int size = result.records().size();
                if (size != 1) {
                    throw new MikrotikDataException(
                            "Expected exactly one CAPsMAN manager row but RouterOS returned " + size);
                }
                return result.records().get(0).requireBoolean("enabled");
            }
        };
    }

    /**
     * Creates a raw registration-table operation for one resolved source.
     *
     * @param source exact compatibility source identifier
     * @return read-only registration-table operation
     */
    public RouterOsOperation<List<RouterOsRecord>> registrationOperation(String source) {
        String checked = requireSource(source);
        RouterOsCommand command = RouterOsCommand.builder(checked + "/print").build();
        return new RouterOsOperation<>() {
            @Override
            public String name() {
                return "read WiFi registrations from " + checked;
            }

            @Override
            public RouterOsCommand command() {
                return command;
            }

            @Override
            public List<RouterOsRecord> map(CommandResult result) {
                return result.records();
            }
        };
    }

    /**
     * Resolves zero, one, or both currently relevant registration sources.
     *
     * <p>Legacy CAPsMAN relevance is manager-driven. The modern source may additionally be relevant
     * for a locally installed WiFi driver stack after its registration-table path has been verified.</p>
     *
     * @param sourceRelevant source identifier to relevance mapping
     * @return empty when neither compatible registration source is relevant
     */
    public Optional<DataSourcePlan> plan(Map<String, Boolean> sourceRelevant) {
        Objects.requireNonNull(sourceRelevant, "sourceRelevant");
        List<String> selected = new ArrayList<>(2);
        if (Boolean.TRUE.equals(sourceRelevant.get(LEGACY_SOURCE))) {
            selected.add(LEGACY_SOURCE);
        }
        if (Boolean.TRUE.equals(sourceRelevant.get(MODERN_SOURCE))) {
            selected.add(MODERN_SOURCE);
        }
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        if (selected.size() == 1) {
            return Optional.of(DataSourcePlan.single("wifi.registration", selected.get(0)));
        }
        return Optional.of(DataSourcePlan.composite("wifi.registration", selected));
    }

    /**
     * Applies the generic compatibility merge and source-aware schema mapper.
     *
     * @param plan already resolved data-source plan
     * @param successfulResults successful source result sets
     * @param mapper normalized WiFi mapper
     * @return immutable source-preserving registration list
     * @throws MikrotikDataException when a present typed field is malformed
     */
    public List<WifiRegistration> map(
            DataSourcePlan plan,
            Map<String, ? extends List<RouterOsRecord>> successfulResults,
            WifiRegistrationMapper mapper) throws MikrotikDataException {
        Objects.requireNonNull(mapper, "mapper");
        List<ResolvedRecord> resolved = sourceResolver.resolve(
                Objects.requireNonNull(plan, "plan"),
                Objects.requireNonNull(successfulResults, "successfulResults"));
        List<WifiRegistration> registrations = new ArrayList<>(resolved.size());
        for (ResolvedRecord record : resolved) {
            registrations.add(mapper.map(record.record(), record.source()));
        }
        return List.copyOf(registrations);
    }

    /**
     * Classifies a manager probe as a missing RouterOS command/menu.
     *
     * @param failure mapped RouterOS command failure
     * @return true only for command category zero
     */
    public boolean isMissingManagerCommand(MikrotikCommandException failure) {
        Objects.requireNonNull(failure, "failure");
        return failure.category().isPresent() && failure.category().getAsInt() == 0;
    }

    /**
     * Classifies a registration-table probe as a missing RouterOS command/menu.
     *
     * @param failure mapped RouterOS command failure
     * @return true only for command category zero
     */
    public boolean isMissingRegistrationCommand(MikrotikCommandException failure) {
        return isMissingManagerCommand(failure);
    }

    /**
     * Exact immutable description of one source and its manager capability probe.
     *
     * @param source registration source identifier
     * @param managerPath read-only global manager print path
     * @param capability stable capability identifier
     */
    public record SourceCandidate(String source, String managerPath, String capability) {
        /** Validates and normalizes one immutable source candidate. */
        public SourceCandidate {
            source = requireSource(source);
            Objects.requireNonNull(managerPath, "managerPath");
            Objects.requireNonNull(capability, "capability");
            if (!managerPath.endsWith("/print")) {
                throw new IllegalArgumentException("managerPath must be a read-only /print path");
            }
            if (capability.isBlank()) {
                throw new IllegalArgumentException("capability must not be blank");
            }
        }
    }

    private static SourceCandidate requireCandidate(SourceCandidate candidate) {
        return Objects.requireNonNull(candidate, "candidate");
    }

    private static String requireSource(String source) {
        Objects.requireNonNull(source, "source");
        if (!LEGACY_SOURCE.equals(source) && !MODERN_SOURCE.equals(source)) {
            throw new IllegalArgumentException("Unknown WiFi registration source");
        }
        return source;
    }

    private static Set<String> packageNames(RouterOsEnvironment environment) {
        if (environment.packages().isEmpty()) {
            return Set.of();
        }
        return environment.packages().orElseThrow().stream()
                .map(RouterOsPackage::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean legacyPotential(Version version, Set<String> packages) {
        if (version.known()) {
            if (version.major() < 7 || version.major() == 7 && version.minor() < 13) {
                return true;
            }
            if (environment.packageInformationAvailable()) {
                return packages.contains("wireless");
            }
            return true;
        }
        return !environment.packageInformationAvailable() || packages.contains("wireless");
    }

    private static boolean modernPotential(Version version, Set<String> packages) {
        if (version.known()) {
            if (version.major() > 7) {
                return true;
            }
            if (version.major() < 7) {
                return false;
            }
            return version.minor() >= 13;
        }
        return packages.contains("wifi-qcom")
                || packages.contains("wifi-qcom-ac")
                || packages.contains("wifiwave2");
    }

    private static Version parseVersion(String value) {
        Matcher matcher = VERSION_PREFIX.matcher(Objects.requireNonNull(value, "value"));
        if (!matcher.find()) {
            return Version.UNKNOWN;
        }
        try {
            return new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException ignored) {
            return Version.UNKNOWN;
        }
    }

    private record Version(int major, int minor) {
        private static final Version UNKNOWN = new Version(-1, -1);
        private boolean known() { return major >= 0 && minor >= 0; }
    }
}
