package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.interfaces.internal.InterfaceAddressMapper;
import io.github.praktimarc.mikrotik.facade.interfaces.internal.InterfaceInfoMapper;
import io.github.praktimarc.mikrotik.facade.interfaces.internal.InterfaceMonitorMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.internal.stream.RouterOsPublisher;
import io.github.praktimarc.mikrotik.facade.internal.stream.StreamRegistry;
import me.legrange.mikrotik.ApiConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

/** Synchronous typed interface and IP-address facade plus streaming traffic monitoring. */
public final class InterfacesApi {
    static final String INTERFACE_PATH = "/interface/print";
    static final String ADDRESS_PATH = "/ip/address/print";
    static final String MONITOR_PATH = "/interface/monitor-traffic";
    static final int MONITOR_QUEUE_CAPACITY = 64;

    private final ApiConnection connection;
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final Executor dispatchExecutor;
    private final Executor callbackExecutor;
    private final StreamRegistry streamRegistry;
    private final InterfaceInfoMapper interfaceMapper;
    private final InterfaceAddressMapper addressMapper;
    private final InterfaceMonitorMapper monitorMapper;

    /** Creates the interface facade for session wiring. */
    public InterfacesApi(
            ApiConnection connection,
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor dispatchExecutor,
            Executor callbackExecutor,
            StreamRegistry streamRegistry) {
        this(connection, engine, lifecycle, dispatchExecutor, callbackExecutor, streamRegistry,
                new InterfaceInfoMapper(), new InterfaceAddressMapper(), new InterfaceMonitorMapper());
    }

    InterfacesApi(
            ApiConnection connection,
            CommandEngine engine,
            SessionLifecycle lifecycle,
            Executor dispatchExecutor,
            Executor callbackExecutor,
            StreamRegistry streamRegistry,
            InterfaceInfoMapper interfaceMapper,
            InterfaceAddressMapper addressMapper,
            InterfaceMonitorMapper monitorMapper) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.dispatchExecutor = Objects.requireNonNull(dispatchExecutor, "dispatchExecutor");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.streamRegistry = Objects.requireNonNull(streamRegistry, "streamRegistry");
        this.interfaceMapper = Objects.requireNonNull(interfaceMapper, "interfaceMapper");
        this.addressMapper = Objects.requireNonNull(addressMapper, "addressMapper");
        this.monitorMapper = Objects.requireNonNull(monitorMapper, "monitorMapper");
    }

    /** Lists all generic RouterOS interfaces. */
    public List<InterfaceInfo> list() throws MikrotikFacadeException {
        return list(emptyProperties());
    }

    /** Lists interfaces matching exact RouterOS properties. */
    public List<InterfaceInfo> list(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(listOperation(queries, interfaceMapper));
    }

    /** Lists all RouterOS IP address assignments. */
    public List<InterfaceAddress> addresses() throws MikrotikFacadeException {
        return addresses(emptyProperties());
    }

    /** Lists IP address assignments matching exact RouterOS properties. */
    public List<InterfaceAddress> addresses(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(addressesOperation(queries, addressMapper));
    }

    /**
     * Creates a cold continuous traffic monitor for one exact interface name.
     * RouterOS I/O starts only when the returned publisher is subscribed.
     */
    public Flow.Publisher<InterfaceMonitorEntry> monitor(String interfaceName)
            throws MikrotikConnectionException {
        lifecycle.ensureOpen();
        String name = requireNonBlank(interfaceName, "interfaceName");
        RouterOsCommand command = RouterOsCommand.builder(MONITOR_PATH)
                .argument("interface", name)
                .build();
        RouterOsPublisher<InterfaceMonitorEntry> delegate = new RouterOsPublisher<>(
                connection,
                "monitor interface traffic",
                command,
                MONITOR_QUEUE_CAPACITY,
                dispatchExecutor,
                callbackExecutor,
                monitorMapper::map);
        return subscriber -> delegate.subscribe(new RegisteredSubscriber<>(subscriber, streamRegistry));
    }

    static RouterOsOperation<List<InterfaceInfo>> listOperation(
            RouterOsProperties queries,
            InterfaceInfoMapper mapper) {
        RouterOsCommand command = commandWithQueries(INTERFACE_PATH, queries);
        return listOperation("list interfaces", command, mapper::map);
    }

    static RouterOsOperation<List<InterfaceAddress>> addressesOperation(
            RouterOsProperties queries,
            InterfaceAddressMapper mapper) {
        RouterOsCommand command = commandWithQueries(ADDRESS_PATH, queries);
        return listOperation("list interface addresses", command, mapper::map);
    }

    private static RouterOsCommand commandWithQueries(String path, RouterOsProperties queries) {
        Objects.requireNonNull(queries, "queries");
        RouterOsCommand.Builder builder = RouterOsCommand.builder(path);
        queries.asMap().forEach(builder::query);
        return builder.build();
    }

    private static <T> RouterOsOperation<List<T>> listOperation(
            String name,
            RouterOsCommand command,
            RecordMapper<T> mapper) {
        return new RouterOsOperation<>() {
            @Override
            public String name() { return name; }

            @Override
            public RouterOsCommand command() { return command; }

            @Override
            public List<T> map(CommandResult result) throws MikrotikFacadeException {
                ArrayList<T> mapped = new ArrayList<>(result.records().size());
                for (RouterOsRecord record : result.records()) {
                    mapped.add(mapper.map(record));
                }
                return List.copyOf(mapped);
            }
        };
    }

    private static RouterOsProperties emptyProperties() {
        return RouterOsProperties.builder().build();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static final class RegisteredSubscriber<T> implements Flow.Subscriber<T> {
        private final Flow.Subscriber<? super T> target;
        private final StreamRegistry registry;
        private RegisteredSubscription subscription;

        private RegisteredSubscriber(Flow.Subscriber<? super T> target, StreamRegistry registry) {
            this.target = Objects.requireNonNull(target, "target");
            this.registry = Objects.requireNonNull(registry, "registry");
        }

        @Override
        public void onSubscribe(Flow.Subscription upstream) {
            RegisteredSubscription registered = new RegisteredSubscription(upstream, registry);
            this.subscription = registered;
            registry.register(registered);
            try {
                target.onSubscribe(registered);
            } catch (RuntimeException | Error callbackFailure) {
                registered.cancel();
                throw callbackFailure;
            }
        }

        @Override
        public void onNext(T item) {
            try {
                target.onNext(item);
            } catch (RuntimeException | Error callbackFailure) {
                RegisteredSubscription current = subscription;
                if (current != null) {
                    current.cancel();
                }
                throw callbackFailure;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            unregister();
            target.onError(throwable);
        }

        @Override
        public void onComplete() {
            unregister();
            target.onComplete();
        }

        private void unregister() {
            RegisteredSubscription current = subscription;
            if (current != null) {
                registry.unregister(current);
            }
        }
    }

    private static final class RegisteredSubscription implements Flow.Subscription {
        private final Flow.Subscription upstream;
        private final StreamRegistry registry;
        private boolean cancelled;

        private RegisteredSubscription(Flow.Subscription upstream, StreamRegistry registry) {
            this.upstream = Objects.requireNonNull(upstream, "upstream");
            this.registry = Objects.requireNonNull(registry, "registry");
        }

        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public synchronized void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            registry.unregister(this);
            upstream.cancel();
        }
    }

    @FunctionalInterface
    private interface RecordMapper<T> {
        T map(RouterOsRecord record) throws MikrotikFacadeException;
    }
}
