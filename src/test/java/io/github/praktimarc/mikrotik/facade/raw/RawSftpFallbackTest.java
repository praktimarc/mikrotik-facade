package io.github.praktimarc.mikrotik.facade.raw;

import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RawSftpFallbackTest {
    @Test
    void structuredRawCommandCanExpressLegacyRouterOsSftpUpload() throws Exception {
        AtomicReference<String> sent = new AtomicReference<>();
        StubApiConnection connection = new StubApiConnection() {
            @Override
            public String execute(String command, ResultListener listener) {
                sent.set(command);
                listener.completed(Map.of());
                return "tag-1";
            }

            @Override
            public void cancel(String tag) {}
        };
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CommandEngine engine = new CommandEngine(
                    connection,
                    Duration.ofSeconds(5),
                    Runnable::run,
                    Runnable::run,
                    scheduler);
            RawApi raw = new RawApi(engine, new SessionLifecycle());

            raw.command("/tool/fetch")
                    .argument("url", "sftp://198.51.100.10/upload/config.rsc")
                    .argument("upload", "yes")
                    .argument("src-path", "config.rsc")
                    .argument("user", "backup-user")
                    .argument("password", "test-secret")
                    .execute();

            String command = sent.get();
            assertNotNull(command);
            assertTrue(command.startsWith("/tool/fetch "));
            assertTrue(command.contains("upload='yes'"));
            assertTrue(command.contains("src-path='config.rsc'"));
            assertTrue(command.contains("url='sftp://198.51.100.10/upload/config.rsc'"));
        } finally {
            scheduler.shutdownNow();
        }
    }
}
