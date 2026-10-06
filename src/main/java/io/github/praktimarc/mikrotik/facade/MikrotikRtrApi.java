package io.github.praktimarc.mikrotik.facade;

/**
 * Entry point for one authenticated RouterOS facade session.
 *
 * <p>Session creation is configured through {@link #builder()}. The bootstrap
 * implementation is introduced separately so the builder contract can be
 * validated independently.</p>
 */
public final class MikrotikRtrApi implements AutoCloseable {

    private MikrotikRtrApi() {
    }

    /**
     * Creates a new facade builder.
     *
     * @return new builder with safe defaults
     */
    public static MikrotikRtrApiBuilder builder() {
        return new MikrotikRtrApiBuilder();
    }

    /**
     * Closes this facade session.
     *
     * <p>The concrete session lifecycle is installed by the bootstrap layer.
     * No externally constructible facade instance exists before that layer is
     * present.</p>
     */
    @Override
    public void close() {
    }
}
