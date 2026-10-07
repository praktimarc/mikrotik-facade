package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.interfaces.internal.InterfaceAddressMapper;
import io.github.praktimarc.mikrotik.facade.interfaces.internal.InterfaceInfoMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterfacesApiTest {
    @Test
    void interfaceListOperationUsesGenericInterfacePathAndEqualityQueries() {
        var operation = InterfacesApi.listOperation(
                RouterOsProperties.builder().set("name", "ether1").build(),
                new InterfaceInfoMapper());
        assertEquals("/interface/print", operation.command().path());
        assertEquals("ether1", operation.command().queries().get("name"));
    }

    @Test
    void addressOperationUsesIpAddressPathAndCommentQuery() {
        var operation = InterfacesApi.addressesOperation(
                RouterOsProperties.builder().set("comment", "cmts-internal").build(),
                new InterfaceAddressMapper());
        assertEquals("/ip/address/print", operation.command().path());
        assertEquals("cmts-internal", operation.command().queries().get("comment"));
    }
}
