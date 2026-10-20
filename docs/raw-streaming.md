# Exact RouterOS API words — continuous streaming

The general-purpose streaming API is for RouterOS commands that emit
multiple successive API `!re` sentences: e.g. `/interface/listen`,
`/user/active/listen`, or a supported `/log/print` follow mode.

This uses the **native RouterOS binary API**, not a console parser or REST.
A supplied word is forwarded verbatim through the low-level
`ApiConnection.executeWords(List<String>, ResultListener)`.

The stream API is **different** from `api.raw().words(...)`: finite
operations return collected results, while a stream delivers records
incrementally and may continue indefinitely. The latter is implemented as
Java 17 `Flow.Publisher<RouterOsRecord>`.

For typed log entries with split topics and preserved RouterOS timestamps,
use the [typed Log API guide](log-streaming.md) and
`api.log().follow()` / `api.log().followOnly()`.

## Start a live log

```java
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

// Assume a connected, authenticated MikrotikRtrApi named "api".
AtomicReference<Flow.Subscription> active = new AtomicReference<>();

Flow.Publisher<RouterOsRecord> publisher =
        api.raw().streamWords("/log/print", "=follow-only=");

publisher.subscribe(new Flow.Subscriber<RouterOsRecord>() {
    @Override public void onSubscribe(Flow.Subscription subscription) {
        active.set(subscription);
        subscription.request(1);
    }

    @Override public void onNext(RouterOsRecord row) {
        // Keep callbacks short; log messages may include private information.
        System.out.printf("%s [%s] %s%n",
                row.find("time").orElse("?"),
                row.find("topics").orElse("?"),
                row.find("message").orElse(""));
        Flow.Subscription subscription = active.get();
        if (subscription != null) subscription.request(1);
    }

    @Override public void onError(Throwable error) {
        active.set(null);
        System.err.println("RouterOS log stream failed: "
                + error.getClass().getSimpleName());
    }

    @Override public void onComplete() {
        active.set(null);
        System.out.println("RouterOS stream completed");
    }
});

// On user Stop, UI disposal, or application shutdown:
Flow.Subscription subscription = active.getAndSet(null);
if (subscription != null) subscription.cancel();
```

The `=follow-only=` argument is the native API-word representation of
a valueless print option. The RouterOS CLI documents `follow` and
`follow-only`; **firmware support of the corresponding binary API command
and attribute needs to be confirmed on each target RouterOS family**.
The facade does not emulate follow mode, poll, or turn CLI syntax into
native words. An unsupported command fails through `onError`.

For initial history *and* new messages, try `=follow=` instead (if
supported by the router). For continuously changing item tables,
`/listen` is explicitly documented by MikroTik's binary API.

## Monitor changes without a fixed expiry

```java
var publisher = api.raw().streamWords(
        "/user/active/listen");

// One subscriber = one tagged RouterOS command.
// Sample replies can include:
// {".id":"*A","name":"admin", ...}
// {".id":"*A",".dead":"yes"}
```

When a property disappears or an item is deleted, `.dead=yes` may be
returned by `/listen`. The binary API manual warns that `listen` does
not behave correctly in every menu. Inspect supported fields before
depending on a particular menu.

The publisher also exists in the async tree for API symmetry:

```java
Flow.Publisher<RouterOsRecord> same =
        api.async().raw().streamWords(
                "/log/print", "=follow-only=");
```

There is no `CompletableFuture<List<RouterOsRecord>>` to await. Subscribe,
request demand and cancel explicitly.

## Exact-word overloads

Both facades accept two forms:

```java
// Convenience overload:
api.raw().streamWords("/log/print", "=follow-only=");

// Immutable complete sentence:
api.raw().streamWords(java.util.List.of(
        "/log/print",
        "=follow-only=",
        "=.proplist=time,topics,message"));

// Example of an API-level listener:
api.raw().streamWords("/interface/listen");
```

Each string is a complete RouterOS protocol word. Query words such as
`?disabled=false` may be sent where the target command actually supports
them. RouterOS `.tag` belongs to the session: **never supply it manually**.
Invalid paths, empty words, NUL characters and caller tags are rejected.
The facade does not implement regex query matching or client-side
filtering on the raw stream. Values and ordering are not normalized.

The methods accept arbitrary commands; **use a least-privilege account**.
They are not limited to safe, read-only operation. Do not stream sensitive
records into shared diagnostics or application logs.

## Lifecycle, buffering, and errors

- **Cold:** constructing the publisher sends nothing. Each subscription
  starts its own tagged RouterOS operation; repeated subscriptions do not
  share an existing stream.
- **Cancellation:** `Flow.Subscription.cancel()` cancels **only that
  operation** by its tag, on a best-effort basis. The connection remains
  usable by other subscribers and finite commands. Cancellation may race
  with a command's result or an already executed mutation.
- **Session close:** the active-stream registry cancels streams before
  the connection is intentionally closed. The public `Flow` contract does
  **not** promise `onComplete` or `onError` after cancellation. Update UI
  state when invoking `cancel()` rather than waiting for a terminal signal.
- **Connection loss:** the low-level library terminates outstanding commands
  upon fatal connection loss, triggering the subscriber's `onError`. The
  facade does not reconnect or automatically restart a follow command.
  A completely silent network failure may require separate transport
  health checks; this API does not add a heartbeat.
- **Timeout:** the ordinary finite-command `commandTimeout` is not
  applied to `streamWords()`, allowing long-running commands. Timeouts
  imposed by RouterOS or network infrastructure still apply.
- **Flow demand:** the subscriber must call `request(n)` with positive
  values. Events arrive in order and callbacks for a given subscription
  are serialized.
- **Bounded buffer:** each subscription has capacity for **256**
  undelivered records. If it fills, the stream fails with
  `MikrotikBackpressureException` and best-effort cancels RouterOS.
  Records are not silently discarded while claiming continued streaming.
  Keep callbacks quick; batch JavaFX UI updates outside the subscriber.
- **Terminal responses:** ordinary `!re` records are mapped to
  `RouterOsRecord`. A successful `!done` causes `onComplete` after
  buffered records are consumed. A `!trap` or transport error maps to
  `onError`. Completion metadata is not surfaced as a stream item.

A subscription may fail if RouterOS does not support the supplied menu,
options or version. The facade does not inspect available menu capabilities
before sending the words.

## Tests and verification

The repository's loopback/fake-connection tests cover the exact native
word path, publisher coldness, independent command tags, ordered records,
successful completion, low-level failures, buffer overflow, cancellation
before the command tag is assigned, session-close cancellation and
preservation of existing structured interface monitoring.

They verify client-side protocol dispatch and stream lifecycle — **not**
the behavior of a particular RouterOS appliance. Before declaring
production support for `/log/print =follow-only=`, run an authorized
read-only test on the RouterOS versions and hardware in use. Check that
the router emits entries continually, that stop cancels promptly, and
that active unrelated API operations remain unaffected.

**Authoritative references:**
- [MikroTik binary API: tags, listen and /cancel](https://manual.mikrotik.com/docs/developer-guides/api/)
- [MikroTik Scripting: print follow and follow-only](https://help.mikrotik.com/docs/spaces/ROS/pages/47579229/Scripting)
- [Raw protocol words and query syntax](raw-protocol-words.md)
- [Interface traffic monitoring example](getting-started.md)
