package io.github.praktimarc.mikrotik.facade.async;

import io.github.praktimarc.mikrotik.facade.raw.AsyncRawApi;

import java.util.Objects;

/**
 * Mirrored asynchronous facade tree for one RouterOS session.
 */
public final class AsyncMikrotikRtrApi {

    private final AsyncRawApi raw;

    /**
     * Creates the asynchronous facade tree for session wiring.
     *
     * @param raw asynchronous raw API
     */
    public AsyncMikrotikRtrApi(AsyncRawApi raw) {
        this.raw = Objects.requireNonNull(raw, "raw");
    }

    /**
     * Returns the asynchronous raw RouterOS escape hatch.
     *
     * @return asynchronous raw API
     */
    public AsyncRawApi raw() {
        return raw;
    }
}
