package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterfacesFacadeWiringTest {
    @Test
    void syncAsyncAndSessionCloseStreamingAreWired() throws Exception {
        FakeConnection connection = new FakeConnection();
        RouterOsEnvironment environment = RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo("7.20.4", "arm64", "test-board", "MikroTik", RouterOsRecord.empty()),
                List.of());
        MikrotikRtrApi api = new MikrotikRtrApi(
                connection, new SessionLifecycle(), environment, Runnable::run);
        try {
            assertNotNull(api.interfaces());
            assertNotNull(api.async().interfaces());
            api.interfaces().monitor("ether1").subscribe(new RequestingSubscriber());
            assertTrue(connection.executeEntered.await(2, TimeUnit.SECONDS));
        } finally {
            api.close();
        }
        assertEquals(1, connection.cancels.get());
    }

    private static final class RequestingSubscriber
            implements Flow.Subscriber<io.github.praktimarc.mikrotik.facade.interfaces.InterfaceMonitorEntry> {
        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(io.github.praktimarc.mikrotik.facade.interfaces.InterfaceMonitorEntry item) {}
        @Override public void onError(Throwable throwable) {}
        @Override public void onComplete() {}
    }

    private static final class FakeConnection extends ApiConnection {
        private final AtomicInteger cancels = new AtomicInteger();
        private final CountDownLatch executeEntered = new CountDownLatch(1);

        @Override
        public String execute(String command, ResultListener listener) throws MikrotikApiException {
            executeEntered.countDown();
            return "tag-monitor";
        }

        @Override public void cancel(String tag) { cancels.incrementAndGet(); }
        @Override public void close() {}
    }
}
