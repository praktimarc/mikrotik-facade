package io.github.praktimarc.mikrotik.facade.dhcp;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.dhcp.internal.DhcpLeaseMapper;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhcpServerApiTest {

    private final DhcpLeaseMapper mapper = new DhcpLeaseMapper();

    @Test
    void macLookupUsesExactLeaseQuery() {
        RouterOsOperation<Optional<DhcpLease>> operation =
                DhcpServerApi.findByMacOperation("AA:BB:CC:DD:EE:FF", mapper);

        assertEquals("/ip/dhcp-server/lease/print", operation.command().path());
        assertEquals("AA:BB:CC:DD:EE:FF", operation.command().queries().get("mac-address"));
        assertFalse(operation.command().queries().containsKey("address"));
    }

    @Test
    void addressLookupUsesExactLeaseQuery() {
        RouterOsOperation<Optional<DhcpLease>> operation =
                DhcpServerApi.findByAddressOperation("192.0.2.10", mapper);

        assertEquals("192.0.2.10", operation.command().queries().get("address"));
        assertFalse(operation.command().queries().containsKey("mac-address"));
    }

    @Test
    void zeroRowsMapsToOptionalEmpty() throws Exception {
        Optional<DhcpLease> lease = DhcpServerApi.findByMacOperation("AA", mapper)
                .map(new CommandResult(List.of(), RouterOsRecord.empty()));
        assertTrue(lease.isEmpty());
    }

    @Test
    void exactlyOneRowMapsToLease() throws Exception {
        Optional<DhcpLease> lease = DhcpServerApi.findByAddressOperation("192.0.2.10", mapper)
                .map(new CommandResult(
                        List.of(RouterOsRecord.of(Map.of("address", "192.0.2.10"))),
                        RouterOsRecord.empty()));
        assertEquals("192.0.2.10", lease.orElseThrow().address().orElseThrow());
    }

    @Test
    void multipleRowsWhereOneIsExpectedAreDataError() {
        CommandResult result = new CommandResult(
                List.of(RouterOsRecord.of(Map.of()), RouterOsRecord.of(Map.of())),
                RouterOsRecord.empty());

        assertThrows(
                MikrotikDataException.class,
                () -> DhcpServerApi.findByMacOperation("AA", mapper).map(result));
    }

    @Test
    void blankLookupValuesAreProgrammingErrors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DhcpServerApi.findByMacOperation("   ", mapper));
        assertThrows(
                IllegalArgumentException.class,
                () -> DhcpServerApi.findByAddressOperation("", mapper));
    }
}
