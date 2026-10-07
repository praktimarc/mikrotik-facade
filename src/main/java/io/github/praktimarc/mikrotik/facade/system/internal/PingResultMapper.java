package io.github.praktimarc.mikrotik.facade.system.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.system.PingReply;
import io.github.praktimarc.mikrotik.facade.system.PingResult;

import java.util.ArrayList;
import java.util.List;

/** Maps finite RouterOS ping records and terminal summary data. */
public final class PingResultMapper {
    public PingResult map(CommandResult result) throws MikrotikDataException {
        RouterOsRecord summary = findSummary(result);
        long sent = summary.requireLong("sent");
        long received = summary.requireLong("received");
        int packetLoss = parsePacketLoss(summary.require("packet-loss"));

        ArrayList<PingReply> replies = new ArrayList<>();
        for (RouterOsRecord record : result.records()) {
            if (isReplyRecord(record)) {
                replies.add(mapReply(record));
            }
        }

        try {
            return new PingResult(
                    summary,
                    List.copyOf(replies),
                    sent,
                    received,
                    packetLoss,
                    summary.getDuration("min-rtt"),
                    summary.getDuration("avg-rtt"),
                    summary.getDuration("max-rtt"));
        } catch (IllegalArgumentException inconsistent) {
            throw new MikrotikDataException("RouterOS ping summary contains inconsistent counters", inconsistent);
        }
    }

    private static RouterOsRecord findSummary(CommandResult result) throws MikrotikDataException {
        if (hasSummary(result.completion())) {
            return result.completion();
        }
        List<RouterOsRecord> records = result.records();
        for (int i = records.size() - 1; i >= 0; i--) {
            if (hasSummary(records.get(i))) {
                return records.get(i);
            }
        }
        throw new MikrotikDataException("RouterOS ping completed without a usable summary");
    }

    private static boolean hasSummary(RouterOsRecord record) {
        return record.find("sent").isPresent()
                && record.find("received").isPresent()
                && record.find("packet-loss").isPresent();
    }

    private static boolean isReplyRecord(RouterOsRecord record) {
        return record.find("seq").isPresent()
                || record.find("host").isPresent()
                || record.find("size").isPresent()
                || record.find("ttl").isPresent()
                || record.find("time").isPresent()
                || record.find("status").isPresent();
    }

    private static PingReply mapReply(RouterOsRecord raw) throws MikrotikDataException {
        return new PingReply(
                raw,
                raw.getLong("seq"),
                raw.find("host"),
                raw.getLong("size"),
                raw.getLong("ttl"),
                raw.getDuration("time"),
                raw.find("status"));
    }

    private static int parsePacketLoss(String raw) throws MikrotikDataException {
        String value = raw.endsWith("%") ? raw.substring(0, raw.length() - 1) : raw;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            throw new MikrotikDataException(
                    "RouterOS property 'packet-loss' cannot be converted to percent",
                    malformed);
        }
    }
}
