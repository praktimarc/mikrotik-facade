package io.github.praktimarc.mikrotik.facade.environment;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable system information captured from {@code /system/resource} during bootstrap.
 */
public final class RouterOsSystemInfo implements RouterOsEntity {

    private final String version;
    private final String architectureName;
    private final String boardName;
    private final String platform;
    private final RouterOsRecord raw;

    /**
     * Creates an immutable system-information snapshot.
     *
     * @param version RouterOS version
     * @param architectureName architecture name, or {@code null} when unavailable
     * @param boardName board name, or {@code null} when unavailable
     * @param platform platform name, or {@code null} when unavailable
     * @param raw complete raw RouterOS record
     */
    public RouterOsSystemInfo(
            String version,
            String architectureName,
            String boardName,
            String platform,
            RouterOsRecord raw) {
        this.version = requireNonBlank(version, "version");
        this.architectureName = normalizeOptional(architectureName);
        this.boardName = normalizeOptional(boardName);
        this.platform = normalizeOptional(platform);
        this.raw = Objects.requireNonNull(raw, "raw");
    }

    /**
     * Returns the RouterOS version reported by the session.
     *
     * @return non-blank version string
     */
    public String version() {
        return version;
    }

    /**
     * Returns the RouterOS architecture name when reported.
     *
     * @return optional architecture name
     */
    public Optional<String> architectureName() {
        return Optional.ofNullable(architectureName);
    }

    /**
     * Returns the board name when reported.
     *
     * @return optional board name
     */
    public Optional<String> boardName() {
        return Optional.ofNullable(boardName);
    }

    /**
     * Returns the platform name when reported.
     *
     * @return optional platform name
     */
    public Optional<String> platform() {
        return Optional.ofNullable(platform);
    }

    @Override
    public RouterOsRecord raw() {
        return raw;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
