package io.github.praktimarc.mikrotik.facade.exception;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Indicates a RouterOS command-level failure.
 *
 * <p>The transport tag is intentionally not part of the public facade contract.</p>
 */
public class MikrotikCommandException extends MikrotikFacadeException {

    /** Safe facade operation name, when available. */
    private final String operation;
    /** RouterOS command path without arguments, when available. */
    private final String commandPath;
    /** RouterOS error category, where null means that no category was supplied. */
    private final Integer category;
    /** Sanitized RouterOS command error message, when available. */
    private final String routerOsMessage;

    /**
     * Creates a command exception without structured command context.
     *
     * @param message safe diagnostic message
     */
    public MikrotikCommandException(String message) {
        this(message, null, null, null, null, null);
    }

    /**
     * Creates a command exception without structured command context.
     *
     * @param message safe diagnostic message
     * @param cause original failure
     */
    public MikrotikCommandException(String message, Throwable cause) {
        this(message, null, null, null, null, cause);
    }

    /**
     * Creates a command exception with safe structured RouterOS context.
     *
     * @param message safe diagnostic message
     * @param operation facade operation name, or null when unavailable
     * @param commandPath RouterOS command path without arguments, or null when unavailable
     * @param category RouterOS category, including legitimate zero, or null when absent
     * @param routerOsMessage sanitized RouterOS error message, or null when unavailable
     * @param cause original failure, or null when unavailable
     */
    public MikrotikCommandException(
            String message,
            String operation,
            String commandPath,
            Integer category,
            String routerOsMessage,
            Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.commandPath = commandPath;
        this.category = category;
        this.routerOsMessage = routerOsMessage;
    }

    /**
     * Returns the facade operation name when available.
     *
     * @return optional operation name
     */
    public Optional<String> operation() {
        return Optional.ofNullable(operation);
    }

    /**
     * Returns the RouterOS command path when available.
     *
     * @return optional command path
     */
    public Optional<String> commandPath() {
        return Optional.ofNullable(commandPath);
    }

    /**
     * Returns the RouterOS error category when RouterOS supplied one.
     *
     * <p>A real category value of zero remains distinguishable from an absent category.</p>
     *
     * @return optional RouterOS category
     */
    public OptionalInt category() {
        return category == null ? OptionalInt.empty() : OptionalInt.of(category);
    }

    /**
     * Returns the sanitized RouterOS error message when available.
     *
     * @return optional RouterOS message
     */
    public Optional<String> routerOsMessage() {
        return Optional.ofNullable(routerOsMessage);
    }
}
