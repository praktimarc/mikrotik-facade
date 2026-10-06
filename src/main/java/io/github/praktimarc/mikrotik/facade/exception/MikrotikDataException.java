package io.github.praktimarc.mikrotik.facade.exception;

/**
 * Indicates malformed, inconsistent, or otherwise unusable RouterOS data.
 */
public class MikrotikDataException extends MikrotikFacadeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message safe diagnostic message
     */
    public MikrotikDataException(String message) {
        super(message);
    }

    /**
     * Creates an exception with its original cause.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
