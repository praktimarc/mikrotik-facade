package io.github.praktimarc.mikrotik.facade.ip.firewall.filter;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.ip.firewall.FirewallRule;
import io.github.praktimarc.mikrotik.facade.ip.firewall.internal.FirewallRuleMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FilterApiTest {
    @Test void propertyBasedFindUsesFilterPrintAndMapsRules() throws Exception {
        RouterOsProperties queries=RouterOsProperties.builder().set("chain","forward").set("comment","Example").build();
        RouterOsOperation<List<FirewallRule>> operation=FilterApi.findOperation(queries,new FirewallRuleMapper());
        assertEquals("/ip/firewall/filter/print",operation.command().path());
        assertEquals(queries.asMap(),operation.command().queries());
        List<FirewallRule> rules=operation.map(new CommandResult(List.of(RouterOsRecord.of(Map.of(".id","*1","chain","FORWARD","comment","example","future","kept"))),RouterOsRecord.empty()));
        assertEquals(1,rules.size());
        assertEquals("kept",rules.get(0).raw().find("future").orElseThrow());
    }
    @Test void emptyFilterResultIsNormal() throws Exception {
        var operation=FilterApi.findOperation(RouterOsProperties.builder().build(),new FirewallRuleMapper());
        assertTrue(operation.map(new CommandResult(List.of(),RouterOsRecord.empty())).isEmpty());
    }
    @Test void flexibleAddPreservesPropertiesAndRetAndDisabledConvenienceUsesId() throws Exception {
        RouterOsProperties properties=RouterOsProperties.builder().set("chain","forward").set("comment","Example").build();
        RouterOsOperation<Optional<String>> add=FilterApi.addOperation(properties);
        assertEquals(properties.asMap(),add.command().arguments());
        assertEquals("*9",add.map(new CommandResult(List.of(),RouterOsRecord.of(Map.of("ret","*9")))).orElseThrow());
        var set=FilterApi.setDisabledOperation("*9",true);
        assertEquals("*9",set.command().arguments().get(".id"));
        assertEquals("true",set.command().arguments().get("disabled"));
    }
    @Test void serverQueryCannotIntroduceUnrelatedFirewallRules() throws Exception {
        var query = RouterOsProperties.builder().set("chain", "forward").set("comment", "approved").build();
        var operation = FilterApi.findOperation(query, new FirewallRuleMapper());
        var rows = List.of(
                RouterOsRecord.of(Map.of(".id", "*1", "chain", "forward", "comment", "approved")),
                RouterOsRecord.of(Map.of(".id", "*2", "chain", "input",
                        "comment", "approved", "bytes", "invalid")),
                RouterOsRecord.of(Map.of(".id", "*3", "chain", "forward", "comment", "other")),
                RouterOsRecord.of(Map.of(".id", "*4", "chain", "forward")));
        var result = operation.map(new CommandResult(rows, RouterOsRecord.empty()));
        assertEquals(1, result.size());
        assertEquals("*1", result.get(0).id().orElseThrow());
    }

}
