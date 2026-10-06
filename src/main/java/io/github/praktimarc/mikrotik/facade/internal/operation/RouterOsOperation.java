package io.github.praktimarc.mikrotik.facade.internal.operation;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;

/**
 * Internal typed operation shared by synchronous and asynchronous facade paths.
 *
 * @param <T> mapped result type
 */
public interface RouterOsOperation<T> {

    /** Returns a stable safe operation name. */
    String name();

    /** Returns the immutable RouterOS command. */
    RouterOsCommand command();

    /**
     * Maps the common command result to the typed result.
     *
     * @throws MikrotikFacadeException if typed interpretation fails
     */
    T map(CommandResult result) throws MikrotikFacadeException;
}
