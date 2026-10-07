package io.github.praktimarc.mikrotik.facade.firewall.mangle;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.firewall.FirewallRule;
import io.github.praktimarc.mikrotik.facade.firewall.internal.FirewallRuleMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MangleApiTest {
    @Test void propertyBasedFindUsesManglePrintAndDoesNotDeduplicateRows() throws Exception {
        RouterOsProperties queries=RouterOsProperties.builder().set("src-address","192.0.2.10").build();
        RouterOsOperation<List<FirewallRule>> operation=MangleApi.findOperation(queries,new FirewallRuleMapper());
        RouterOsRecord duplicate=RouterOsRecord.of(Map.of(".id","*A","src-address","192.0.2.10"));
        List<FirewallRule> rules=operation.map(new CommandResult(List.of(duplicate,duplicate),RouterOsRecord.empty()));
        assertEquals("/ip/firewall/mangle/print",operation.command().path());
        assertEquals("192.0.2.10",operation.command().queries().get("src-address"));
        assertEquals(2,rules.size());
    }
    @Test void removeUsesExactRouterOsId() throws Exception {
        var remove=MangleApi.removeOperation("*A");
        assertEquals("/ip/firewall/mangle/remove",remove.command().path());
        assertEquals(Map.of(".id","*A"),remove.command().arguments());
        assertNull(remove.map(new CommandResult(List.of(),RouterOsRecord.empty())));
    }
    @Test void setDisabledReusesSameSessionOperationContract() {
        var set=MangleApi.setDisabledOperation("*B",false);
        assertEquals("/ip/firewall/mangle/set",set.command().path());
        assertEquals("*B",set.command().arguments().get(".id"));
        assertEquals("false",set.command().arguments().get("disabled"));
        assertThrows(IllegalArgumentException.class,()->MangleApi.removeOperation("  "));
    }
}
