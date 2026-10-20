package io.github.praktimarc.mikrotik.facade;

import io.github.praktimarc.mikrotik.facade.environment.RouterOsEnvironment;
import io.github.praktimarc.mikrotik.facade.environment.RouterOsSystemInfo;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import io.github.praktimarc.mikrotik.facade.testing.StubApiConnection;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class RawWordsApiTest {
    @Test
    void rawProtocolWordsBypassStringParserAndKeepCompletion() throws Exception {
        WordCapturingConnection conn = new WordCapturingConnection();
        var env = RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo("7.21", "arm64", "test-router", null, RouterOsRecord.empty()),
                List.of());
        try (MikrotikRtrApi api = new MikrotikRtrApi(
                conn, new SessionLifecycle(), env, Runnable::run)) {
            List<String> exact = List.of(
                    "/interface/wifi/capsman/remote-cap/print",
                    "?=base-mac=D0:EA:11:8A:2F:9B",
                    "?base-mac",
                    "=comment=spaces and 'quotes'");
            var result = api.raw().words(exact);
            assertEquals(exact, conn.seen);
            assertEquals("cap-test", result.records().get(0).find("identity").orElseThrow());
            assertEquals("ok", result.completion().find("ret").orElseThrow());

            var asyncResult = api.async().raw()
                    .words(exact.get(0), exact.get(1), exact.get(2), exact.get(3))
                    .get(3, TimeUnit.SECONDS);
            assertEquals(exact, conn.seen);
            assertEquals("cap-test", asyncResult.records().get(0).find("identity").orElseThrow());

            assertThrows(IllegalArgumentException.class,
                    () -> api.raw().words(exact.get(0), ".tag=manually-supplied"));
            assertThrows(IllegalArgumentException.class,
                    () -> api.raw().words(exact.get(0), ""));
        }
    }


    @Test
    void cookbookReadOnlyWordSequencesAreForwardedExactlyAndInOrder() throws Exception {
        WordCapturingConnection conn = new WordCapturingConnection();
        var env = RouterOsEnvironment.withPackages(
                new RouterOsSystemInfo("7.21", "arm64", "test-router", null, RouterOsRecord.empty()),
                List.of());

        try (MikrotikRtrApi api = new MikrotikRtrApi(
                conn, new SessionLifecycle(), env, Runnable::run)) {
            List<List<String>> examples = List.of(
                    List.of("/interface/print", "?type=ether",
                            "=.proplist=.id,name,type,disabled"),
                    List.of("/interface/print", "?type=ether", "?disabled=false",
                            "=.proplist=.id,name,type,disabled"),
                    List.of("/interface/print", "?type=ether", "?type=vlan",
                            "?#|", "=.proplist=name,type"),
                    List.of("/ip/route/print", "?>distance=1",
                            "?gateway=172.16.1.1", "?#&!",
                            "=.proplist=.id,dst-address,distance,gateway"),
                    List.of("/ip/route/print", "?comment"),
                    List.of("/ip/route/print", "?-comment"),
                    List.of("/interface/wifi/capsman/remote-cap/print",
                            "?base-mac=D0:EA:11:8A:2F:9B"),
                    List.of("/interface/wifi/capsman/remote-cap/print",
                            "?=base-mac=D0:EA:11:8A:2F:9B"),
                    List.of("/interface/wifi/capsman/remote-cap/print",
                            "?base-mac"),
                    List.of("/interface/print", "=count-only="));

            for (List<String> words : examples) {
                var result = api.raw().words(words);
                assertEquals(words, conn.seen, "Must preserve the cookbook sentence exactly");
                assertEquals("cap-test", result.records().get(0).require("identity"));
                assertEquals("ok", result.completion().require("ret"));
            }

            // Async and synchronous APIs must use the same unparsed word path.
            List<String> asyncWords = examples.get(2);
            var asyncResult = api.async().raw().words(asyncWords).get(3, TimeUnit.SECONDS);
            assertEquals(asyncWords, conn.seen);
            assertEquals("ok", asyncResult.completion().require("ret"));
        }
    }

    @Test
    void protocolWordsAreExcludedFromStructuralDiagnostics() {
        String secret = "diagnostic-test-token-only";
        RouterOsCommand command = RouterOsCommand.fromWireWords(List.of(
                "/ip/firewall/filter/print", "?comment=" + secret));
        assertTrue(command.hasWireWords());
        assertFalse(command.toString().contains(secret));
        assertTrue(command.queryValuesForRedaction().containsValue(secret));
        assertThrows(IllegalStateException.class, command::serialize);
    }

    private static class WordCapturingConnection extends StubApiConnection {
        private volatile List<String> seen;
        @Override
        public String executeWords(List<String> words, ResultListener listener) {
            seen = List.copyOf(words);
            listener.receive(Map.of("identity", "cap-test"));
            listener.completed(Map.of("ret", "ok"));
            return "test-tag";
        }
        @Override
        public String execute(String command, ResultListener listener) {
            throw new AssertionError("Exact words must bypass execute(String)");
        }
        @Override
        public void close() {}
    }
}
