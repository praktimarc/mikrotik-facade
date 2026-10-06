package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.async.AsyncMikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.dhcp.AsyncDhcpServerApi;
import io.github.praktimarc.mikrotik.facade.dhcp.DhcpServerApi;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.raw.AsyncRawApi;
import io.github.praktimarc.mikrotik.facade.raw.RawApi;
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
    private final RawApi raw;
    private final DhcpServerApi dhcpServer;
    private final AsyncMikrotikRtrApi async;

    MikrotikRtrApi(ApiConnection connection, SessionLifecycle lifecycle, RouterOsEnvironment environment, Executor configuredCallbackExecutor) {
        this(connection,lifecycle,environment,configuredCallbackExecutor,DEFAULT_COMMAND_TIMEOUT);
    }

    MikrotikRtrApi(ApiConnection connection, SessionLifecycle lifecycle, RouterOsEnvironment environment, Executor configuredCallbackExecutor, Duration commandTimeout) {
        this.connection=Objects.requireNonNull(connection,"connection");
        this.lifecycle=Objects.requireNonNull(lifecycle,"lifecycle");
        this.environment=Objects.requireNonNull(environment,"environment");
        this.configuredCallbackExecutor=configuredCallbackExecutor;
        Objects.requireNonNull(commandTimeout,"commandTimeout");
        int session=SESSION_SEQUENCE.incrementAndGet();
        this.dispatchExecutor=Executors.newSingleThreadExecutor(daemonThreadFactory("mikrotik-facade-dispatch-"+session));
        this.timeoutScheduler=Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("mikrotik-facade-timeout-"+session));
        if(configuredCallbackExecutor==null){
            this.ownedCallbackExecutor=Executors.newSingleThreadExecutor(daemonThreadFactory("mikrotik-facade-callback-"+session));
            this.callbackExecutor=ownedCallbackExecutor;
        } else {
            this.ownedCallbackExecutor=null;
            this.callbackExecutor=configuredCallbackExecutor;
        }
        this.commandEngine=new CommandEngine(connection,commandTimeout,dispatchExecutor,callbackExecutor,timeoutScheduler);
        this.raw=new RawApi(commandEngine,lifecycle);
        this.dhcpServer=new DhcpServerApi(commandEngine,lifecycle);
        this.async=new AsyncMikrotikRtrApi(
                new AsyncRawApi(commandEngine,lifecycle,callbackExecutor),
                new AsyncDhcpServerApi(commandEngine,lifecycle,callbackExecutor));
    }

    public static MikrotikRtrApiBuilder builder(){return new MikrotikRtrApiBuilder();}
    public RouterOsEnvironment environment(){return environment;}
    public RawApi raw(){return raw;}
    public DhcpServerApi dhcpServer(){return dhcpServer;}
    public AsyncMikrotikRtrApi async(){return async;}

    @Override public void close() throws MikrotikConnectionException {
        if(!lifecycle.beginClose()) return;
        MikrotikConnectionException closeFailure=null;
        commandEngine.cancelActive();
        connection.removeConnectionListener(lifecycle);
        try{connection.close();}catch(ApiConnectionException exception){closeFailure=new MikrotikConnectionException("Unable to close RouterOS connection cleanly",exception);}finally{
            drainDispatch(); timeoutScheduler.shutdown(); dispatchExecutor.shutdown(); if(ownedCallbackExecutor!=null)ownedCallbackExecutor.shutdown(); lifecycle.finishClose();
        }
        if(closeFailure!=null) throw closeFailure;
    }
    SessionLifecycle lifecycle(){return lifecycle;}
    Executor configuredCallbackExecutor(){return configuredCallbackExecutor;}
    private void drainDispatch(){try{Future<?> barrier=dispatchExecutor.submit(()->{});barrier.get(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException|RejectedExecutionException ignored){}}
    private static ThreadFactory daemonThreadFactory(String baseName){AtomicInteger sequence=new AtomicInteger();return task->{Thread thread=new Thread(task,baseName+'-'+sequence.incrementAndGet());thread.setDaemon(true);return thread;};}
}
