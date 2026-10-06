package io.github.praktimarc.mikrotik.facade.internal.compat;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;

/** RouterOS record plus the exact compatibility source from which it originated. */
public final class ResolvedRecord {

    private final RouterOsRecord record;
    private final String source;

    /**
     * Creates a provenance-preserving resolved record.
     *
     * @param record immutable raw record
     * @param source exact compatibility source identifier
     */
    public ResolvedRecord(RouterOsRecord record, String source) {
        this.record = Objects.requireNonNull(record, "record");
        this.source = DataSourcePlan.validateIdentifier(source, "source");
    }

    /**
     * Returns the immutable raw record.
     *
     * @return raw record
     */
    public RouterOsRecord record() {
        return record;
    }

    /**
     * Returns the exact selected source identifier.
     *
     * @return source identifier
     */
    public String source() {
        return source;
    }
}
