package io.github.praktimarc.mikrotik.facade.internal.command;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of one finite RouterOS command.
 *
 * <p>Normal !re records and terminal !done properties remain separate.</p>
 */
public final class CommandResult {

    private final List<RouterOsRecord> records;
    private final RouterOsRecord completion;

    /** Creates an immutable command result. */
    public CommandResult(List<RouterOsRecord> records, RouterOsRecord completion) {
        this.records = List.copyOf(Objects.requireNonNull(records, "records"));
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    /** Creates a result from raw maps. */
    public static CommandResult ofRaw(List<Map<String, String>> records, Map<String, String> completion) {
        Objects.requireNonNull(records, "records");
        return new CommandResult(
                records.stream().map(RouterOsRecord::of).toList(),
                RouterOsRecord.of(Objects.requireNonNull(completion, "completion")));
    }

    /** Returns normal !re records. */
    public List<RouterOsRecord> records() {
        return records;
    }

    /** Returns terminal !done properties, possibly empty. */
    public RouterOsRecord completion() {
        return completion;
    }
}
