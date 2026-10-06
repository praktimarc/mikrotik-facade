package io.github.praktimarc.mikrotik.facade.exception;

/**
 * Indicates a RouterOS transport or session connection failure.
 */
public class MikrotikConnectionException extends MikrotikFacadeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message safe diagnostic message
     */
    public MikrotikConnectionException(String message) {
        super(message);
    }

    /**
     * Creates an exception with its original cause.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
