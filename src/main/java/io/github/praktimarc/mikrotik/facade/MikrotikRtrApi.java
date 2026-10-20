package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.async.AsyncMikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.ip.IpApi;
import io.github.praktimarc.mikrotik.facade.ip.AsyncIpApi;
import io.github.praktimarc.mikrotik.facade.ping.PingApi;
import io.github.praktimarc.mikrotik.facade.ping.AsyncPingApi;
import io.github.praktimarc.mikrotik.facade.ping.PingRequest;
import io.github.praktimarc.mikrotik.facade.ping.PingResult;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.file.AsyncFileApi;
import io.github.praktimarc.mikrotik.facade.file.FileApi;
import io.github.praktimarc.mikrotik.facade.file.internal.FileDownloadExecutor;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.log.LogApi;
import io.github.praktimarc.mikrotik.facade.interfaces.AsyncInterfacesApi;
import io.github.praktimarc.mikrotik.facade.interfaces.InterfacesApi;
import io.github.praktimarc.mikrotik.facade.internal.capability.CapabilityRegistry;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.internal.stream.StreamRegistry;
import io.github.praktimarc.mikrotik.facade.internal.stream.RawWordStreams;
import io.github.praktimarc.mikrotik.facade.queue.AsyncQueueApi;
import io.github.praktimarc.mikrotik.facade.queue.QueueApi;
import io.github.praktimarc.mikrotik.facade.raw.AsyncRawApi;
import io.github.praktimarc.mikrotik.facade.raw.RawApi;
import io.github.praktimarc.mikrotik.facade.snmp.AsyncSnmpApi;
import io.github.praktimarc.mikrotik.facade.snmp.SnmpApi;
import io.github.praktimarc.mikrotik.facade.wifi.AsyncWifiApi;
import io.github.praktimarc.mikrotik.facade.wifi.WifiApi;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Entry point for one authenticated RouterOS facade session. */
public final class MikrotikRtrApi implements AutoCloseable {
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(60);
    private static final AtomicInteger SESSION_SEQUENCE = new AtomicInteger();
    private final ApiConnection connection;
    private final SessionLifecycle lifecycle;
    private final RouterOsEnvironment environment;
    private final Executor configuredCallbackExecutor;
    private final ExecutorService dispatchExecutor;
    private final ScheduledExecutorService timeoutScheduler;
    private final Executor callbackExecutor;
    private final ExecutorService ownedCallbackExecutor;
    private final CommandEngine commandEngine;
    private final FacadeDiagnostics diagnostics;
    private final CapabilityRegistry capabilityRegistry;
    private final StreamRegistry streamRegistry;
    private final RawWordStreams rawWordStreams;
    private final FileDownloadExecutor fileDownloadExecutor;
    private final RawApi raw;
    private final LogApi log;
    private final IpApi ip;
    private final WifiApi wifi;
    private final InterfacesApi interfaces;
    private final QueueApi queue;
    private final SnmpApi snmp;
    private final PingApi pingApi;
    private final FileApi file;
    private final AsyncMikrotikRtrApi async;

    MikrotikRtrApi(ApiConnection connection, SessionLifecycle lifecycle, RouterOsEnvironment environment, Executor configuredCallbackExecutor) {
        this(connection,lifecycle,environment,configuredCallbackExecutor,DEFAULT_COMMAND_TIMEOUT);
    }

    MikrotikRtrApi(ApiConnection connection, SessionLifecycle lifecycle, RouterOsEnvironment environment, Executor configuredCallbackExecutor, Duration commandTimeout) {
        this(connection,lifecycle,environment,configuredCallbackExecutor,commandTimeout,null);
    }

    MikrotikRtrApi(
            ApiConnection connection,
            SessionLifecycle lifecycle,
            RouterOsEnvironment environment,
            Executor configuredCallbackExecutor,
            Duration commandTimeout,
            FacadeDiagnostics diagnosticsOverride) {
        this.connection=Objects.requireNonNull(connection,"connection");
        this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
        this.environment=Objects.requireNonNull(environment,"environment");
        this.configuredCallbackExecutor=configuredCallbackExecutor;
        Objects.requireNonNull(commandTimeout,"commandTimeout");
        int session=SESSION_SEQUENCE.incrementAndGet();
        this.diagnostics=diagnosticsOverride==null
                ? FacadeDiagnostics.slf4j("session-"+session)
                : diagnosticsOverride;
        this.lifecycle.attachDiagnostics(diagnostics);
        this.dispatchExecutor=Executors.newSingleThreadExecutor(daemonThreadFactory("mikrotik-facade-dispatch-"+session));
        this.timeoutScheduler=Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("mikrotik-facade-timeout-"+session));
        if(configuredCallbackExecutor==null){
            this.ownedCallbackExecutor=Executors.newSingleThreadExecutor(daemonThreadFactory("mikrotik-facade-callback-"+session));
            this.callbackExecutor=ownedCallbackExecutor;
        } else {
            this.ownedCallbackExecutor=null;
            this.callbackExecutor=configuredCallbackExecutor;
        }
        this.commandEngine=new CommandEngine(connection,commandTimeout,dispatchExecutor,callbackExecutor,timeoutScheduler,diagnostics);
        this.fileDownloadExecutor=new FileDownloadExecutor(callbackExecutor,daemonThreadFactory("mikrotik-facade-download-"+session));
        this.capabilityRegistry=new CapabilityRegistry(diagnostics);
        this.streamRegistry=new StreamRegistry();
        this.rawWordStreams=new RawWordStreams(
                connection,lifecycle,dispatchExecutor,callbackExecutor,streamRegistry);
        this.raw=new RawApi(commandEngine,lifecycle,rawWordStreams);
        this.log=new LogApi(rawWordStreams);
        this.ip=new IpApi(commandEngine,lifecycle);
        this.wifi=new WifiApi(commandEngine,lifecycle,environment,capabilityRegistry);
        this.interfaces=new InterfacesApi(connection,commandEngine,lifecycle,dispatchExecutor,callbackExecutor,streamRegistry);
        this.queue=new QueueApi(commandEngine,lifecycle);
        this.snmp=new SnmpApi(commandEngine,lifecycle);
        this.pingApi=new PingApi(commandEngine,lifecycle);
        this.file=new FileApi(connection,commandEngine,lifecycle);
        this.async=new AsyncMikrotikRtrApi(
                new AsyncRawApi(commandEngine,lifecycle,callbackExecutor,rawWordStreams),
                new AsyncIpApi(commandEngine,lifecycle,callbackExecutor),
                new AsyncWifiApi(commandEngine,lifecycle,callbackExecutor,environment,capabilityRegistry),
                new AsyncInterfacesApi(commandEngine,lifecycle,callbackExecutor),
                new AsyncQueueApi(commandEngine,lifecycle,callbackExecutor),
                new AsyncSnmpApi(commandEngine,lifecycle,callbackExecutor),
                new AsyncPingApi(commandEngine,lifecycle,callbackExecutor),
                new AsyncFileApi(connection,commandEngine,lifecycle,callbackExecutor,fileDownloadExecutor));
        this.diagnostics.sessionReady();
    }

    public static MikrotikRtrApiBuilder builder(){return new MikrotikRtrApiBuilder();}
    public RouterOsEnvironment environment(){return environment;}
    public RawApi raw(){return raw;}
    /** Returns the typed RouterOS live log streaming API. */
    public LogApi log(){return log;}
    /** Returns the RouterOS /ip API. */
    public IpApi ip(){return ip;}
    /** Returns the compatibility-aware typed WiFi/CAPsMAN API.
     * @return typed WiFi API
     */
    public WifiApi wifi(){return wifi;}
    /** Returns the typed interface API and traffic monitoring entry point. */
    public InterfacesApi interfaces(){return interfaces;}
    /** Returns the typed queue API. */
    public QueueApi queue(){return queue;}
    /** Returns the typed SNMP API. */
    public SnmpApi snmp(){return snmp;}
    /** Executes finite router-originated /ping. */
    public PingResult ping(PingRequest request) throws MikrotikFacadeException {
        return pingApi.ping(request);
    }
    /** Returns the RouterOS /file API. */
    public FileApi file(){return file;}
    public AsyncMikrotikRtrApi async(){return async;}

    @Override public void close() throws MikrotikConnectionException {
        if(!lifecycle.beginClose()) return;
        diagnostics.sessionClosing();
        MikrotikConnectionException closeFailure=null;
        fileDownloadExecutor.beginClose();
        streamRegistry.cancelActive();
        commandEngine.cancelActive();
        connection.removeConnectionListener(lifecycle);
        try{connection.close();}catch(ApiConnectionException exception){diagnostics.closeFailure(exception); closeFailure=new MikrotikConnectionException("Unable to close RouterOS connection cleanly",exception);}finally{
            fileDownloadExecutor.finishClose(); drainDispatch(); timeoutScheduler.shutdown(); dispatchExecutor.shutdown(); if(ownedCallbackExecutor!=null)ownedCallbackExecutor.shutdown(); lifecycle.finishClose(); diagnostics.sessionClosed();
        }
        if(closeFailure!=null) throw closeFailure;
    }
    SessionLifecycle lifecycle(){return lifecycle;}
    Executor configuredCallbackExecutor(){return configuredCallbackExecutor;}
    private void drainDispatch(){try{Future<?> barrier=dispatchExecutor.submit(()->{});barrier.get(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException|RejectedExecutionException ignored){}}
    private static ThreadFactory daemonThreadFactory(String baseName){AtomicInteger sequence=new AtomicInteger();return task->{Thread thread=new Thread(task,baseName+'-'+sequence.incrementAndGet());thread.setDaemon(true);return thread;};}
}
