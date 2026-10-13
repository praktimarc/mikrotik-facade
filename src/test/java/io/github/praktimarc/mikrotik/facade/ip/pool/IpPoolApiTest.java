package io.github.praktimarc.mikrotik.facade.ip.pool;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;
import io.github.praktimarc.mikrotik.facade.ip.pool.internal.IpPoolMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IpPoolApiTest {
    @Test
    void poolsPreserveOneRouterOsRowAndSplitMultipleRanges() throws Exception {
        RouterOsOperation<List<IpPool>> operation =
                IpPoolApi.listOperation(new IpPoolMapper());

        assertEquals("/ip/pool/print", operation.command().path());

        List<IpPool> pools = operation.map(new CommandResult(
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


}
