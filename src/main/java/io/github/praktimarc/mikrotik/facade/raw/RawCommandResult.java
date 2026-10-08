package io.github.praktimarc.mikrotik.facade.raw;

import io.github.praktimarc.mikrotik.facade.ClientSideFilter;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;

import java.util.List;
import java.util.Objects;

/**
 * Immutable public result of one finite raw RouterOS command.
 */
public final class RawCommandResult {

    private final List<RouterOsRecord> records;
    private final RouterOsRecord completion;

    /**
     * Creates an immutable raw command result.
     *
     * @param records normal {@code !re} records
     * @param completion terminal {@code !done} properties
     */
    public RawCommandResult(List<RouterOsRecord> records, RouterOsRecord completion) {
        this.records = List.copyOf(Objects.requireNonNull(records, "records"));
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    static RawCommandResult from(CommandResult result) {
        Objects.requireNonNull(result, "result");
        return new RawCommandResult(result.records(), result.completion());
    }

    /**
     * Returns the normal {@code !re} records.
     *
     * @return immutable record list
     */
    public List<RouterOsRecord> records() {
        return records;
    }

    /**
     * Applies an explicit client-side filter to the returned records.
     *
     * <p>This never alters or re-executes the RouterOS server query.</p>
     */
    public List<RouterOsRecord> records(ClientSideFilter filter) {
        return Objects.requireNonNull(filter, "filter").apply(records);
    }

    /**
     * Returns the terminal {@code !done} properties.
     *
     * @return immutable completion record, possibly empty
     */
    public RouterOsRecord completion() {
        return completion;
    }
}
