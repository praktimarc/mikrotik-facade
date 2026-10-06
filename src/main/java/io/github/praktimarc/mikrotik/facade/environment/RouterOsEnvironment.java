package io.github.praktimarc.mikrotik.facade.environment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable RouterOS environment snapshot created once during session bootstrap.
 */
public final class RouterOsEnvironment {

    private final RouterOsSystemInfo systemInfo;
    private final List<RouterOsPackage> packages;

    private RouterOsEnvironment(RouterOsSystemInfo systemInfo, List<RouterOsPackage> packages) {
        this.systemInfo = Objects.requireNonNull(systemInfo, "systemInfo");
        this.packages = packages == null ? null : List.copyOf(packages);
    }

    /**
     * Creates an environment with an available package snapshot.
     *
     * @param systemInfo required system information
     * @param packages installed package snapshot, which may be empty
     * @return immutable environment
     */
    public static RouterOsEnvironment withPackages(
            RouterOsSystemInfo systemInfo,
            List<RouterOsPackage> packages) {
        return new RouterOsEnvironment(systemInfo, Objects.requireNonNull(packages, "packages"));
    }

    /**
     * Creates an environment for a RouterOS system where package information is explicitly unavailable.
     *
     * @param systemInfo required system information
     * @return immutable environment without package information
     */
    public static RouterOsEnvironment withoutPackageInformation(RouterOsSystemInfo systemInfo) {
        return new RouterOsEnvironment(systemInfo, null);
    }

    /**
     * Returns the mandatory system-information snapshot.
     *
     * @return system information
     */
    public RouterOsSystemInfo systemInfo() {
        return systemInfo;
    }

    /**
     * Returns the installed-package snapshot when RouterOS supports that information.
     *
     * <p>An available empty list means that the command completed successfully and reported no package rows.
     * An empty optional means that package information was explicitly unavailable on the target system.</p>
     *
     * @return optional immutable package list
     */
    public Optional<List<RouterOsPackage>> packages() {
        return Optional.ofNullable(packages);
    }

    /**
     * Reports whether package information was available during bootstrap.
     *
     * @return {@code true} when a package snapshot, including an empty one, is available
     */
    public boolean packageInformationAvailable() {
        return packages != null;
    }
}
