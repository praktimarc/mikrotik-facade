package io.github.praktimarc.mikrotik.facade.contract;

import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer contract for the exact public guarantees used from
 * io.github.praktimarc:mikrotik:3.0.8-praktimarc.4.
 */
class MikrotikJavaContractTest {

    @Test
    void concurrentListenerCommandsAreIndependentlyTaggedAndRouted() throws Exception {
        AtomicReference<RouterOsWireTestServer.CommandSentence> first = new AtomicReference<>();
        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            if ("/first".equals(command.command())) {
                first.set(command);
                return;
            }
            if ("/second".equals(command.command())) {
                wire.reply("!re", command.tag(), "=value=second");
                wire.reply("!done", command.tag(), "=terminal=second");
                RouterOsWireTestServer.CommandSentence held = first.get();
                assertNotNull(held);
                wire.reply("!re", held.tag(), "=value=first");
                wire.reply("!done", held.tag(), "=terminal=first");
                return;
            }
            throw new AssertionError("Unexpected command " + command.command());
        })) {
            ApiConnection connection = connect(server);
            try {
                ListenerProbe firstProbe = new ListenerProbe();
                ListenerProbe secondProbe = new ListenerProbe();

                String firstTag = connection.execute("/first", firstProbe);
                String secondTag = connection.execute("/second", secondProbe);

                assertNotEquals(firstTag, secondTag);
                assertTrue(firstProbe.terminal.await(1, TimeUnit.SECONDS));
                assertTrue(secondProbe.terminal.await(1, TimeUnit.SECONDS));

                assertEquals(List.of("first"), firstProbe.values());
                assertEquals(List.of("second"), secondProbe.values());
                assertEquals("first", firstProbe.completion.get().get("terminal"));
                assertEquals("second", secondProbe.completion.get().get("terminal"));
                assertNull(firstProbe.failure.get());
                assertNull(secondProbe.failure.get());
                assertEquals(1, firstProbe.completions.get());
                assertEquals(1, secondProbe.completions.get());
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void completionAwareListenerReceivesGenericDoneMetadata() throws Exception {
        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) ->
                wire.reply("!done", command.tag(), "=ret=*A", "=foo=bar", "=empty="))) {
            ApiConnection connection = connect(server);
            try {
                AtomicReference<Map<String, String>> completion = new AtomicReference<>();
                AtomicInteger legacyCompleted = new AtomicInteger();
                CountDownLatch done = new CountDownLatch(1);

                connection.execute("/completion-contract", new ResultListener() {
                    @Override
                    public void receive(Map<String, String> result) {
                        fail("No data record expected");
                    }

                    @Override
                    public void error(MikrotikApiException ex) {
                        fail(ex);
                    }

                    @Override
                    public void completed() {
                        legacyCompleted.incrementAndGet();
                        done.countDown();
                    }

                    @Override
                    public void completed(Map<String, String> metadata) {
                        completion.set(metadata);
                        done.countDown();
                    }
                });

                assertTrue(done.await(1, TimeUnit.SECONDS));
                assertNotNull(completion.get());
                assertEquals("*A", completion.get().get("ret"));
                assertEquals("bar", completion.get().get("foo"));
                assertEquals("", completion.get().get("empty"));
                assertFalse(completion.get().containsKey(".tag"));
                assertEquals(0, legacyCompleted.get());
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> completion.get().put("x", "y"));
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void taggedTrapIsTerminalCommandErrorAndDoesNotBreakConnection() throws Exception {
        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            if ("/bad".equals(command.command())) {
                wire.reply("!trap", command.tag(), "=message=missing command", "=category=0");
            } else if ("/bad-without-category".equals(command.command())) {
                wire.reply("!trap", command.tag(), "=message=generic failure");
            } else if ("/good".equals(command.command())) {
                wire.reply("!re", command.tag(), "=value=still-alive");
                wire.reply("!done", command.tag());
            } else {
                throw new AssertionError("Unexpected command " + command.command());
            }
        })) {
            ApiConnection connection = connect(server);
            try {
                ListenerProbe bad = new ListenerProbe();
                connection.execute("/bad", bad);

                assertTrue(bad.terminal.await(1, TimeUnit.SECONDS));
                assertInstanceOf(ApiCommandException.class, bad.failure.get());
                ApiCommandException commandFailure = (ApiCommandException) bad.failure.get();
                assertTrue(commandFailure.hasCategory());
                assertEquals(0, commandFailure.getCategory());
                assertEquals("missing command", commandFailure.getMessage());
                assertEquals(0, bad.completions.get());

                ListenerProbe absentCategory = new ListenerProbe();
                connection.execute("/bad-without-category", absentCategory);
                assertTrue(absentCategory.terminal.await(1, TimeUnit.SECONDS));
                assertInstanceOf(ApiCommandException.class, absentCategory.failure.get());
                ApiCommandException absent =
                        (ApiCommandException) absentCategory.failure.get();
                assertFalse(absent.hasCategory());
                assertEquals(0, absent.getCategory());

                List<Map<String, String>> good = connection.execute("/good");
                assertEquals(1, good.size());
                assertEquals("still-alive", good.get(0).get("value"));
                assertTrue(connection.isConnected());
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void unexpectedEstablishedConnectionLossNotifiesListenerExactlyOnce() throws Exception {
        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            if ("/drop".equals(command.command())) {
                wire.closeClientConnection();
            }
        })) {
            ApiConnection connection = connect(server);
            AtomicInteger notifications = new AtomicInteger();
            AtomicReference<ApiConnectionException> failure = new AtomicReference<>();
            CountDownLatch lost = new CountDownLatch(1);
            connection.addConnectionListener(cause -> {
                failure.compareAndSet(null, cause);
                notifications.incrementAndGet();
                lost.countDown();
            });

            connection.execute("/drop", new NoopListener());

            assertTrue(lost.await(1, TimeUnit.SECONDS));
            assertNotNull(failure.get());
            assertFalse(connection.isConnected());
            Thread.sleep(100);
            assertEquals(1, notifications.get());
            connection.close();
        }
    }

    @Test
    void intentionalCloseIsIdempotentAndDoesNotReportConnectionLoss() throws Exception {
        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            throw new AssertionError("No command expected");
        })) {
            ApiConnection connection = connect(server);
            CountDownLatch lost = new CountDownLatch(1);
            connection.addConnectionListener(cause -> lost.countDown());

            connection.close();
            connection.close();

            assertFalse(connection.isConnected());
            assertFalse(lost.await(200, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void fatalConnectionLossTerminatesActiveCommandAndRejectsLaterSubmission() throws Exception {
        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            if ("/hold".equals(command.command())) {
                wire.closeClientConnection();
            }
        })) {
            ApiConnection connection = connect(server);
            ListenerProbe active = new ListenerProbe();
            CountDownLatch lost = new CountDownLatch(1);
            AtomicReference<ApiConnectionException> lifecycleFailure = new AtomicReference<>();
            connection.addConnectionListener(cause -> {
                lifecycleFailure.set(cause);
                lost.countDown();
            });

            connection.execute("/hold", active);

            assertTrue(lost.await(1, TimeUnit.SECONDS));
            assertTrue(active.terminal.await(1, TimeUnit.SECONDS));
            assertNotNull(lifecycleFailure.get());
            assertInstanceOf(ApiConnectionException.class, active.failure.get());
            assertEquals(0, active.completions.get());
            assertThrows(
                    ApiConnectionException.class,
                    () -> connection.execute("/later", new NoopListener()));
            assertFalse(connection.isConnected());
            connection.close();
        }
    }

    @Test
    void binaryDownloadIsByteExactChunkedAndCoexistsWithTextListener() throws Exception {
        byte[] payload = hostilePayload(70_013);
        String remote = "flash/docsis/cm profile=1;#.cfg";
        AtomicReference<String> fileQuery = new AtomicReference<>();
        List<String> fileParameters = Collections.synchronizedList(new ArrayList<>());
        List<Integer> offsets = Collections.synchronizedList(new ArrayList<>());
        List<Integer> chunkSizes = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<String> watchTag = new AtomicReference<>();

        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            switch (command.command()) {
                case "/watch" -> watchTag.set(command.tag());
                case "/file/print" -> {
                    fileQuery.set(command.queries().isEmpty() ? null : command.queries().get(0));
                    wire.reply("!re", command.tag(), "=size=" + payload.length);
                    wire.reply("!done", command.tag());
                }
                case "/file/read" -> {
                    String pendingWatch = watchTag.getAndSet(null);
                    if (pendingWatch != null) {
                        wire.reply("!re", pendingWatch, "=value=text-only");
                        wire.reply("!done", pendingWatch);
                    }

                    String file = command.parameter("file");
                    int offset = Integer.parseInt(command.parameter("offset"));
                    int chunk = Integer.parseInt(command.parameter("chunk-size"));
                    fileParameters.add(file);
                    offsets.add(offset);
                    chunkSizes.add(chunk);

                    int length = Math.min(chunk, payload.length - offset);
                    wire.replyData(
                            command.tag(),
                            Arrays.copyOfRange(payload, offset, offset + length));
                    wire.reply("!done", command.tag());
                }
                default -> throw new AssertionError("Unexpected command " + command.command());
            }
        })) {
            ApiConnection connection = connect(server);
            Path directory = Files.createTempDirectory("mikrotik-facade-contract-download");
            Path target = directory.resolve("download.bin");
            try {
                ListenerProbe watch = new ListenerProbe();
                connection.execute("/watch", watch);

                long bytes = connection.downloadFile(remote, target);

                assertTrue(watch.terminal.await(1, TimeUnit.SECONDS));
                assertEquals(List.of("text-only"), watch.values());
                assertNull(watch.failure.get());

                assertEquals(payload.length, bytes);
                assertArrayEquals(payload, Files.readAllBytes(target));
                assertFalse(Files.exists(target.resolveSibling("download.bin.part")));
                assertEquals("?name=" + remote, fileQuery.get());
                assertTrue(fileParameters.size() >= 3);
                assertTrue(fileParameters.stream().allMatch(remote::equals));
                assertEquals(0, offsets.get(0));
                assertTrue(chunkSizes.stream().allMatch(size -> size > 0 && size <= 32_768));
                for (int index = 1; index < offsets.size(); index++) {
                    assertTrue(offsets.get(index) > offsets.get(index - 1));
                }
                assertTrue(connection.isConnected());
            } finally {
                connection.close();
                Files.deleteIfExists(target.resolveSibling("download.bin.part"));
                Files.deleteIfExists(target);
                Files.deleteIfExists(directory);
            }
        }
    }



    @Test
    void binaryDownloadPublishesFinalTargetOnlyAfterCompleteSuccess() throws Exception {
        byte[] payload = hostilePayload(40_000);
        CountDownLatch firstReadEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRead = new CountDownLatch(1);

        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            if ("/file/print".equals(command.command())) {
                wire.reply("!re", command.tag(), "=size=" + payload.length);
                wire.reply("!done", command.tag());
                return;
            }
            if ("/file/read".equals(command.command())) {
                int offset = Integer.parseInt(command.parameter("offset"));
                int chunk = Integer.parseInt(command.parameter("chunk-size"));
                if (offset == 0) {
                    firstReadEntered.countDown();
                    assertTrue(releaseFirstRead.await(1, TimeUnit.SECONDS));
                }
                int length = Math.min(chunk, payload.length - offset);
                wire.replyData(
                        command.tag(),
                        Arrays.copyOfRange(payload, offset, offset + length));
                wire.reply("!done", command.tag());
                return;
            }
            throw new AssertionError("Unexpected command " + command.command());
        })) {
            ApiConnection connection = connect(server);
            Path directory = Files.createTempDirectory("mikrotik-facade-contract-staging");
            Path target = directory.resolve("staged.bin");
            Path part = target.resolveSibling("staged.bin.part");
            Files.write(target, new byte[]{99});

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Long> download = executor.submit(
                        () -> connection.downloadFile("flash/staged.bin", target));

                assertTrue(firstReadEntered.await(1, TimeUnit.SECONDS));
                assertFalse(Files.exists(target));
                assertTrue(Files.exists(part));

                releaseFirstRead.countDown();

                assertEquals(payload.length, download.get(2, TimeUnit.SECONDS));
                assertArrayEquals(payload, Files.readAllBytes(target));
                assertFalse(Files.exists(part));
            } finally {
                releaseFirstRead.countDown();
                executor.shutdownNow();
                connection.close();
                Files.deleteIfExists(part);
                Files.deleteIfExists(target);
                Files.deleteIfExists(directory);
            }
        }
    }
    private static ApiConnection connect(RouterOsWireTestServer server) throws Exception {
        ApiConnection connection = ApiConnection.connect(
                SocketFactory.getDefault(),
                "127.0.0.1",
                server.port(),
                1_000);
        connection.setTimeout(1_500);
        assertTrue(server.awaitClientConnection(1, TimeUnit.SECONDS));
        return connection;
    }

    private static byte[] hostilePayload(int length) {
        byte[] data = new byte[length];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) index;
        }
        byte[] hostile = new byte[]{
                0, 13, 10, (byte) 0xc0, (byte) 0xaf, (byte) 0xff, (byte) 0xfe,
                '=', 'd', 'a', 't', 'a', '=', '!', 'r', 'e', '!', 'd', 'o', 'n', 'e'
        };
        System.arraycopy(hostile, 0, data, 123, hostile.length);
        return data;
    }

    private static final class ListenerProbe implements ResultListener {
        private final List<Map<String, String>> rows =
                Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<Map<String, String>> completion = new AtomicReference<>();
        private final AtomicReference<MikrotikApiException> failure = new AtomicReference<>();
        private final AtomicInteger completions = new AtomicInteger();
        private final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void receive(Map<String, String> result) {
            rows.add(result);
        }

        @Override
        public void error(MikrotikApiException ex) {
            failure.compareAndSet(null, ex);
            terminal.countDown();
        }

        @Override
        public void completed() {
            completions.incrementAndGet();
            terminal.countDown();
        }

        @Override
        public void completed(Map<String, String> metadata) {
            completion.set(metadata);
            completions.incrementAndGet();
            terminal.countDown();
        }

        private List<String> values() {
            synchronized (rows) {
                return rows.stream().map(row -> row.get("value")).toList();
            }
        }
    }

    private static final class NoopListener implements ResultListener {
        @Override public void receive(Map<String, String> result) {}
        @Override public void error(MikrotikApiException ex) {}
        @Override public void completed() {}
    }
}
