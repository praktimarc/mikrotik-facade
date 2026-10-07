package io.github.praktimarc.mikrotik.facade.firewall.addresslist;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.firewall.addresslist.internal.AddressListEntryMapper;
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
}
