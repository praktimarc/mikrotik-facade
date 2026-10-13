package io.github.praktimarc.mikrotik.facade.async;

import io.github.praktimarc.mikrotik.facade.file.AsyncFileApi;
import io.github.praktimarc.mikrotik.facade.interfaces.AsyncInterfacesApi;
import io.github.praktimarc.mikrotik.facade.ip.AsyncIpApi;
import io.github.praktimarc.mikrotik.facade.ping.AsyncPingApi;
import io.github.praktimarc.mikrotik.facade.ping.PingRequest;
import io.github.praktimarc.mikrotik.facade.ping.PingResult;
import io.github.praktimarc.mikrotik.facade.queue.AsyncQueueApi;
import io.github.praktimarc.mikrotik.facade.raw.AsyncRawApi;
import io.github.praktimarc.mikrotik.facade.snmp.AsyncSnmpApi;
import io.github.praktimarc.mikrotik.facade.wifi.AsyncWifiApi;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Mirrored asynchronous facade tree for one RouterOS session. */
public final class AsyncMikrotikRtrApi {
    private final AsyncRawApi raw;
    private final AsyncIpApi ip;
    private final AsyncWifiApi wifi;
    private final AsyncInterfacesApi interfaces;
    private final AsyncQueueApi queue;
    private final AsyncSnmpApi snmp;
    private final AsyncPingApi pingApi;
    private final AsyncFileApi file;

    public AsyncMikrotikRtrApi(
            AsyncRawApi raw,
            AsyncIpApi ip,
            AsyncWifiApi wifi,
            AsyncInterfacesApi interfaces,
            AsyncQueueApi queue,
            AsyncSnmpApi snmp,
            AsyncPingApi pingApi,
            AsyncFileApi file) {
        this.raw = Objects.requireNonNull(raw);
        this.ip = Objects.requireNonNull(ip);
        this.wifi = Objects.requireNonNull(wifi);
        this.interfaces = Objects.requireNonNull(interfaces);
        this.queue = Objects.requireNonNull(queue);
        this.snmp = Objects.requireNonNull(snmp);
        this.pingApi = Objects.requireNonNull(pingApi);
        this.file = Objects.requireNonNull(file);
    }

    public AsyncRawApi raw() { return raw; }
    public AsyncIpApi ip() { return ip; }
    public AsyncWifiApi wifi() { return wifi; }
    public AsyncInterfacesApi interfaces() { return interfaces; }
    public AsyncQueueApi queue() { return queue; }
    public AsyncSnmpApi snmp() { return snmp; }
    public CompletableFuture<PingResult> ping(PingRequest request) { return pingApi.ping(request); }
    public AsyncFileApi file() { return file; }
}
