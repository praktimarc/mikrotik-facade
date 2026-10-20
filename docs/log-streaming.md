# Typed RouterOS log streaming

`api.log().follow()` and `api.log().followOnly()` are convenient typed
wrappers around the [native Raw-Streaming API](raw-streaming.md).

The methods return a **cold** `Flow.Publisher<RouterLogEntry>`, not a
`CompletableFuture` or an already-running task. Each subscriber starts one
independent tagged `/log/print` operation. Stop it through the received
`Flow.Subscription`.

- `follow()` sends the exact words
  `["/log/print", "=follow="]`. RouterOS is expected to send existing log
  entries followed by new entries when the binary API option is supported.
- `followOnly()` sends `["/log/print", "=follow-only="]`. RouterOS is expected
  to report only entries arriving after the command starts.

**Important:** MikroTik documents these options for RouterOS `print`.
Their exact operation through the binary API still needs confirmation on
real RouterOS 6 and 7 installations. The facade does **not** silently
fall back to polling or execute a CLI command. Unsupported variants cause
`onError`.

## Java example: view log records and stop on request

```java
import io.github.praktimarc.mikrotik.facade.log.RouterLogEntry;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

// api is an already authenticated MikrotikRtrApi
AtomicReference<Flow.Subscription> active = new AtomicReference<>();

api.log().followOnly().subscribe(new Flow.Subscriber<RouterLogEntry>() {
    @Override public void onSubscribe(Flow.Subscription subscription) {
        active.set(subscription);
        subscription.request(1);
    }

    @Override public void onNext(RouterLogEntry item) {
        System.out.printf("%s [%s] %s%n",
                item.time().orElse("?"),
                String.join(",", item.topics()),
                item.message().orElse(""));

        // Request another entry only while still active.
        Flow.Subscription subscription = active.get();
        if (subscription != null) subscription.request(1);
    }

    @Override public void onError(Throwable error) {
        active.set(null);
        System.err.println("Log stream failed: "
                + error.getClass().getSimpleName());
    }

    @Override public void onComplete() {
        active.set(null);
        System.out.println("Log stream completed");
    }
});

// When the user clicks Stop or the screen is disposed:
Flow.Subscription subscription = active.getAndSet(null);
if (subscription != null) subscription.cancel();
```

**Do not close the API session immediately after calling `subscribe()`.**
Keep it alive until the user stops watching or the application shuts down.
The example omits connection creation and UI wiring; see
[getting started](getting-started.md).

## RouterLogEntry fields

| Java method | Type | Meaning |
| --- | --- | --- |
| `id()` | `Optional<String>` | RouterOS `.id` if present |
| `time()` | `Optional<String>` | Exact RouterOS time/date text |
| `topics()` | `List<String>` | Comma-separated topics split and trimmed, in original order |
| `topicsText()` | `Optional<String>` | Original `topics` property without normalization |
| `message()` | `Optional<String>` | RouterOS message text |
| `raw()` | `RouterOsRecord` | All returned fields, including unknown future properties |

The data model is immutable. Missing properties are represented as empty
`Optional` values or empty topic lists, **not** synthetic timestamps or
empty fabricated log messages.

RouterOS may report a time-of-day only, date without year, or a full date
and time depending on context and firmware. Therefore `time()` is **not**
converted to `Instant`, and is not implicitly interpreted as the Java
client's local timezone. If absolute time is needed, the consuming
application must supply the router's timezone and date/year context, or
obtain a guaranteed absolute timestamp from another source.

Topic splitting provides convenient matching:

```java
if (entry.topics().contains("firewall")) {
    // Handle a firewall log message.
}
```

`raw()` is retained for fields added by future RouterOS versions; do not
dump raw records to shared logs, as messages may contain usernames, IPs,
client identifiers, or other sensitive data.

## Cancellation, connection loss, and backpressure

This API uses exactly the same stream lifecycle as `raw().streamWords()`:
256 queued records per subscription, serialized Flow callbacks,
`/cancel` by the internally managed RouterOS tag, and cancellation of
active streams when the facade session closes.

There is **no finite-command overall timeout**. Fatal connection loss
causes `onError`; an unresponsive but still-open TCP connection might
require external health monitoring. A subscriber that stops requesting
records will eventually fill the bounded queue and receive
`MikrotikBackpressureException` rather than lose records silently.
Cancellation does not guarantee `onComplete`; update the UI when requesting
the stop.

The methods are read-only RouterOS `print` commands. They do not update
RouterOS logging configuration, persist history, or recreate events lost
before the subscriber starts. For durable long-term log storage, use
Remote Syslog rather than relying on finite RouterOS memory log buffers.

## Joint device verification checklist (not yet performed)

For each RouterOS 6 and 7 target device and software version:

1. Establish a least-privilege API/API-SSL connection. Record the RouterOS
   version, package configuration, and test-time timezone (not credentials).
2. Start `api.log().followOnly()`. Confirm no historical events are emitted.
   Create one harmless test log entry in the router terminal using
   `:log info "typed-log-stream-smoke"`, then check that exactly one
   matching new entry is delivered. Use a unique identifier for each run.
3. Cancel through the subscription. Confirm no later callback is delivered
   to the cancelled subscriber and other facade commands still work.
4. Repeat with `api.log().follow()` and verify that previous memory-log
   entries are delivered before the newly generated one, where supported.
5. Run two simultaneous log subscriptions, cancel one, and verify the other
   still receives newly generated log messages.
6. Intentionally close a *test session* and verify active stream cleanup.
   For a separate connection-loss test, terminate the test connection
   externally and verify `onError`, without disrupting production routers.
7. Compare event ordering and behavior across RouterOS versions. If one
   version rejects `=follow=` or `=follow-only=`, record the RouterOS trap
   rather than assuming binary API support.

The existing JUnit tests verify client-side word framing, mapping,
session lifecycle, errors, cancellation and topic parsing with fake
connections. They do **not** prove that a live RouterOS device will accept
the follow arguments or emit notifications as expected.

References:
- [MikroTik Log manual](https://manual.mikrotik.com/docs/diagnostics-monitoring-and-troubleshooting/log/)
- [MikroTik scripting / print flags](https://manual.mikrotik.com/docs/developer-guides/scripting/)
- [MikroTik binary API](https://manual.mikrotik.com/docs/developer-guides/api/)
- [Raw-Streaming guide](raw-streaming.md)
