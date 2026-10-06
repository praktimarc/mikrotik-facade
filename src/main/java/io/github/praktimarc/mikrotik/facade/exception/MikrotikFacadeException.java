package io.github.praktimarc.mikrotik.facade.exception;

/**
 * Base checked exception for technical or data failures reported by the facade.
 */
public class MikrotikFacadeException extends Exception {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message safe diagnostic message
     */
    public MikrotikFacadeException(String message) {
        super(message);
    }

    /**
     * Creates an exception with its original cause.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikFacadeException(String message, Throwable cause) {
        super(message, cause);
    }
}
