package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FilesFacadeWiringTest {
    @Test
    void syncAndAsyncFilesTreesAreWired() throws Exception {
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                new StubApiConnection(),
                new SessionLifecycle(),
                environment(),
                Runnable::run)) {
            assertNotNull(api.files());
            assertNotNull(api.async().files());
        }
    }

    @Test
    void sessionCloseTerminatesRunningAsyncBinaryDownload() throws Exception {
        BlockingDownloadConnection connection = new BlockingDownloadConnection();
        MikrotikRtrApi api = new MikrotikRtrApi(
                connection,
                new SessionLifecycle(),
                environment(),
                Runnable::run);

        CompletableFuture<?> download =
                api.async().files().download("flash/test.bin", Path.of("unused.bin"));
        assertTrue(connection.downloadEntered.await(2, TimeUnit.SECONDS));

        api.close();

        assertTrue(download.isDone());
        ExecutionException failure = assertThrows(ExecutionException.class, download::get);
        assertInstanceOf(MikrotikConnectionException.class, failure.getCause());
    }

    private static RouterOsEnvironment environment() {
        return RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo(
                        "7.20.4", "arm64", "test-board", "MikroTik", RouterOsRecord.empty()),
                List.of());
    }

    private static final class BlockingDownloadConnection extends StubApiConnection {
        private final CountDownLatch downloadEntered = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public String execute(String command, ResultListener listener) throws MikrotikApiException {
            if (!command.startsWith("/file/print")) {
                throw new AssertionError("Unexpected command " + command);
            }
            listener.receive(Map.of("name", "flash/test.bin", "size", "1"));
            listener.completed();
            return "file-meta";
        }

        @Override
        public long downloadFile(String remoteFile, Path localFile)
                throws MikrotikApiException, IOException {
            downloadEntered.countDown();
            try {
                closed.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            throw new ApiConnectionException("connection closed");
        }

        @Override
        public void cancel(String tag) {}

        @Override
        public void close() {
            closed.countDown();
        }
    }
}
