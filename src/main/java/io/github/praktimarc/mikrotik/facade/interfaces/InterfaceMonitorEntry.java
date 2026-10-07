package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** One immutable sample emitted by RouterOS interface traffic monitoring. */
public final class InterfaceMonitorEntry implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final Optional<String> name;
    private final OptionalLong rxPacketsPerSecond;
    private final OptionalLong txPacketsPerSecond;
    private final OptionalLong rxBitsPerSecond;
    private final OptionalLong txBitsPerSecond;
    private final OptionalLong fpRxPacketsPerSecond;
    private final OptionalLong fpTxPacketsPerSecond;
    private final OptionalLong fpRxBitsPerSecond;
    private final OptionalLong fpTxBitsPerSecond;
    private final OptionalLong rxDropsPerSecond;
    private final OptionalLong txDropsPerSecond;
    private final OptionalLong rxErrorsPerSecond;
    private final OptionalLong txErrorsPerSecond;
    private final OptionalLong txQueueDropsPerSecond;

    /** Creates one normalized monitoring sample. */
    public InterfaceMonitorEntry(
            RouterOsRecord raw,
            Optional<String> name,
            OptionalLong rxPacketsPerSecond,
            OptionalLong txPacketsPerSecond,
            OptionalLong rxBitsPerSecond,
            OptionalLong txBitsPerSecond,
            OptionalLong fpRxPacketsPerSecond,
            OptionalLong fpTxPacketsPerSecond,
            OptionalLong fpRxBitsPerSecond,
            OptionalLong fpTxBitsPerSecond,
            OptionalLong rxDropsPerSecond,
            OptionalLong txDropsPerSecond,
            OptionalLong rxErrorsPerSecond,
            OptionalLong txErrorsPerSecond,
            OptionalLong txQueueDropsPerSecond) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.name = Objects.requireNonNull(name, "name");
        this.rxPacketsPerSecond = Objects.requireNonNull(rxPacketsPerSecond, "rxPacketsPerSecond");
        this.txPacketsPerSecond = Objects.requireNonNull(txPacketsPerSecond, "txPacketsPerSecond");
        this.rxBitsPerSecond = Objects.requireNonNull(rxBitsPerSecond, "rxBitsPerSecond");
        this.txBitsPerSecond = Objects.requireNonNull(txBitsPerSecond, "txBitsPerSecond");
        this.fpRxPacketsPerSecond = Objects.requireNonNull(fpRxPacketsPerSecond, "fpRxPacketsPerSecond");
        this.fpTxPacketsPerSecond = Objects.requireNonNull(fpTxPacketsPerSecond, "fpTxPacketsPerSecond");
        this.fpRxBitsPerSecond = Objects.requireNonNull(fpRxBitsPerSecond, "fpRxBitsPerSecond");
        this.fpTxBitsPerSecond = Objects.requireNonNull(fpTxBitsPerSecond, "fpTxBitsPerSecond");
        this.rxDropsPerSecond = Objects.requireNonNull(rxDropsPerSecond, "rxDropsPerSecond");
        this.txDropsPerSecond = Objects.requireNonNull(txDropsPerSecond, "txDropsPerSecond");
        this.rxErrorsPerSecond = Objects.requireNonNull(rxErrorsPerSecond, "rxErrorsPerSecond");
        this.txErrorsPerSecond = Objects.requireNonNull(txErrorsPerSecond, "txErrorsPerSecond");
        this.txQueueDropsPerSecond = Objects.requireNonNull(txQueueDropsPerSecond, "txQueueDropsPerSecond");
    }

    public Optional<String> name() { return name; }
    public OptionalLong rxPacketsPerSecond() { return rxPacketsPerSecond; }
    public OptionalLong txPacketsPerSecond() { return txPacketsPerSecond; }
    public OptionalLong rxBitsPerSecond() { return rxBitsPerSecond; }
    public OptionalLong txBitsPerSecond() { return txBitsPerSecond; }
    public OptionalLong fpRxPacketsPerSecond() { return fpRxPacketsPerSecond; }
    public OptionalLong fpTxPacketsPerSecond() { return fpTxPacketsPerSecond; }
    public OptionalLong fpRxBitsPerSecond() { return fpRxBitsPerSecond; }
    public OptionalLong fpTxBitsPerSecond() { return fpTxBitsPerSecond; }
    public OptionalLong rxDropsPerSecond() { return rxDropsPerSecond; }
    public OptionalLong txDropsPerSecond() { return txDropsPerSecond; }
    public OptionalLong rxErrorsPerSecond() { return rxErrorsPerSecond; }
    public OptionalLong txErrorsPerSecond() { return txErrorsPerSecond; }
    public OptionalLong txQueueDropsPerSecond() { return txQueueDropsPerSecond; }

    @Override
    public RouterOsRecord raw() { return raw; }
}
