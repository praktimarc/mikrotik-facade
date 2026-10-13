package io.github.praktimarc.mikrotik.facade.interfaces;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import java.util.List;
import java.util.Map;
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

}
