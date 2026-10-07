package io.github.praktimarc.mikrotik.facade.internal.capability;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Session-scoped registry for definitive RouterOS capability knowledge.
 *
 * <p>Only {@link CapabilityState#SUPPORTED} and {@link CapabilityState#UNSUPPORTED}
 * are cached. Technical probe failures and explicit {@link CapabilityState#UNKNOWN}
 * results leave the registry unchanged.</p>
 */
public final class CapabilityRegistry {

    private final ConcurrentMap<String, CapabilityState> definitive = new ConcurrentHashMap<>();
    private final Object probeLock = new Object();
    private final FacadeDiagnostics diagnostics;

    /** Creates an empty session-scoped capability registry without emitted diagnostics. */
    public CapabilityRegistry() {
        this(FacadeDiagnostics.noOp("standalone-session"));
    }

    /** Creates a session-scoped capability registry with explicit diagnostics. */
    public CapabilityRegistry(FacadeDiagnostics diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /** Returns the shared internal diagnostic context for source resolvers. */
    public FacadeDiagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * Returns cached capability knowledge, or {@code UNKNOWN} when none exists.
     *
     * @param capability stable capability identifier
     * @return cached state or {@code UNKNOWN}
     */
    public CapabilityState state(String capability) {
        return definitive.getOrDefault(validateCapability(capability), CapabilityState.UNKNOWN);
    }

    /**
     * Records definitive capability knowledge derived from environment data or known rules.
     *
     * @param capability stable capability identifier
     * @param state definitive state
     */
    public void recordDefinitive(String capability, CapabilityState state) {
        String key = validateCapability(capability);
        CapabilityState checked = Objects.requireNonNull(state, "state");
        if (!checked.isDefinitive()) {
            throw new IllegalArgumentException("only definitive capability states may be cached");
        }
        CapabilityState existing = definitive.putIfAbsent(key, checked);
        if (existing != null && existing != checked) {
            throw new IllegalStateException(
                    "conflicting definitive capability state for " + key);
        }
        diagnostics.capabilityDecision(
                key,
                (existing == null ? checked : existing).name(),
                existing == null ? "recorded" : "cached");
    }

    /**
     * Resolves one capability with a validated read-only RouterOS probe when no definitive cache exists.
     *
     * <p>v1 active probes are deliberately restricted to RouterOS {@code /print} commands.
     * Package-, version-, board-, and rule-derived capability knowledge should use
     * {@link #recordDefinitive(String, CapabilityState)} instead of synthetic commands.</p>
     *
     * @param capability stable capability identifier
     * @param probeCommand read-only RouterOS print command
     * @param probe probe implementation
     * @return resolved state
     * @throws MikrotikFacadeException when the technical probe itself fails; that failure is not cached
     */
    public CapabilityState resolve(
            String capability,
            RouterOsCommand probeCommand,
            CapabilityProbe probe) throws MikrotikFacadeException {
        String key = validateCapability(capability);
        RouterOsCommand command = Objects.requireNonNull(probeCommand, "probeCommand");
        CapabilityProbe checkedProbe = Objects.requireNonNull(probe, "probe");
        validateReadOnly(command);

        CapabilityState cached = definitive.get(key);
        if (cached != null) {
            diagnostics.capabilityDecision(key, cached.name(), "cache");
            return cached;
        }

        synchronized (probeLock) {
            cached = definitive.get(key);
            if (cached != null) {
                diagnostics.capabilityDecision(key, cached.name(), "cache");
                return cached;
            }
            CapabilityState resolved = Objects.requireNonNull(
                    checkedProbe.probe(command),
                    "probe result");
            if (resolved.isDefinitive()) {
                definitive.put(key, resolved);
            }
            diagnostics.capabilityDecision(key, resolved.name(), "probe");
            return resolved;
        }
    }

    /**
     * Returns an immutable deterministic snapshot of definitive cached knowledge.
     *
     * @return immutable capability snapshot sorted by identifier
     */
    public Map<String, CapabilityState> snapshot() {
        LinkedHashMap<String, CapabilityState> ordered = new LinkedHashMap<>();
        definitive.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    /** Read-only capability probe implementation supplied by a feature module. */
    @FunctionalInterface
    public interface CapabilityProbe {
        /**
         * Executes the already validated read-only probe and classifies its result.
         *
         * @param command validated read-only RouterOS print command
         * @return supported, unsupported, or unknown
         * @throws MikrotikFacadeException when the technical probe fails
         */
        CapabilityState probe(RouterOsCommand command) throws MikrotikFacadeException;
    }

    private static void validateReadOnly(RouterOsCommand command) {
        String path = command.path();
        if (!path.endsWith("/print")) {
            throw new IllegalArgumentException("capability probes must use read-only RouterOS /print commands");
        }
    }

    private static String validateCapability(String capability) {
        Objects.requireNonNull(capability, "capability");
        String trimmed = capability.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("capability must not be blank");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '-' || ch == ':')) {
                throw new IllegalArgumentException("capability contains unsupported diagnostic characters");
            }
        }
        return trimmed;
    }
}
