package io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.ResultListener;
import me.legrange.mikrotik.MikrotikApiException;
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
            assertEquals(1, f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*A'"),
                    f.connection.commands);
        }
    }

    @Test
    void noMatchIsNoOpIncludingWhenOnlyContainingCidrExists() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "blocked", "192.0.2.0/24"));
            assertEquals(0, f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void removesAllMatchingAddressesAcrossListsAndWithinOneList() throws Exception {
        for (String secondList : List.of("allowed", "blocked")) {
            try (Fixture f = new Fixture()) {
                f.connection.rows = List.of(
                        row("*A", "blocked", ADDRESS),
                        row("*X", "different", "198.51.100.25"),
                        row("*B", secondList, ADDRESS),
                        row("*C", "third", ADDRESS));
                assertEquals(3, f.sync.removeByIpAddress(ADDRESS));
                assertEquals(List.of(
                        PRINT,
                        "/ip/firewall/address-list/remove .id='*A'",
                        "/ip/firewall/address-list/remove .id='*B'",
                        "/ip/firewall/address-list/remove .id='*C'"),
                        f.connection.commands);
            }
        }
    }

    @Test
    void repeatedIdInMatchingEntriesFailsBeforeAnyRemoval() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "blocked", ADDRESS), row("*A", "other", ADDRESS));
            assertThrows(MikrotikDataException.class,
                    () -> f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void anyMatchingRowWithoutIdFailsBeforeAllRemovals() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "first", ADDRESS),
                    Map.of("list", "blocked", "address", ADDRESS),
                    row("*B", "third", ADDRESS));
            assertThrows(MikrotikDataException.class,
                    () -> f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void failedRemovalStopsFurtherCommandsAfterEarlierSuccessfulRemoves() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "first", ADDRESS),
                    row("*B", "second", ADDRESS),
                    row("*C", "third", ADDRESS));
            f.connection.failRemoveId = "*B";
            assertThrows(Exception.class, () -> f.sync.removeByIpAddress(ADDRESS));
            assertEquals(List.of(PRINT,
                    "/ip/firewall/address-list/remove .id='*A'",
                    "/ip/firewall/address-list/remove .id='*B'"), f.connection.commands);
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
    void asynchronousUniqueMatchAndAbsenceReturnOneAndZero() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*E", "blocked", ADDRESS));
            assertEquals(1, f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*E'"),
                    f.connection.commands);
        }
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of();
            assertEquals(0, f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void asynchronousRemovalDeletesEveryMatchingEntryInSequence() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "first", ADDRESS),
                    row("*X", "not-matched", "192.0.2.11"),
                    row("*B", "second", ADDRESS));
            assertEquals(2, f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT,
                    "/ip/firewall/address-list/remove .id='*A'",
                    "/ip/firewall/address-list/remove .id='*B'"), f.connection.commands);
        }
    }

    @Test
    void asynchronousMissingIdOrRepeatedIdRejectsBeforeDeletion() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "first", ADDRESS),
                    Map.of("address", ADDRESS, "list", "second"));
            var e = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertInstanceOf(MikrotikDataException.class, e.getCause());
            assertEquals(List.of(PRINT), f.connection.commands);
        }
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "first", ADDRESS),
                    row("*A", "second", ADDRESS));
            var e = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertInstanceOf(MikrotikDataException.class, e.getCause());
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void asynchronousFailureDoesNotScheduleSubsequentRemovals() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "first", ADDRESS),
                    row("*B", "second", ADDRESS), row("*C", "third", ADDRESS));
            f.connection.failRemoveId = "*B";
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> f.async.removeByIpAddress(ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT,
                    "/ip/firewall/address-list/remove .id='*A'",
                    "/ip/firewall/address-list/remove .id='*B'"), f.connection.commands);
        }
    }

    @Test
    void cancellingPendingFirstRemoveDoesNotScheduleSubsequentRemovals() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "first", ADDRESS),
                    row("*B", "second", ADDRESS));
            f.connection.deferRemoveId = "*A";
            var future = f.async.removeByIpAddress(ADDRESS);
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*A'"),
                    f.connection.commands);
            assertNotNull(f.connection.pendingRemove);
            assertTrue(future.cancel(true));
            assertTrue(future.isCancelled());
            f.connection.pendingRemove.completed();
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*A'"),
                    f.connection.commands);
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


    @Test
    void removeByListAndAddressOnlyAffectsSpecifiedListIncludingDuplicateRows() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    row("*B", "other", ADDRESS),
                    row("*C", "target", ADDRESS),
                    row("*D", "target", "192.0.2.11"),
                    row("*E", "TARGET", ADDRESS));
            assertEquals(2, f.sync.removeByListAndAddress("target", ADDRESS));
            assertEquals(List.of(PRINT,
                    "/ip/firewall/address-list/remove .id='*A'",
                    "/ip/firewall/address-list/remove .id='*C'"), f.connection.commands);
        }
    }

    @Test
    void removeByListAndAddressReturnsZeroWhenNoExactListAddressPairExists() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "other", ADDRESS),
                    row("*B", "target", "192.0.2.0/24"),
                    row("*C", "TARGET", ADDRESS));
            assertEquals(0, f.sync.removeByListAndAddress("target", ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void removeByListAndAddressRejectsInvalidArgumentsWithoutRouterCommunication() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThrows(NullPointerException.class,
                    () -> f.sync.removeByListAndAddress(null, ADDRESS));
            assertThrows(IllegalArgumentException.class,
                    () -> f.sync.removeByListAndAddress("  ", ADDRESS));
            assertThrows(NullPointerException.class,
                    () -> f.sync.removeByListAndAddress("target", null));
            assertThrows(IllegalArgumentException.class,
                    () -> f.sync.removeByListAndAddress("target", "\t"));
            assertThrows(NullPointerException.class,
                    () -> f.async.removeByListAndAddress(null, ADDRESS));
            assertThrows(IllegalArgumentException.class,
                    () -> f.async.removeByListAndAddress(" ", ADDRESS));
            assertThrows(NullPointerException.class,
                    () -> f.async.removeByListAndAddress("target", null));
            assertThrows(IllegalArgumentException.class,
                    () -> f.async.removeByListAndAddress("target", " "));
            assertTrue(f.connection.commands.isEmpty());
        }
    }

    @Test
    void removeByListAndAddressPrevalidatesSelectedIdsButIgnoresUnselectedInvalidRows() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    Map.of("list", "other", "address", ADDRESS));
            assertEquals(1, f.sync.removeByListAndAddress("target", ADDRESS));
            assertEquals(List.of(PRINT,
                    "/ip/firewall/address-list/remove .id='*A'"), f.connection.commands);
        }
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    Map.of("list", "target", "address", ADDRESS),
                    row("*B", "other", ADDRESS));
            assertThrows(MikrotikDataException.class,
                    () -> f.sync.removeByListAndAddress("target", ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    row("*A", "target", ADDRESS));
            assertThrows(MikrotikDataException.class,
                    () -> f.sync.removeByListAndAddress("target", ADDRESS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void asynchronousRemoveByListAndAddressRemovesEveryMatchInSelectedListOnly() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    row("*B", "other", ADDRESS),
                    row("*C", "target", ADDRESS));
            assertEquals(2, f.async.removeByListAndAddress("target", ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT,
                    "/ip/firewall/address-list/remove .id='*A'",
                    "/ip/firewall/address-list/remove .id='*C'"), f.connection.commands);
        }
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(row("*A", "other", ADDRESS));
            assertEquals(0, f.async.removeByListAndAddress("target", ADDRESS).get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT), f.connection.commands);
        }
    }

    @Test
    void asynchronousRemoveByListAndAddressRejectsMissingIdsAndStopsOnRemovalFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    Map.of("list", "target", "address", ADDRESS));
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> f.async.removeByListAndAddress("target", ADDRESS)
                            .get(3, TimeUnit.SECONDS));
            assertInstanceOf(MikrotikDataException.class, failure.getCause());
            assertEquals(List.of(PRINT), f.connection.commands);
        }
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    row("*B", "target", ADDRESS),
                    row("*C", "target", ADDRESS));
            f.connection.failRemoveId = "*B";
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> f.async.removeByListAndAddress("target", ADDRESS)
                            .get(3, TimeUnit.SECONDS));
            assertEquals(List.of(PRINT,
                    "/ip/firewall/address-list/remove .id='*A'",
                    "/ip/firewall/address-list/remove .id='*B'"), f.connection.commands);
        }
    }

    @Test
    void cancellingPendingListScopedRemovalPreventsFurtherDeletes() throws Exception {
        try (Fixture f = new Fixture()) {
            f.connection.rows = List.of(
                    row("*A", "target", ADDRESS),
                    row("*B", "target", ADDRESS),
                    row("*C", "other", ADDRESS));
            f.connection.deferRemoveId = "*A";
            var result = f.async.removeByListAndAddress("target", ADDRESS);
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*A'"),
                    f.connection.commands);
            assertTrue(result.cancel(true));
            f.connection.pendingRemove.completed();
            assertEquals(List.of(PRINT, "/ip/firewall/address-list/remove .id='*A'"),
                    f.connection.commands);
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
        volatile ResultListener pendingRemove;
        volatile String failRemoveId;
        volatile String deferRemoveId;
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
                if (failRemoveId != null && command.endsWith(".id='" + failRemoveId + "'")) {
                    listener.error(new MikrotikApiException("Simulated remove failure"));
                } else if (deferRemoveId != null && command.endsWith(".id='" + deferRemoveId + "'")) {
                    pendingRemove = listener;
                } else {
                    listener.completed();
                }
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
