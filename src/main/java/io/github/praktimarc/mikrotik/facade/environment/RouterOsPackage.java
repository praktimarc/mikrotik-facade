package io.github.praktimarc.mikrotik.facade.environment;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable installed-package snapshot captured during bootstrap.
 */
public final class RouterOsPackage implements RouterOsEntity {

    private final String name;
    private final String version;
    private final RouterOsRecord raw;

    /**
     * Creates an immutable package snapshot.
     *
     * @param name package name
     * @param version package version, or {@code null} when not reported
     * @param raw complete raw RouterOS record
     */
    public RouterOsPackage(String name, String version, RouterOsRecord raw) {
        this.name = requireNonBlank(name, "name");
        this.version = version == null || version.isBlank() ? null : version;
        this.raw = Objects.requireNonNull(raw, "raw");
    }

    /**
     * Returns the package name.
     *
     * @return non-blank package name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the package version when RouterOS reported one.
     *
     * @return optional version
     */
    public Optional<String> version() {
        return Optional.ofNullable(version);
    }

    @Override
    public RouterOsRecord raw() {
        return raw;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
