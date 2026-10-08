package io.github.praktimarc.mikrotik.facade.contract;

import io.github.praktimarc.mikrotik.facade.RouterOsQuery;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import me.legrange.mikrotik.ApiConnection;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedQueryLowLevelContractTest {

    @Test
    void pinnedLowLevelParserTranslatesRepeatedPropertyBooleanQueryToWireStack()
            throws Exception {
        AtomicReference<List<String>> queryWords = new AtomicReference<>();

        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            queryWords.set(command.queries());
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
                RouterOsCommand command = RouterOsCommand.builder("/interface/print")
                        .query(RouterOsQuery.eq("interface", "cap-a")
                                .or(RouterOsQuery.eq("interface", "cap-b"))
                                .and(RouterOsQuery.gt("signal", "-80")))
                        .build();

                connection.execute(command.serialize());

                assertEquals(List.of(
                        "?interface=cap-a",
                        "?interface=cap-b",
                        "?#|",
                        "?>signal=-80",
                        "?#&"), queryWords.get());
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void pinnedLowLevelParserPreservesNotEqualsAsQueryStackNegation() throws Exception {
        AtomicReference<List<String>> queryWords = new AtomicReference<>();

        try (RouterOsWireTestServer server = new RouterOsWireTestServer((wire, command) -> {
            queryWords.set(command.queries());
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
                RouterOsCommand command = RouterOsCommand.builder("/interface/print")
                        .query(RouterOsQuery.notEq("disabled", "true"))
                        .build();

                connection.execute(command.serialize());

                assertEquals(List.of("?disabled=true", "?#!"), queryWords.get());
            } finally {
                connection.close();
            }
        }
    }
}
