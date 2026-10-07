package io.github.praktimarc.mikrotik.facade.system.internal;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PingResultMapperTest {
    private final PingResultMapper mapper = new PingResultMapper();

    @Test
    void mapsRepliesAndCumulativeSummaryFromReplyStream() throws Exception {
        var result = mapper.map(CommandResult.ofRaw(List.of(
                Map.ofEntries(
                        Map.entry("seq", "0"), Map.entry("host", "192.0.2.1"), Map.entry("size", "56"),
                        Map.entry("ttl", "64"), Map.entry("time", "417us"), Map.entry("sent", "1"),
                        Map.entry("received", "1"), Map.entry("packet-loss", "0"),
                        Map.entry("min-rtt", "417us"), Map.entry("avg-rtt", "417us"), Map.entry("max-rtt", "417us")),
                Map.ofEntries(
                        Map.entry("seq", "1"), Map.entry("host", "192.0.2.1"), Map.entry("size", "56"),
                        Map.entry("ttl", "64"), Map.entry("time", "1ms438us"), Map.entry("sent", "2"),
                        Map.entry("received", "2"), Map.entry("packet-loss", "0%"),
                        Map.entry("min-rtt", "417us"), Map.entry("avg-rtt", "927us"), Map.entry("max-rtt", "1ms438us"))),
                Map.of()));

        assertEquals(2, result.replies().size());
        assertEquals(2L, result.sent());
        assertEquals(2L, result.received());
        assertEquals(0, result.packetLossPercent());
        assertEquals(Duration.ofNanos(417_000), result.minRtt().orElseThrow());
        assertEquals(Duration.ofNanos(1_438_000), result.maxRtt().orElseThrow());
    }

    @Test
    void acceptsSummaryFromDoneProperties() throws Exception {
        var result = mapper.map(CommandResult.ofRaw(
                List.of(Map.of("seq", "0", "status", "timeout")),
                Map.of("sent", "1", "received", "0", "packet-loss", "100%")));

        assertEquals(1, result.replies().size());
        assertEquals("timeout", result.replies().get(0).status().orElseThrow());
        assertEquals(100, result.packetLossPercent());
        assertTrue(result.minRtt().isEmpty());
    }

    @Test
    void hundredPercentLossIsNormalData() throws Exception {
        var result = mapper.map(CommandResult.ofRaw(
                List.of(
                        Map.of("seq", "0", "status", "timeout"),
                        Map.of("seq", "1", "status", "timeout"),
                        Map.of("sent", "2", "received", "0", "packet-loss", "100")),
                Map.of()));
        assertEquals(0L, result.received());
        assertEquals(100, result.packetLossPercent());
    }

    @Test
    void multicastMayProduceMoreRepliesThanRequestsAndNegativeLoss() throws Exception {
        var result = mapper.map(CommandResult.ofRaw(
                List.of(Map.of("sent", "1", "received", "4", "packet-loss", "-300%")),
                Map.of()));
        assertEquals(1L, result.sent());
        assertEquals(4L, result.received());
        assertEquals(-300, result.packetLossPercent());
    }

    @Test
    void missingSummaryIsDataError() {
        assertThrows(MikrotikDataException.class,
                () -> mapper.map(CommandResult.ofRaw(
                        List.of(Map.of("seq", "0", "host", "192.0.2.1")),
                        Map.of())));
    }

    @Test
    void malformedLossAndNegativePacketCountersAreDataErrors() {
        assertThrows(MikrotikDataException.class,
                () -> mapper.map(CommandResult.ofRaw(
                        List.of(Map.of("sent", "1", "received", "1", "packet-loss", "oops%")),
                        Map.of())));
        assertThrows(MikrotikDataException.class,
                () -> mapper.map(CommandResult.ofRaw(
                        List.of(Map.of("sent", "-1", "received", "0", "packet-loss", "100%")),
                        Map.of())));
    }
}
