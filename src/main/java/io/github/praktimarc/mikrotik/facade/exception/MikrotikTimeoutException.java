package io.github.praktimarc.mikrotik.facade.exception;

/**
 * Indicates that a finite facade command exceeded its configured timeout.
 */
public class MikrotikTimeoutException extends MikrotikCommandException {

    /**
     * Creates the exception with a diagnostic message.
     *
     * @param message safe diagnostic message
     */
    public MikrotikTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates the exception with its original cause.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception with safe structured command context.
     *
     * @param message safe diagnostic message
     * @param operation facade operation name, or null when unavailable
     * @param commandPath RouterOS command path without arguments, or null when unavailable
     * @param cause original failure, or null when unavailable
     */
    public MikrotikTimeoutException(String message, String operation, String commandPath, Throwable cause) {
        super(message, operation, commandPath, null, null, cause);
    }
}
