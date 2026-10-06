package io.github.praktimarc.mikrotik.facade.dhcp;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable typed view of a RouterOS DHCP server lease.
 *
 * <p>The typed surface mirrors the fields used by the legacy ISPSup DHCP lease DTO.
 * Unknown or newer RouterOS properties remain available through {@link #raw()}.</p>
 */
public final class DhcpLease implements RouterOsEntity {

    private final RouterOsRecord raw;
    private final Optional<String> address;
    private final Optional<String> macAddress;
    private final Optional<String> clientId;
    private final Optional<String> addressLists;
    private final Optional<String> server;
    private final Optional<String> dhcpOption;
    private final Optional<String> status;
    private final Optional<Duration> expiresAfter;
    private final Optional<Duration> lastSeen;
    private final Optional<String> activeAddress;
    private final Optional<String> activeMacAddress;
    private final Optional<String> activeClientId;
    private final Optional<String> activeServer;
    private final Optional<String> hostName;
    private final Optional<String> agentCircuitId;
    private final Optional<String> agentRemoteId;
    private final Optional<Boolean> radius;
    private final Optional<Boolean> dynamic;
    private final Optional<Boolean> blocked;
    private final Optional<Boolean> disabled;
    private final Optional<String> comment;

    /**
     * Creates a mapped immutable DHCP lease.
     *
     * @param raw complete raw RouterOS lease record
     * @param address lease address
     * @param macAddress lease MAC address
     * @param clientId configured client id
     * @param addressLists configured address-list value
     * @param server DHCP server name
     * @param dhcpOption configured DHCP option value
     * @param status lease status
     * @param expiresAfter remaining lease duration
     * @param lastSeen time since last observation
     * @param activeAddress active address
     * @param activeMacAddress active MAC address
     * @param activeClientId active client id
     * @param activeServer active DHCP server
     * @param hostName client host name
     * @param agentCircuitId normalized known agent circuit id
     * @param agentRemoteId normalized known agent remote id
     * @param radius whether the lease originates from RADIUS
     * @param dynamic whether the lease is dynamic
     * @param blocked whether the lease is blocked
     * @param disabled whether the lease is disabled
     * @param comment lease comment
     */
    public DhcpLease(
            RouterOsRecord raw,
            Optional<String> address,
            Optional<String> macAddress,
            Optional<String> clientId,
            Optional<String> addressLists,
            Optional<String> server,
            Optional<String> dhcpOption,
            Optional<String> status,
            Optional<Duration> expiresAfter,
            Optional<Duration> lastSeen,
            Optional<String> activeAddress,
            Optional<String> activeMacAddress,
            Optional<String> activeClientId,
            Optional<String> activeServer,
            Optional<String> hostName,
            Optional<String> agentCircuitId,
            Optional<String> agentRemoteId,
            Optional<Boolean> radius,
            Optional<Boolean> dynamic,
            Optional<Boolean> blocked,
            Optional<Boolean> disabled,
            Optional<String> comment) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.address = nonNull(address, "address");
        this.macAddress = nonNull(macAddress, "macAddress");
        this.clientId = nonNull(clientId, "clientId");
        this.addressLists = nonNull(addressLists, "addressLists");
        this.server = nonNull(server, "server");
        this.dhcpOption = nonNull(dhcpOption, "dhcpOption");
        this.status = nonNull(status, "status");
        this.expiresAfter = nonNull(expiresAfter, "expiresAfter");
        this.lastSeen = nonNull(lastSeen, "lastSeen");
        this.activeAddress = nonNull(activeAddress, "activeAddress");
        this.activeMacAddress = nonNull(activeMacAddress, "activeMacAddress");
        this.activeClientId = nonNull(activeClientId, "activeClientId");
        this.activeServer = nonNull(activeServer, "activeServer");
        this.hostName = nonNull(hostName, "hostName");
        this.agentCircuitId = nonNull(agentCircuitId, "agentCircuitId");
        this.agentRemoteId = nonNull(agentRemoteId, "agentRemoteId");
        this.radius = nonNull(radius, "radius");
        this.dynamic = nonNull(dynamic, "dynamic");
        this.blocked = nonNull(blocked, "blocked");
        this.disabled = nonNull(disabled, "disabled");
        this.comment = nonNull(comment, "comment");
    }

    /** Returns the lease address.
     * @return optional lease address
     */
    public Optional<String> address() { return address; }
    /** Returns the lease MAC address.
     * @return optional lease MAC address
     */
    public Optional<String> macAddress() { return macAddress; }
    /** Returns the configured client id.
     * @return optional configured client id
     */
    public Optional<String> clientId() { return clientId; }
    /** Returns the configured address-list value.
     * @return optional configured address-list value
     */
    public Optional<String> addressLists() { return addressLists; }
    /** Returns the DHCP server name.
     * @return optional DHCP server name
     */
    public Optional<String> server() { return server; }
    /** Returns the configured DHCP option value.
     * @return optional configured DHCP option value
     */
    public Optional<String> dhcpOption() { return dhcpOption; }
    /** Returns the lease status.
     * @return optional lease status
     */
    public Optional<String> status() { return status; }
    /** Returns the remaining lease duration.
     * @return optional remaining lease duration
     */
    public Optional<Duration> expiresAfter() { return expiresAfter; }
    /** Returns the time since last observation.
     * @return optional time since last observation
     */
    public Optional<Duration> lastSeen() { return lastSeen; }
    /** Returns the active address.
     * @return optional active address
     */
    public Optional<String> activeAddress() { return activeAddress; }
    /** Returns the active MAC address.
     * @return optional active MAC address
     */
    public Optional<String> activeMacAddress() { return activeMacAddress; }
    /** Returns the active client id.
     * @return optional active client id
     */
    public Optional<String> activeClientId() { return activeClientId; }
    /** Returns the active DHCP server.
     * @return optional active DHCP server
     */
    public Optional<String> activeServer() { return activeServer; }
    /** Returns the client host name.
     * @return optional client host name
     */
    public Optional<String> hostName() { return hostName; }
    /** Returns the normalized known agent circuit id.
     * @return optional normalized known agent circuit id
     */
    public Optional<String> agentCircuitId() { return agentCircuitId; }
    /** Returns the normalized known agent remote id.
     * @return optional normalized known agent remote id
     */
    public Optional<String> agentRemoteId() { return agentRemoteId; }
    /** Returns the RADIUS flag.
     * @return optional RADIUS flag
     */
    public Optional<Boolean> radius() { return radius; }
    /** Returns the dynamic flag.
     * @return optional dynamic flag
     */
    public Optional<Boolean> dynamic() { return dynamic; }
    /** Returns the blocked flag.
     * @return optional blocked flag
     */
    public Optional<Boolean> blocked() { return blocked; }
    /** Returns the disabled flag.
     * @return optional disabled flag
     */
    public Optional<Boolean> disabled() { return disabled; }
    /** Returns the lease comment.
     * @return optional lease comment
     */
    public Optional<String> comment() { return comment; }

    @Override
    public RouterOsRecord raw() { return raw; }

    private static <T> Optional<T> nonNull(Optional<T> value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
