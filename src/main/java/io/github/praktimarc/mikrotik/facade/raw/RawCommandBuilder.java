package io.github.praktimarc.mikrotik.facade.raw;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent structured raw RouterOS command builder.
 */
public final class RawCommandBuilder {

    private final RawApi owner;
    private final RouterOsCommand.Builder command;

    RawCommandBuilder(RawApi owner, String path) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.command = RouterOsCommand.builder(path);
    }

    /**
     * Adds or replaces a command argument.
     *
     * @param name RouterOS argument name
     * @param value exact argument value
     * @return this builder
     */
    public RawCommandBuilder argument(String name, String value) {
        command.argument(name, value);
        return this;
    }

    /**
     * Adds or replaces an equality query.
     *
     * @param name RouterOS query property name
     * @param value exact query value
     * @return this builder
     */
    public RawCommandBuilder query(String name, String value) {
        command.query(name, value);
        return this;
    }

    /**
     * Selects a returned RouterOS property.
     *
     * @param name property name
     * @return this builder
     */
    public RawCommandBuilder property(String name) {
        command.property(name);
        return this;
    }

    /**
     * Executes the structured raw command synchronously.
     *
     * @return records plus terminal completion properties
     * @throws MikrotikFacadeException if the RouterOS operation fails
     */
    public RawCommandResult execute() throws MikrotikFacadeException {
        return owner.executeCommand(command.build());
    }

    /**
     * Asynchronous mirror of the structured raw command builder.
     */
    public static final class Async {

        private final AsyncRawApi owner;
        private final RouterOsCommand.Builder command;

        Async(AsyncRawApi owner, String path) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.command = RouterOsCommand.builder(path);
        }

        /**
         * Adds or replaces a command argument.
         *
         * @param name RouterOS argument name
         * @param value exact argument value
         * @return this builder
         */
        public Async argument(String name, String value) {
            command.argument(name, value);
            return this;
        }

        /**
         * Adds or replaces an equality query.
         *
         * @param name RouterOS query property name
         * @param value exact query value
         * @return this builder
         */
        public Async query(String name, String value) {
            command.query(name, value);
            return this;
        }

        /**
         * Selects a returned RouterOS property.
         *
         * @param name property name
         * @return this builder
         */
        public Async property(String name) {
            command.property(name);
            return this;
        }

        /**
         * Executes the structured raw command asynchronously.
         *
         * @return cancellable future for records plus terminal completion properties
         */
        public CompletableFuture<RawCommandResult> execute() {
            return owner.executeCommand(command.build());
        }
    }

    static final class ResultOperation implements RouterOsOperation<RawCommandResult> {

        private final RouterOsCommand command;

        ResultOperation(RouterOsCommand command) {
            this.command = Objects.requireNonNull(command, "command");
        }

        @Override
        public String name() {
            return "raw " + command.path();
        }

        @Override
        public RouterOsCommand command() {
            return command;
        }

        @Override
        public RawCommandResult map(CommandResult result) {
            return RawCommandResult.from(result);
        }
    }

    static final class RecordsOperation implements RouterOsOperation<List<RouterOsRecord>> {

        private final RouterOsCommand command;

        RecordsOperation(RouterOsCommand command) {
            this.command = Objects.requireNonNull(command, "command");
        }

        @Override
        public String name() {
            return "raw " + command.path();
        }

        @Override
        public RouterOsCommand command() {
            return command;
        }

        @Override
        public List<RouterOsRecord> map(CommandResult result) {
            return result.records();
        }
    }
}
