package io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.ip.firewall.addresslist.internal.AddressListEntryMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AddressListApiTest {
    @Test void listAndAddressQueryUsesRouterOsListPropertyNotLegacyAddressListKey() {
        RouterOsProperties queries=RouterOsProperties.builder().set("list","active-clients").set("address","192.0.2.10").build();
        var operation=AddressListApi.findOperation(queries,new AddressListEntryMapper());
        assertEquals("/ip/firewall/address-list/print",operation.command().path());
        assertEquals("active-clients",operation.command().queries().get("list"));
        assertFalse(operation.command().queries().containsKey("address-list"));
        assertEquals("192.0.2.10",operation.command().queries().get("address"));
    }
    @Test void stableAddressListFieldsMapAndUnknownRawDataRemainsAttached() throws Exception {
        RouterOsOperation<List<AddressListEntry>> operation=AddressListApi.findOperation(RouterOsProperties.builder().build(),new AddressListEntryMapper());
        RouterOsRecord raw=RouterOsRecord.of(Map.ofEntries(
          Map.entry(".id","*1"),Map.entry("list","active-clients"),Map.entry("address","192.0.2.10"),
          Map.entry("timeout","5m"),Map.entry("creation-time","2026-10-07 08:00:00"),
          Map.entry("dynamic","true"),Map.entry("disabled","false"),Map.entry("comment",""),Map.entry("future","kept")));
        AddressListEntry entry=operation.map(new CommandResult(List.of(raw),RouterOsRecord.empty())).get(0);
        assertEquals("active-clients",entry.list().orElseThrow());
        assertEquals(300L,entry.timeout().orElseThrow().getSeconds());
        assertTrue(entry.dynamic().orElseThrow());
        assertEquals("kept",entry.raw().find("future").orElseThrow());
    }
    @Test void emptyResultAndFlexibleAddAreNormal() throws Exception {
        var find=AddressListApi.findOperation(RouterOsProperties.builder().build(),new AddressListEntryMapper());
        assertTrue(find.map(new CommandResult(List.of(),RouterOsRecord.empty())).isEmpty());
        RouterOsProperties properties=RouterOsProperties.builder().set("list","blocked").set("address","198.51.100.5").build();
        RouterOsOperation<Optional<String>> add=AddressListApi.addOperation(properties);
        assertEquals(properties.asMap(),add.command().arguments());
        assertEquals("*C",add.map(new CommandResult(List.of(),RouterOsRecord.of(Map.of("ret","*C")))).orElseThrow());
        assertThrows(MikrotikDataException.class,()->new AddressListEntryMapper().map(RouterOsRecord.of(Map.of("timeout","bad-time"))));
    }
    @Test void setPropertiesUsesExactIdAndPreservesAllUpdateValues() throws Exception {
        RouterOsProperties updates = RouterOsProperties.builder()
                .set("comment", "test entry with whitespace and = sign")
                .set("disabled", "true")
                .set("timeout", "5m")
                .build();
        var operation = AddressListApi.setPropertiesOperation("*A", updates);
        assertEquals("/ip/firewall/address-list/set", operation.command().path());
        assertEquals("*A", operation.command().arguments().get(".id"));
        assertEquals("test entry with whitespace and = sign",
                operation.command().arguments().get("comment"));
        assertEquals("true", operation.command().arguments().get("disabled"));
        assertEquals("5m", operation.command().arguments().get("timeout"));
        assertEquals(4, operation.command().arguments().size());
        assertNull(operation.map(new CommandResult(List.of(), RouterOsRecord.empty())));
    }

    @Test void setPropertiesRejectsInvalidTargetsAndReservedIdentity() {
        var valid = RouterOsProperties.builder().set("comment", "example").build();
        assertThrows(IllegalArgumentException.class,
                () -> AddressListApi.setPropertiesOperation("  ", valid));
        assertThrows(IllegalArgumentException.class,
                () -> AddressListApi.setPropertiesOperation("*A",
                        RouterOsProperties.builder().build()));
        assertThrows(IllegalArgumentException.class,
                () -> AddressListApi.setPropertiesOperation("*A",
                        RouterOsProperties.builder().set(".id", "*B").build()));
        assertThrows(NullPointerException.class,
                () -> AddressListApi.setPropertiesOperation("*A", null));
    }

    @Test void removeUsesExactIdOnlyAndMapsVoidCompletion() throws Exception {
        var operation = AddressListApi.removeOperation("*D");
        assertEquals("/ip/firewall/address-list/remove", operation.command().path());
        assertEquals(Map.of(".id", "*D"), operation.command().arguments());
        assertNull(operation.map(new CommandResult(List.of(), RouterOsRecord.empty())));
        assertThrows(IllegalArgumentException.class,
                () -> AddressListApi.removeOperation(" "));
        assertThrows(NullPointerException.class,
                () -> AddressListApi.removeOperation(null));
    }

    @Test void listAndAddressMustBothMatchActualRouterOsFields() throws Exception {
        var query = RouterOsProperties.builder().set("list", "allowed")
                .set("address", "192.0.2.10").build();
        var operation = AddressListApi.findOperation(query, new AddressListEntryMapper());
        var found = operation.map(new CommandResult(List.of(
                RouterOsRecord.of(Map.of("list", "allowed", "address", "192.0.2.11")),
                RouterOsRecord.of(Map.of("list", "other", "address", "192.0.2.10")),
                RouterOsRecord.of(Map.of("list", "ALLOWED", "address", "192.0.2.10")),
                RouterOsRecord.of(Map.of("list", "allowed"))),
                RouterOsRecord.empty()));
        assertEquals(1, found.size());
        assertEquals("192.0.2.10", found.get(0).address().orElseThrow());
    }

}
