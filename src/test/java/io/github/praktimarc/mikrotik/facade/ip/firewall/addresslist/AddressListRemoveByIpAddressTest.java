package io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AddressListRemoveByIpAddressTest {
    private static final String ADDRESS = "192.0.2.10";
    private static final String PRINT = "/ip/firewall/address-list/print";

    @Test
    void synchronousUniqueMatchUsesUnfilteredReadAndRemovesExactRouterOsId() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*B", "other", "192.0.2.11"),
                    row("*A", "blocked", ADDRESS));
            assertTrue(f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*A'"),
                    f.connection.commands);
        }
    }

    @Test
    void noMatchIsNoOpIncludingWhenOnlyContainingCidrExists() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "blocked", "192.0.2.0/24"));
            assertFalse(f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void sameIpInTwoListsOrRepeatedInOneListIsAmbiguousAndNotDeleted() throws Exception {
        for (String secondList : List.of("allowed", "blocked")) {
            try (Fixture f = new Fixture()) {
                f.connection.rows = List.of(
                        row("*A", "blocked", ADDRESS),
                        row("*B", secondList, ADDRESS));
                assertThrows(MikrotikDataException.class,
                        () -> f.sync.removeByIpAddress(ADDRESS));
                assertEquals(List.of(PRINT), f.connection.commands);
            }
        }
    }

    @Test
    void matchingRowWithoutIdFailsClosedInsteadOfSendingRemove() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(Map.of("list", "blocked", "address", ADDRESS));
            assertThrows(MikrotikDataException.class,
                    () -> f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void blankOrNullAddressIsRejectedWithoutRouterOsCalls() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThrows(IllegalArgumentException.class,
                    () -> f.sync.removeByIpAddress(" "));
            assertThrows(NullPointerException.class,
                    () -> f.sync.removeByIpAddress(null));
            assertThrows(IllegalArgumentException.class,
                    () -> f.async.removeByIpAddress("\t"));
            assertThrows(NullPointerException.class,
                    () -> f.async.removeByIpAddress(null));
            assertTrue(f.connection.commands.isEmpty());
        }
    }

    @Test
    void asynchronousUniqueMatchAndAbsenceReturnTrueAndFalse() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*E", "blocked", ADDRESS));
            assertTrue(f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*E'"),
                    f.connection.commands);
        }
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of();
            assertFalse(f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void asyncDuplicateCompletesExceptionallyWithoutRemoval() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "first", ADDRESS),
                    row("*B", "second", ADDRESS));
            var future = f.async.removeByIpAddress(ADDRESS);
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> future.get(3, TimeUnit.SECONDS));
            assertInstanceOf(MikrotikDataException.class, error.getCause());
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void cancellationDuringPendingLookupNeverStartsRemoval() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "blocked", ADDRESS));
            f.connection.deferPrint = true;
            var result = f.async.removeByIpAddress(ADDRESS);
            assertEquals(List.of(PRINT), f.connection.commands);
            assertNotNull(f.connection.pendingPrint);
            assertTrue(result.cancel(true));
            assertTrue(result.isCancelled());
            assertEquals(1, f.connection.cancellations);
            f.connection.pendingPrint.receive(row("*A", "blocked", ADDRESS));
            f.connection.pendingPrint.completed();
            assertEquals(List.of(PRINT), f.connection.commands);
            assertThrows(CancellationException.class, result::join);
        }
    }

    private static Map<String, String> row(String id, String list, String address) {
        return Map.of(".id", id, "list", list, "address", address);
    }

    private static final class Fixture implements AutoCloseable {
        final SimulatedConnection connection = new SimulatedConnection();
        final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        final CommandEngine engine = new CommandEngine(
                connection, Duration.ofSeconds(10), Runnable::run, Runnable::run, timer);
        final SessionLifecycle lifecycle = new SessionLifecycle();
        final AddressListApi sync = new AddressListApi(engine, lifecycle);
        final AsyncAddressListApi async = new AsyncAddressListApi(engine, lifecycle, Runnable::run);

        @Override public void close() {
            engine.cancelActive();
            timer.shutdownNow();
        }
    }

    private static final class SimulatedConnection extends StubApiConnection {
        volatile List<Map<String, String>> rows = List.of();
        final java.util.List<String> commands = new java.util.ArrayList<>();
        volatile boolean deferPrint;
        volatile ResultListener pendingPrint;
        int cancellations;
        int nextTag;

        @Override
        public String execute(String command, ResultListener listener) {
            commands.add(command);
            String tag = "test-" + (++nextTag);
            if (command.equals(PRINT)) {
                if (deferPrint) {
                    pendingPrint = listener;
                } else {
                    rows.forEach(listener::receive);
                    listener.completed();
                }
            } else if (command.startsWith("/ip/firewall/address-list/remove ")) {
                listener.completed();
            } else {
                throw new AssertionError("Unexpected RouterOS command: " + command);
            }
            return tag;
        }

        @Override
        public void cancel(String tag) {
            cancellations++;
        }
    }
}
