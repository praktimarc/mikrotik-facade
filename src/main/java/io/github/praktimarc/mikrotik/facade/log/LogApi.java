package io.github.praktimarc.mikrotik.facade.log;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.internal.stream.RawWordStreams;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Continuous typed RouterOS log streaming.
 *
 * <p>Both methods are cold: each subscription starts a separate native
 * {@code /log/print} command, returns each {@code !re} as a
 * {@link RouterLogEntry}, and remains active until the router finishes,
 * fails, or the subscriber calls {@link Flow.Subscription#cancel()}.
 * The finite command timeout does not apply. The buffer and tag cancellation
 * are managed by the same infrastructure as raw streaming.</p>
 *
 * <p>Follow flags are based on the documented RouterOS print options.
 * Firmware-specific behavior of their binary API representation must be
 * verified on real RouterOS devices before production use.</p>
 */
public final class LogApi {
    private static final List<String> FOLLOW = List.of("/log/print", "=follow=");
    private static final List<String> FOLLOW_ONLY = List.of("/log/print", "=follow-only=");
    private final RawWordStreams streams;

    /** Creates the typed log API for an authenticated facade session. */
    public LogApi(RawWordStreams streams) {
        this.streams = Objects.requireNonNull(streams, "streams");
    }

    /**
     * Includes current log history followed by new messages where supported
     * by the connected RouterOS version.
     *
     * @return cold stream of typed log entries
     * @throws MikrotikConnectionException if the session has failed or is closed
     */
    public Flow.Publisher<RouterLogEntry> follow() throws MikrotikConnectionException {
        return streams.streamMappedWords(FOLLOW, "follow RouterOS log", RouterLogEntry::new);
    }

    /**
     * Watches only newly arriving log messages where supported by the router.
     *
     * @return cold stream of typed log entries
     * @throws MikrotikConnectionException if the session has failed or is closed
     */
    public Flow.Publisher<RouterLogEntry> followOnly() throws MikrotikConnectionException {
        return streams.streamMappedWords(FOLLOW_ONLY,
                "follow only new RouterOS log entries", RouterLogEntry::new);
    }
}
