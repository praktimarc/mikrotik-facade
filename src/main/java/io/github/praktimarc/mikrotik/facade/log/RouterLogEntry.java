package io.github.praktimarc.mikrotik.facade.log;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable typed view of one RouterOS {@code /log/print} result.
 *
 * <p>The RouterOS timestamp is deliberately retained as text. RouterOS may
 * return a time of day, month/day plus time, or a full date and time; without
 * the router's timezone and date context it cannot safely be turned into an
 * absolute Java {@code Instant}.</p>
 */
public final class RouterLogEntry implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final Optional<String> id;
    private final Optional<String> time;
    private final Optional<String> topicsText;
    private final List<String> topics;
    private final Optional<String> message;

    public RouterLogEntry(RouterOsRecord raw) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.id = raw.find(".id");
        this.time = raw.find("time");
        this.topicsText = raw.find("topics");
        this.message = raw.find("message");
        this.topics = parseTopics(topicsText);
    }

    /** Optional RouterOS item ID; not all stream records provide it. */
    public Optional<String> id() { return id; }

    /** RouterOS timestamp exactly as supplied, without timezone guessing. */
    public Optional<String> time() { return time; }

    /** Individual topics, in RouterOS order (e.g. {@code system}, {@code info}). */
    public List<String> topics() { return topics; }

    /** Original unsplit topic string, if present. */
    public Optional<String> topicsText() { return topicsText; }

    /** Log message as supplied; it may contain sensitive or private values. */
    public Optional<String> message() { return message; }

    /** Complete immutable RouterOS record, including unrecognized properties. */
    @Override public RouterOsRecord raw() { return raw; }

    private static List<String> parseTopics(Optional<String> rawTopics) {
        if (rawTopics.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String topic : rawTopics.orElseThrow().split(",", -1)) {
            String cleaned = topic.trim();
            if (!cleaned.isEmpty()) result.add(cleaned);
        }
        return List.copyOf(result);
    }
}
