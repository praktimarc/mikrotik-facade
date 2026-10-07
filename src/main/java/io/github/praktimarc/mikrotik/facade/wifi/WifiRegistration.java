package io.github.praktimarc.mikrotik.facade.wifi;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable normalized view of one RouterOS WiFi/CAPsMAN registration row. */
public final class WifiRegistration implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String source;
    private final Optional<String> id;
    private final Optional<String> interfaceName;
    private final Optional<String> ssid;
    private final Optional<String> macAddress;
    private final Optional<Duration> uptime;
    private final OptionalLong signalDbm;
    private final Optional<String> txRate;
    private final Optional<String> rxRate;
    private final Optional<String> packets;
    private final Optional<String> bytes;
    private final OptionalLong txBitsPerSecond;
    private final OptionalLong rxBitsPerSecond;
    private final OptionalLong vlanId;
    private final Optional<Boolean> authorized;
    private final Optional<String> eapIdentity;

    /**
     * Creates a normalized immutable registration entry.
     *
     * @param raw complete source record
     * @param source exact compatibility source identifier
     * @param id RouterOS row id
     * @param interfaceName associated interface name
     * @param ssid associated SSID
     * @param macAddress peer MAC address
     * @param uptime association uptime
     * @param signalDbm received signal in dBm
     * @param txRate transmit bitrate representation
     * @param rxRate receive bitrate representation
     * @param packets exact RouterOS transmitted/received packet representation
     * @param bytes exact RouterOS transmitted/received byte representation
     * @param txBitsPerSecond current transmitted bits per second
     * @param rxBitsPerSecond current received bits per second
     * @param vlanId assigned VLAN id
     * @param authorized authentication state
     * @param eapIdentity EAP identity when reported
     */
    public WifiRegistration(
            RouterOsRecord raw,
            String source,
            Optional<String> id,
            Optional<String> interfaceName,
            Optional<String> ssid,
            Optional<String> macAddress,
            Optional<Duration> uptime,
            OptionalLong signalDbm,
            Optional<String> txRate,
            Optional<String> rxRate,
            Optional<String> packets,
            Optional<String> bytes,
            OptionalLong txBitsPerSecond,
            OptionalLong rxBitsPerSecond,
            OptionalLong vlanId,
            Optional<Boolean> authorized,
            Optional<String> eapIdentity) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.source = requireNonBlank(source, "source");
        this.id = Objects.requireNonNull(id, "id");
        this.interfaceName = Objects.requireNonNull(interfaceName, "interfaceName");
        this.ssid = Objects.requireNonNull(ssid, "ssid");
        this.macAddress = Objects.requireNonNull(macAddress, "macAddress");
        this.uptime = Objects.requireNonNull(uptime, "uptime");
        this.signalDbm = Objects.requireNonNull(signalDbm, "signalDbm");
        this.txRate = Objects.requireNonNull(txRate, "txRate");
        this.rxRate = Objects.requireNonNull(rxRate, "rxRate");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.txBitsPerSecond = Objects.requireNonNull(txBitsPerSecond, "txBitsPerSecond");
        this.rxBitsPerSecond = Objects.requireNonNull(rxBitsPerSecond, "rxBitsPerSecond");
        this.vlanId = Objects.requireNonNull(vlanId, "vlanId");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.eapIdentity = Objects.requireNonNull(eapIdentity, "eapIdentity");
    }

    /** Returns the exact compatibility source used for this row.
     * @return exact compatibility source identifier
     */
    public String source() { return source; }

    /** Returns the RouterOS row id when present.
     * @return optional RouterOS row id
     */
    public Optional<String> id() { return id; }

    /** Returns the associated RouterOS interface name when present.
     * @return optional associated interface name
     */
    public Optional<String> interfaceName() { return interfaceName; }

    /** Returns the associated SSID when present.
     * @return optional associated SSID
     */
    public Optional<String> ssid() { return ssid; }

    /** Returns the registered peer MAC address when present.
     * @return optional peer MAC address
     */
    public Optional<String> macAddress() { return macAddress; }

    /** Returns the association uptime when present.
     * @return optional association uptime
     */
    public Optional<Duration> uptime() { return uptime; }

    /** Returns normalized received signal strength in dBm when present.
     * @return optional received signal strength in dBm
     */
    public OptionalLong signalDbm() { return signalDbm; }

    /** Returns the exact RouterOS transmit-rate representation when present.
     * @return optional exact transmit-rate representation
     */
    public Optional<String> txRate() { return txRate; }

    /** Returns the exact RouterOS receive-rate representation when present.
     * @return optional exact receive-rate representation
     */
    public Optional<String> rxRate() { return rxRate; }

    /** Returns the exact RouterOS transmitted/received packet representation when present.
     * @return optional exact transmitted/received packet representation
     */
    public Optional<String> packets() { return packets; }

    /** Returns the exact RouterOS transmitted/received byte representation when present.
     * @return optional exact transmitted/received byte representation
     */
    public Optional<String> bytes() { return bytes; }

    /** Returns the current transmitted bits per second when present.
     * @return optional current transmitted bits per second
     */
    public OptionalLong txBitsPerSecond() { return txBitsPerSecond; }

    /** Returns the current received bits per second when present.
     * @return optional current received bits per second
     */
    public OptionalLong rxBitsPerSecond() { return rxBitsPerSecond; }

    /** Returns the assigned VLAN id when present.
     * @return optional assigned VLAN id
     */
    public OptionalLong vlanId() { return vlanId; }

    /** Returns the peer authorization state when present.
     * @return optional peer authorization state
     */
    public Optional<Boolean> authorized() { return authorized; }

    /** Returns the EAP identity when present.
     * @return optional EAP identity
     */
    public Optional<String> eapIdentity() { return eapIdentity; }

    /** Returns the complete unchanged RouterOS source record.
     * @return complete unchanged RouterOS source record
     */
    @Override
    public RouterOsRecord raw() { return raw; }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
