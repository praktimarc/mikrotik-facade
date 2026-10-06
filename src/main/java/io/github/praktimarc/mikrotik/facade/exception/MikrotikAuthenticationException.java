package io.github.praktimarc.mikrotik.facade.exception;

/**
 * Indicates an authentication failure during session bootstrap.
 */
public class MikrotikAuthenticationException extends MikrotikFacadeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message safe diagnostic message
     */
    public MikrotikAuthenticationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with its original cause.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
