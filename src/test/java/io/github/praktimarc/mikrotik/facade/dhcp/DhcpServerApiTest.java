package io.github.praktimarc.mikrotik.facade.dhcp;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.dhcp.internal.DhcpLeaseMapper;
import io.github.praktimarc.mikrotik.facade.dhcp.internal.DhcpPoolMapper;
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
                List.of(RouterOsRecord.of(Map.of("mac-address", "AA")),
                        RouterOsRecord.of(Map.of("mac-address", "aa"))),
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

    @Test
    void poolsPreserveOneRouterOsRowAndSplitMultipleRanges() throws Exception {
        RouterOsOperation<List<DhcpPool>> operation =
                DhcpServerApi.poolsOperation(new DhcpPoolMapper());

        assertEquals("/ip/pool/print", operation.command().path());

        List<DhcpPool> pools = operation.map(new CommandResult(
                List.of(RouterOsRecord.of(Map.of(
                        ".id", "*1",
                        "name", "customer",
                        "ranges", "192.0.2.10-192.0.2.20, 192.0.2.30-192.0.2.40",
                        "next-pool", "none",
                        "future-field", "kept"))),
                RouterOsRecord.empty()));

        assertEquals(1, pools.size());
        assertEquals(
                List.of("192.0.2.10-192.0.2.20", "192.0.2.30-192.0.2.40"),
                pools.get(0).ranges());
        assertEquals("kept", pools.get(0).raw().find("future-field").orElseThrow());
    }

    @Test
    void leaseCountUsesValuelessCountOnlyAndTerminalRet() throws Exception {
        RouterOsOperation<Long> operation = DhcpServerApi.countLeasesOperation(
                RouterOsProperties.builder().set("server", "customer-dhcp").build());

        assertTrue(operation.command().flags().isEmpty());
        assertEquals("customer-dhcp", operation.command().queries().get("server"));
        assertEquals(
                "/ip/dhcp-server/lease/print where server='customer-dhcp'",
                operation.command().serialize());
        assertEquals(
                2L,
                operation.map(new CommandResult(
                        List.of(RouterOsRecord.of(Map.of("server", "CUSTOMER-DHCP")),
                                RouterOsRecord.of(Map.of("server", "other")),
                                RouterOsRecord.of(Map.of("server", "customer-dhcp"))),
                        RouterOsRecord.empty())));
    }

    @Test
    void leaseCountRejectsMissingMalformedAndNegativeRet() {
        RouterOsOperation<Long> operation = DhcpServerApi.countLeasesOperation(
                RouterOsProperties.builder().build());

        assertThrows(
                MikrotikDataException.class,
                () -> operation.map(new CommandResult(List.of(), RouterOsRecord.empty())));
        assertThrows(
                MikrotikDataException.class,
                () -> operation.map(new CommandResult(
                        List.of(), RouterOsRecord.of(Map.of("ret", "NaN")))));
        assertThrows(
                MikrotikDataException.class,
                () -> operation.map(new CommandResult(
                        List.of(), RouterOsRecord.of(Map.of("ret", "-1")))));
    }

    @Test
    void removeLeaseUsesExactIdAndLeaseExposesRawId() throws Exception {
        RouterOsOperation<Void> remove = DhcpServerApi.removeLeaseOperation("*A");
        assertEquals("/ip/dhcp-server/lease/remove", remove.command().path());
        assertEquals("*A", remove.command().arguments().get(".id"));

        DhcpLease lease = mapper.map(RouterOsRecord.of(Map.of(
                ".id", "*A",
                "address", "192.0.2.10")));
        assertEquals("*A", lease.id().orElseThrow());
    }

    @Test
    void leaseLookupsVerifyActualPropertiesBeforeReturningId() throws Exception {
        var operation = DhcpServerApi.findByAddressOperation("192.0.2.10", mapper);
        var record = operation.map(new CommandResult(List.of(
                RouterOsRecord.of(Map.of(".id", "*1", "address", "192.0.2.11",
                        "expires-after", "bad-time")),
                RouterOsRecord.of(Map.of(".id", "*2", "address", "192.0.2.10")),
                RouterOsRecord.of(Map.of(".id", "*3", "mac-address", "02:00:00:00:00:01"))),
                RouterOsRecord.empty()));
        assertEquals("*2", record.orElseThrow().id().orElseThrow());
    }

    @Test
    void macLookupMatchesCaseInsensitivelyAndCountsOnlyMatchingRows() throws Exception {
        var operation = DhcpServerApi.findByMacOperation("02:00:00:00:00:0A", mapper);
        var one = operation.map(new CommandResult(List.of(
                RouterOsRecord.of(Map.of(".id", "*1", "mac-address", "02:00:00:00:00:0a")),
                RouterOsRecord.of(Map.of(".id", "*2", "mac-address", "02:00:00:00:00:0B"))),
                RouterOsRecord.empty()));
        assertEquals("*1", one.orElseThrow().id().orElseThrow());

        assertThrows(MikrotikDataException.class, () -> operation.map(new CommandResult(List.of(
                RouterOsRecord.of(Map.of(".id", "*1", "mac-address", "02:00:00:00:00:0A")),
                RouterOsRecord.of(Map.of(".id", "*3", "mac-address", "02:00:00:00:00:0a"))),
                RouterOsRecord.empty())));
    }

    @Test
    void unfilteredLeaseCountStillUsesCountOnly() throws Exception {
        var operation = DhcpServerApi.countLeasesOperation(RouterOsProperties.builder().build());
        assertEquals(List.of("count-only"), operation.command().flags());
        assertEquals(3L, operation.map(new CommandResult(
                List.of(), RouterOsRecord.of(Map.of("ret", "3")))));
    }

}
