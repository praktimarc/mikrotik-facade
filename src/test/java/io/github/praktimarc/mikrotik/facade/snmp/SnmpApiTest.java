package io.github.praktimarc.mikrotik.facade.snmp;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.snmp.internal.SnmpCommunityMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnmpApiTest {
    @Test
    void communityMappingExposesStableFieldsButRetainsCredentialFieldsOnlyInRaw() throws Exception {
        SnmpCommunity community = new SnmpCommunityMapper().map(
                io.github.praktimarc.mikrotik.facade.RouterOsRecord.of(Map.ofEntries(
                        Map.entry(".id", "*1"),
                        Map.entry("name", "example-community"),
                        Map.entry("address", "0.0.0.0/0"),
                        Map.entry("security", "private"),
                        Map.entry("read-access", "true"),
                        Map.entry("write-access", "false"),
                        Map.entry("authentication-protocol", "SHA1"),
                        Map.entry("encryption-protocol", "AES"),
                        Map.entry("authentication-password", "test-only-auth"),
                        Map.entry("encryption-password", "test-only-privacy"))));

        assertEquals("example-community", community.name());
        assertFalse(community.writeAccess().orElseThrow());
        assertEquals("test-only-auth", community.raw().find("authentication-password").orElseThrow());
    }

    @Test
    void findByNameDoesNotPutCommunityValueIntoCommandQuery() throws Exception {
        var operation = SnmpApi.findCommunityByNameOperation("example-community", new SnmpCommunityMapper());
        assertEquals("/snmp/community/print", operation.command().path());
        assertTrue(operation.command().queries().isEmpty());

        var result = operation.map(CommandResult.ofRaw(
                List.of(
                        Map.of(".id", "*1", "name", "public"),
                        Map.of(".id", "*2", "name", "example-community", "write-access", "false")),
                Map.of()));

        assertEquals("*2", result.orElseThrow().id().orElseThrow());
    }

    @Test
    void duplicateCommunityNameIsDataError() {
        var operation = SnmpApi.findCommunityByNameOperation("example-community", new SnmpCommunityMapper());
        assertThrows(io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException.class,
                () -> operation.map(CommandResult.ofRaw(
                        List.of(
                                Map.of(".id", "*1", "name", "example-community"),
                                Map.of(".id", "*2", "name", "example-community")),
                        Map.of())));
    }

    @Test
    void writeAccessMutationUsesExactIdAndSameSessionCommandShape() {
        var operation = SnmpApi.setCommunityPropertiesOperation(
                "*2",
                RouterOsProperties.builder().set("write-access", "true").build());
        assertEquals("/snmp/community/set", operation.command().path());
        assertEquals("*2", operation.command().arguments().get(".id"));
        assertEquals("true", operation.command().arguments().get("write-access"));
    }

    @Test
    void genericMutationRejectsIdOverrideAndEmptyProperties() {
        assertThrows(IllegalArgumentException.class, () -> SnmpApi.setCommunityPropertiesOperation(
                "*2", RouterOsProperties.builder().build()));
        assertThrows(IllegalArgumentException.class, () -> SnmpApi.setCommunityPropertiesOperation(
                "*2", RouterOsProperties.builder().set(".id", "*9").build()));
    }
}
