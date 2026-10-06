package io.github.praktimarc.mikrotik.facade.exception;

/**
 * Indicates a file-specific facade failure such as a local file-system error.
 */
public class MikrotikFileException extends MikrotikFacadeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message safe diagnostic message
     */
    public MikrotikFileException(String message) {
        super(message);
    }

    /**
     * Creates an exception with its original cause.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
