package io.github.praktimarc.mikrotik.facade.internal.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouterOsCommandTest {

    @Test
    void serializesArgumentsQueriesAndPropertiesWithoutTransportMetadata() {
        RouterOsCommand command = RouterOsCommand.builder("/ip/address/print")
                .argument("detail", "yes")
                .query("interface", "ether1/wan")
                .property(".id")
                .property("address")
                .build();

        assertEquals(
                "/ip/address/print detail='yes' where interface='ether1/wan' return .id,address",
                command.serialize());
        assertFalse(command.serialize().contains(".tag"));
    }

    @Test
    void commandIsImmutableSnapshotAndDiagnosticsRedactSecrets() {
        RouterOsCommand command = RouterOsCommand.builder("/user/add")
                .argument("name", "alice")
                .argument("password", "super-secret")
                .build();

        assertEquals("super-secret", command.arguments().get("password"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> command.arguments().put("x", "y"));
        assertFalse(command.toString().contains("super-secret"));
    }
}
