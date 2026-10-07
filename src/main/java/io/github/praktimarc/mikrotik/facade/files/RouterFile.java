package io.github.praktimarc.mikrotik.facade.files;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable typed view of one RouterOS file metadata row. */
public final class RouterFile implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String name;
    private final Optional<String> id;
    private final Optional<String> type;
    private final OptionalLong sizeBytes;
    private final Optional<String> creationTime;
    private final Optional<String> lastModified;
    private final Optional<String> packageName;
    private final Optional<String> packageVersion;

    /** Creates one immutable RouterOS file metadata entity. */
    public RouterFile(
            RouterOsRecord raw,
            String name,
            Optional<String> id,
            Optional<String> type,
            OptionalLong sizeBytes,
            Optional<String> creationTime,
            Optional<String> lastModified,
            Optional<String> packageName,
            Optional<String> packageVersion) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.name = requireNonBlank(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.sizeBytes = Objects.requireNonNull(sizeBytes, "sizeBytes");
        this.creationTime = Objects.requireNonNull(creationTime, "creationTime");
        this.lastModified = Objects.requireNonNull(lastModified, "lastModified");
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.packageVersion = Objects.requireNonNull(packageVersion, "packageVersion");
    }

    public String name() { return name; }
    public Optional<String> id() { return id; }
    public Optional<String> type() { return type; }
    public OptionalLong sizeBytes() { return sizeBytes; }
    public Optional<String> creationTime() { return creationTime; }
    public Optional<String> lastModified() { return lastModified; }
    public Optional<String> packageName() { return packageName; }
    public Optional<String> packageVersion() { return packageVersion; }

    @Override
    public RouterOsRecord raw() { return raw; }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
