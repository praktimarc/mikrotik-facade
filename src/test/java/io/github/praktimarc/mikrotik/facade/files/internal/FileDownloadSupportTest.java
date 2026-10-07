package io.github.praktimarc.mikrotik.facade.files.internal;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFileException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikUnsupportedFeatureException;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.ApiDataException;
import me.legrange.mikrotik.MikrotikApiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileDownloadSupportTest {
    @Test
    void successfulDownloadReturnsStableResult() throws Exception {
        Path target = Path.of("local.bin");
        var result = FileDownloadSupport.download(
                new ThrowingConnection(null, 1234),
                "flash/remote.bin",
                target);

        assertEquals("flash/remote.bin", result.remoteFile());
        assertEquals(target, result.localFile());
        assertEquals(1234L, result.bytesWritten());
    }

    @Test
    void localIoFailureBecomesFileException() {
        assertThrows(MikrotikFileException.class, () -> FileDownloadSupport.download(
                new ThrowingConnection(new IOException("disk full"), 0),
                "flash/remote.bin",
                Path.of("local.bin")));
    }

    @Test
    void transportCommandAndDataFailuresKeepFacadeCategories() {
        assertThrows(MikrotikConnectionException.class, () -> FileDownloadSupport.download(
                new ThrowingConnection(new ApiConnectionException("wire down"), 0),
                "flash/remote.bin", Path.of("local.bin")));

        assertThrows(MikrotikCommandException.class, () -> FileDownloadSupport.download(
                new ThrowingConnection(new TestCommandException("permission denied", 9), 0),
                "flash/remote.bin", Path.of("local.bin")));

        assertThrows(MikrotikDataException.class, () -> FileDownloadSupport.download(
                new ThrowingConnection(new TestDataException("bad size"), 0),
                "flash/remote.bin", Path.of("local.bin")));
    }

    @Test
    void onlyDefinitiveMissingBinaryCommandBecomesUnsupportedFeature() {
        assertThrows(MikrotikUnsupportedFeatureException.class, () -> FileDownloadSupport.download(
                new ThrowingConnection(new TestCommandException("no such command prefix", 0), 0),
                "flash/remote.bin", Path.of("local.bin")));

        assertThrows(MikrotikCommandException.class, () -> FileDownloadSupport.download(
                new ThrowingConnection(new TestCommandException("permission denied", 0), 0),
                "flash/remote.bin", Path.of("local.bin")));
    }

    private static final class ThrowingConnection extends StubApiConnection {
        private final Exception failure;
        private final long bytes;

        private ThrowingConnection(Exception failure, long bytes) {
            this.failure = failure;
            this.bytes = bytes;
        }

        @Override
        public long downloadFile(String remoteFile, Path localFile)
                throws MikrotikApiException, IOException {
            if (failure instanceof IOException io) throw io;
            if (failure instanceof MikrotikApiException api) throw api;
            return bytes;
        }
    }

    private static final class TestCommandException extends ApiCommandException {
        private TestCommandException(String message, Integer category) {
            super(message, "tag", category);
        }
    }

    private static final class TestDataException extends ApiDataException {
        private TestDataException(String message) {
            super(message);
        }
    }
}
