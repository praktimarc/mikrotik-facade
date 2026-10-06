package io.github.praktimarc.mikrotik.facade.internal.capability;

/** Internal tri-state capability result for one authenticated RouterOS session. */
public enum CapabilityState {
    /** Capability is definitively available. */
    SUPPORTED,
    /** Capability is definitively unavailable. */
    UNSUPPORTED,
    /** Capability could not be determined definitively. */
    UNKNOWN;

    /**
     * Returns whether this state is safe to cache for the lifetime of the session.
     *
     * @return {@code true} for supported or unsupported
     */
    public boolean isDefinitive() {
        return this != UNKNOWN;
    }
}
