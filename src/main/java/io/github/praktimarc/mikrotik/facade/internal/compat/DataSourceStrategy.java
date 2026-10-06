package io.github.praktimarc.mikrotik.facade.internal.compat;

/** Supported RouterOS source-resolution strategies. */
public enum DataSourceStrategy {
    /** Exactly one authoritative source is selected. */
    SINGLE,
    /** Prefer one source and use the fallback only when the preferred source is unavailable. */
    PREFERRED_FALLBACK,
    /** A compatibility condition has selected exactly one source. */
    CONDITIONAL,
    /** Multiple sources are simultaneously relevant and retain independent provenance. */
    COMPOSITE
}
