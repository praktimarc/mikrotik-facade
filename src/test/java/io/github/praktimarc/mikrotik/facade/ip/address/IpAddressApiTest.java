package io.github.praktimarc.mikrotik.facade.ip.address;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.ip.address.internal.IpAddressV4Mapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IpAddressApiTest {
    @Test
    void addressOperationUsesIpAddressPathAndCommentQuery() {
        var operation = IpAddressApi.listOperation(
                RouterOsProperties.builder().set("comment", "cmts-internal").build(),
                new IpAddressV4Mapper());
        assertEquals("/ip/address/print", operation.command().path());
        assertEquals("cmts-internal", operation.command().queries().get("comment"));
    }
}
