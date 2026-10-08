package io.github.praktimarc.mikrotik.facade.raw;

import io.github.praktimarc.mikrotik.facade.RouterOsQuery;
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

class RawAdvancedQueryTest {

    @Test
    void rawBuilderAcceptsRepeatedPropertyOrExpression() throws Exception {
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

            raw.command("/interface/print")
                    .query(RouterOsQuery.eq("name", "cap-a")
                            .or(RouterOsQuery.eq("name", "cap-b")))
                    .property("name")
                    .execute();

            assertEquals(
                    "/interface/print where name='cap-a' or name='cap-b' return name",
                    sent.get());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
