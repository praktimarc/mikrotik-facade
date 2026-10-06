package io.github.praktimarc.mikrotik.facade.exception;

/**
 * Indicates that a typed facade feature is definitively unsupported by the session environment.
 */
public class MikrotikUnsupportedFeatureException extends MikrotikFacadeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message safe diagnostic message
     */
    public MikrotikUnsupportedFeatureException(String message) {
        super(message);
    }

    /**
     * Creates an exception with its original cause.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikUnsupportedFeatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
