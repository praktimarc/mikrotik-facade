package io.github.praktimarc.mikrotik.facade.internal.session;

/**
 * Internal runtime state of a fully bootstrapped facade session.
 */
public enum SessionState {
    /** Session accepts RouterOS operations. */
    OPEN,
    /** Session suffered an unexpected fatal low-level connection loss. */
    BROKEN,
    /** Controlled close has started and new work must be rejected. */
    CLOSING,
    /** Session resources are closed. */
    CLOSED
}
