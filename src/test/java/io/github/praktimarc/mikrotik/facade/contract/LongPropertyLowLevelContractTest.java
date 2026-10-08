package io.github.praktimarc.mikrotik.facade.contract;

import me.legrange.mikrotik.ApiConnection;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LongPropertyLowLevelContractTest {

    @Test
    void longTextPropertySurvivesPublicLowLevelApiWithoutTruncation() throws Exception {
        String value = "prefix-" + "0123456789".repeat(6_000) + "-TAIL";

        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            wire.reply("!re", command.tag(), "=comment=" + value);
            wire.reply("!done", command.tag());
        })) {
            ApiConnection connection = ApiConnection.connect(
                    SocketFactory.getDefault(),
                    "127.0.0.1",
                    server.port(),
                    1_000);
            connection.setTimeout(1_500);
            assertTrue(server.awaitClientConnection(1, TimeUnit.SECONDS));
            try {
                List<Map<String, String>> rows = connection.execute("/long-property");

                assertEquals(1, rows.size());
                assertEquals(value.length(), rows.get(0).get("comment").length());
                assertEquals(value, rows.get(0).get("comment"));
                assertTrue(rows.get(0).get("comment").endsWith("-TAIL"));
            } finally {
                connection.close();
            }
        }
    }
}
