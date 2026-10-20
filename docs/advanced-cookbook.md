# Advanced facade cookbook

These are application-facing examples for the **typed** Java 17
`mikrotik-facade` API. Start with the [practical cookbook](examples.md)
for installation, connection and the basic read operations.
For native binary protocol debugging use the separate
[Raw-Words Cookbook](raw-protocol-words.md).
This guide follows the current public `0.1.0-SNAPSHOT` API. It is not an
inventory of RouterOS CLI or REST capabilities.

Every snippet assumes an authenticated, connected `MikrotikRtrApi api`
inside a try-with-resources scope. Addresses (`192.0.2.0/24`), names and
MACs are documentation-only values. The router must support the referenced
menu. Operations involving changes are **disabled by default** and require
an explicit decision in the application, appropriate permissions and a
controlled test router.

## 1. DHCP leases: identity, status and Option 82

```java
var candidate = api.ip().dhcpServer().lease()
        .findByMac("02:00:00:00:00:01");

candidate.ifPresent(lease -> {
    System.out.println("ID: " + lease.id().orElse("(not returned)"));
    System.out.println("Address: " + lease.address().orElse("(not returned)"));
    System.out.println("Server: " + lease.server().orElse("(not returned)"));
    System.out.println("State: " + lease.status().orElse("(not returned)"));

    // Option 82 fields are optional and depend on relay/lease state.
    System.out.println("Circuit ID present: " + lease.agentCircuitId().isPresent());
    System.out.println("Remote ID present: " + lease.agentRemoteId().isPresent());
    System.out.println("Active client ID present: " + lease.activeClientId().isPresent());
    System.out.println("Dynamic: " + lease.dynamic().orElse(false));
});
```

`findByAddress` is also available. Both expected-single lookups verify
returned equality properties locally and reject ambiguous duplicates.
An empty `Optional` means no match, **not** a transport failure.
Do not decode DHCP Option 82 fields speculatively: the original RouterOS
representation is retained in `lease.raw()` for authorized diagnostics.

Count all leases versus matching leases:

```java
var empty = RouterOsProperties.builder().build();
var byServer = RouterOsProperties.builder()
        .set("server", "example-dhcp")
        .build();

long all = api.ip().dhcpServer().lease().count(empty);
long selected = api.ip().dhcpServer().lease().count(byServer);
System.out.println("All: " + all + ", server: " + selected);
```

Unfiltered `count` uses RouterOS `count-only` completion metadata.
Filtered counts download records and verify equality locally; on large
lease tables that has a bandwidth cost. Server-side filters that *omit*
matching rows cannot be repaired by local verification.

For guarded ID-based lease deletion see
[practical cookbook `12](examples.md#12-remove-a-dhcp-lease-by-id-destructive-explicitly-opt-in).
Never pass an IP address directly to `remove(String id)`.

## 2. Firewall Filter and Mangle: locate, inspect, change deliberately

For filtering use `RouterOsProperties`. Typed find operations locally verify
the RouterOS properties that were returned. Rules with a missing queried
property are not counted as matches.

```java
var forward = api.ip().firewall().filter().find(
        RouterOsProperties.builder().set("chain", "forward").build());
for (var rule : forward) {
    System.out.println("Rule ID: " + rule.id().orElse("(missing)"));
    System.out.println("Disabled: " + rule.disabled().orElse(false));
    System.out.println("Packets: " + rule.packets());
}

// Separate typed Mangle find conveniences:
var bySource = api.ip().firewall().mangle()
        .findBySourceAddress("192.0.2.0/24");
var byDestination = api.ip().firewall().mangle()
        .findByDestinationAddress("198.51.100.0/24");
var byComment = api.ip().firewall().mangle()
        .findByComment("example-marker");
```

The methods `filter().findByComment` and `mangle().find` are also
available. RouterOS has no atomic read-modify-write transaction across these
calls; recheck identity before a later write.

### Controlled mutation (never runs as copied)

The code below is **deliberately gated**. A permissive Firewall rule could
change network access. Use a test router and review the exact rule action,
chain, ordering and address scope before enabling the gate.

```java
boolean approvedForTestRouter = false;

if (approvedForTestRouter) {
    var props = RouterOsProperties.builder()
            .set("chain", "forward")
            .set("action", "drop")
            .set("disabled", "yes")
            .set("comment", "example-disabled-rule")
            .build();

    var createdId = api.ip().firewall().filter().add(props);
    if (createdId.isPresent()) {
        // Keep this example rule disabled; do not enable it here.
        api.ip().firewall().filter()
                .setDisabled(createdId.orElseThrow(), true);
    }
}
```

`add(...)` returns an `Optional<String>` containing RouterOS `ret` when
provided. Absence of `ret` does **not** mean the operation failed.
`setDisabled` requires a RouterOS item ID, never a comment, IP or position.

For Mangle, an explicitly approved test can also remove an item by exact ID.
The following example requires both operator approval and a single locally
validated match before deletion:

```java
boolean approvedForTestRouter = false;

if (approvedForTestRouter) {
    var matches = api.ip().firewall().mangle()
            .findByComment("example-owned-mangle-rule");
    if (matches.size() == 1) {
        var rule = matches.get(0);
        String exactId = rule.id().orElseThrow();
        api.ip().firewall().mangle().setDisabled(exactId, true);
        // Explicitly removing remains a separate destructive choice:
        // api.ip().firewall().mangle().remove(exactId);
    }
}
```

The ID might become stale between read and write. There is no automatic
rollback or concurrency lock. The public Filter API intentionally does
**not** expose `remove`; Mangle does.

## 3. Firewall Address Lists: read, add, update and remove

```java
var matches = api.ip().firewall().addressList()
        .findByListAndAddress("example-list", "192.0.2.10");

for (var entry : matches) {
    System.out.println("ID: " + entry.id().orElse("(missing)"));
    System.out.println("Dynamic: " + entry.dynamic().orElse(false));
    System.out.println("Timeout: " + entry.timeout());
}

var byList = api.ip().firewall().addressList().find(
        RouterOsProperties.builder()
                .set("list", "example-list")
                .build());
```

Use RouterOS property `list` for address-list entries.
The `address-list` property used on other firewall menus is a different
concept. A dynamic entry can expire and should not be treated as a stable
configuration object.

```java
boolean approvedForTestRouter = false;
if (approvedForTestRouter) {
    var properties = RouterOsProperties.builder()
            .set("list", "example-list")
            .set("address", "192.0.2.10")
            .set("timeout", "1h")
            .set("comment", "temporary-example")
            .build();

    var createdId = api.ip().firewall().addressList().add(properties);
    System.out.println("RouterOS returned an ID: " + createdId.isPresent());
}
```

The facade does not impose an application-specific uniqueness or
authorization policy for `add`. Check existing records first when needed.

### Update or remove an existing entry by RouterOS ID

Both `setProperties(id, properties)` and `remove(id)` require the exact
RouterOS `.id`, **not** an IP address or list name. This example is
disabled by default. It first checks the list and address, then verifies
that there is exactly one match with a returned ID. An update and
a removal are **separate** operator decisions.

```java
boolean approvedForTestRouter = false;

if (approvedForTestRouter) {
    var entries = api.ip().firewall().addressList()
            .findByListAndAddress("example-list", "192.0.2.10");

    if (entries.size() == 1) {
        var entry = entries.get(0);
        String routerOsId = entry.id().orElseThrow();

        api.ip().firewall().addressList().setProperties(
                routerOsId,
                RouterOsProperties.builder()
                        .set("comment", "test-update")
                        .set("disabled", "true")
                        .build());

        // Separate, explicitly destructive operation (not run by default):
        // api.ip().firewall().addressList().remove(routerOsId);
    }
}
```

For asynchronous use, apply the same approval and identity checks.
The following snippet is deliberately disabled:

```java
boolean approvedForAsyncTestRouter = false;

if (approvedForAsyncTestRouter) {
    var matches = api.ip().firewall().addressList()
            .findByListAndAddress("example-list", "192.0.2.10");
    if (matches.size() == 1) {
        String exactId = matches.get(0).id().orElseThrow();
        var change = api.async().ip().firewall().addressList()
                .setProperties(exactId,
                        RouterOsProperties.builder()
                                .set("comment", "approved-change")
                                .build());
        change.get(30, java.util.concurrent.TimeUnit.SECONDS);

        // Only after a separate explicit decision:
        // var removal = api.async().ip().firewall().addressList().remove(exactId);
    }
}
```

Both asynchronous calls **modify router configuration** when invoked.
Await or cancel returned futures before closing the session.
Empty update properties and replacing `.id` are rejected. The methods
do not perform a second identity lookup, provide rollback, or guarantee
that a dynamic/expired entry can be updated or deleted. RouterOS may
change state between the read and the write.

## 4. SNMP communities: protect sensitive fields

```java
var communities = api.snmp().community().list();
System.out.println("SNMP community count: " + communities.size());

var one = api.snmp().community().findByName("example-community");
one.ifPresent(entry -> {
    System.out.println("Has ID: " + entry.id().isPresent());
    System.out.println("Write access: " + entry.writeAccess());
    System.out.println("Disabled: " + entry.disabled());
});
```

A community name can be a credential. Do **not** log the actual community
name, `entry.raw()`, authentication material or complete exception causes.
`findByName` reads the communities and compares names locally so the
credential-like value is not placed in the RouterOS query.

For permitted changes, identify the item by RouterOS ID and validate that
the returned entry is the intended one. The following is disabled by default:

```java
boolean approvedForTestRouter = false;
if (approvedForTestRouter) {
    var item = api.snmp().community()
            .findByName("example-community").orElseThrow();
    String id = item.id().orElseThrow();

    // Restrict write access. This still modifies the test router.
    api.snmp().community().setWriteAccess(id, false);

    // Alternative generic update (never supply ".id" as a property):
    // api.snmp().community().setProperties(id,
    //         RouterOsProperties.builder().set("disabled", "yes").build());
}
```

`setProperties` rejects an empty update map and any attempt to replace the
reserved `.id`. It does not itself decide whether changing this community is
authorized. Verify device compatibility and SNMPv3 field availability first.

## 5. IP pools, addresses, Queue Types and PCQ

Read only; these modules currently expose inventory methods, not general
configuration editors.

```java
api.ip().pool().list().forEach(pool -> {
    System.out.println("Pool: " + pool.name());
    System.out.println("Range fragments: " + pool.ranges());
    System.out.println("Next pool: " + pool.nextPool().orElse("(none)"));
});

var ether1Addresses = api.ip().address().list(
        RouterOsProperties.builder()
                .set("interface", "ether1")
                .build());

api.interfaces().list().forEach(iface ->
        System.out.println("Interface: " + iface.name()));
```

`IpPool.ranges()` contains RouterOS range fragments. It is not a
calculated count of available or unallocated IP addresses. Do not estimate
DHCP pool usage from these strings alone.

For Queue Types, fields such as `pcqRate` and `pcqLimit` are intentionally
preserved as strings because RouterOS can return quantities with unit suffixes:

```java
var types = api.queue().type().find(
        RouterOsProperties.builder().set("kind", "pcq").build());

for (var type : types) {
    System.out.println("Type: " + type.name());
    System.out.println("PCQ classifier: "
            + type.pcqClassifier().orElse("(not returned)"));
    System.out.println("PCQ rate (raw): "
            + type.pcqRate().orElse("(not returned)"));
    System.out.println("PCQ burst time: " + type.pcqBurstTime());
    System.out.println("Source mask: " + type.pcqSrcAddressMask());
}
```

Other fields include `pcqTotalLimit`, `pcqBurstRate`,
`pcqBurstThreshold` and the IPv4/IPv6 address masks.
Use `api.queue().type().list()` for the complete queue-type inventory.
An empty optional field is normal for a non-PCQ queue type.

## 6. WiFi and CAPsMAN: retain source provenance

```java
for (var client : api.wifi().registrationTable()) {
    System.out.println("Source: " + client.source());
    System.out.println("Interface: "
            + client.interfaceName().orElse("(not returned)"));
    System.out.println("Signal: " + client.signalDbm());
    System.out.println("Uptime: " + client.uptime());
}

for (var cap : api.wifi().remoteCaps()) {
    System.out.println("CAP source: " + cap.source());
    System.out.println("CAP board: " + cap.boardName().orElse("(not returned)"));
    System.out.println("CAP state: " + cap.state().orElse("(not returned)"));
}
```

`api.wifi().findRemoteCapByBaseMac(...)` compares the returned base MAC
locally, because certain CAPsMAN menus do not reliably implement server
filters. When legacy and modern managers are both relevant, the facade
retains source information rather than deduplicating by MAC or record ID.
An empty result can be correct when the corresponding manager is disabled
or unavailable; consult the [compatibility catalog](routeros-compatibility.md).

For regex matching, use
`api.wifi().registrationTable(ClientSideFilter.regex("interface", "^cap-"))`.
It is **client-side**, not a RouterOS query.
To compare exact server-side word handling consult the
[Raw-Words Cookbook](raw-protocol-words.md#5-capsman-query-diagnostics).

## 7. Router-originated ping and binary file downloads

```java
import io.github.praktimarc.mikrotik.facade.ping.PingRequest;
import java.time.Duration;

var ping = api.ping(new PingRequest(
        "192.0.2.1", 3, Duration.ofMillis(500)));

System.out.println("Sent: " + ping.sent());
System.out.println("Received: " + ping.received());
System.out.println("Loss: " + ping.packetLossPercent() + "%");
System.out.println("Minimum RTT: " + ping.minRtt());
System.out.println("Average RTT: " + ping.avgRtt());
System.out.println("Maximum RTT: " + ping.maxRtt());
System.out.println("Individual replies: " + ping.replies().size());
```

Ping executes **on the router**, not on the Java host. RTT optionals can
be absent if there are no usable replies. The count must be positive;
an explicitly configured interval uses whole positive milliseconds.

```java
import java.nio.file.Path;

var metadata = api.file().findByName("flash/example.bin");
if (metadata.isPresent()) {
    Path localTarget = Path.of("downloads", "example.bin");
    var downloaded = api.file().download(
            "flash/example.bin", localTarget);

    System.out.println("Bytes written: " + downloaded.bytesWritten());
    System.out.println("Local file: " + downloaded.localFile());
}
```

`api.file().list()` and `api.file().find(RouterOsProperties)` cover file
metadata reads. Downloads use the binary-safe low-level path. Choose a
trusted local target outside an application-controlled shared directory;
do not assume remote paths are safe local filenames.

For larger downloads use
`api.async().file().download(remoteFile, localFile)` and retain its
`CompletableFuture` until completion. Cancellation is best effort; it is
not a guarantee that RouterOS terminated every transfer chunk.

## 8. Async, monitoring, cancellation and failure mapping

The `api.async()` tree mirrors the typed read/write modules, including
firewall and SNMP. Futures must be awaited, composed or cancelled **before**
closing the session.

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

var leases = api.async().ip().dhcpServer().lease()
        .findByAddress("192.0.2.10");
var caps = api.async().wifi().remoteCaps();

CompletableFuture.allOf(leases, caps).get(30, TimeUnit.SECONDS);
System.out.println("Lease exists: " + leases.join().isPresent());
System.out.println("CAP count: " + caps.join().size());
```

A local `get(30, ...)` timeout differs from the facade's configured
`commandTimeout`. If local waiting expires, cancel outstanding futures
explicitly and handle partial results. Do not automatically retry mutations:
a lost response does not establish that a write failed on RouterOS.

### Flow monitor with explicit lifecycle

```java
import io.github.praktimarc.mikrotik.facade.interfaces.InterfaceMonitorEntry;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

var seen = new AtomicInteger();
api.interfaces().monitor("ether1").subscribe(
        new Flow.Subscriber<InterfaceMonitorEntry>() {
            private Flow.Subscription subscription;

            @Override public void onSubscribe(Flow.Subscription s) {
                subscription = s;
                s.request(1);
            }
            @Override public void onNext(InterfaceMonitorEntry sample) {
                System.out.println("Receive bit/s: " + sample.rxBitsPerSecond());
                if (seen.incrementAndGet() >= 3) {
                    subscription.cancel();
                } else {
                    subscription.request(1);
                }
            }
            @Override public void onError(Throwable failure) {
                System.err.println(failure.getClass().getSimpleName());
            }
            @Override public void onComplete() {
                System.out.println("Monitoring completed");
            }
        });
```

The stream starts on subscription. Backpressure is real: the facade does
not silently discard data if a subscriber cannot keep up; it can terminate
with `MikrotikBackpressureException`. In production retain a subscription
reference for cancellation on shutdown or UI disposal, and coordinate
monitoring completion before closing the session.

### Checked exceptions and broken-session behavior

```java
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikTimeoutException;

try {
    var result = api.ip().dhcpServer().lease()
            .findByAddress("192.0.2.10");
    System.out.println(result.isPresent());
} catch (MikrotikTimeoutException timeout) {
    System.err.println("Command timed out");
} catch (MikrotikConnectionException broken) {
    System.err.println("RouterOS session lost; create a new session");
} catch (MikrotikDataException data) {
    System.err.println("Data missing, malformed or ambiguous");
} catch (MikrotikCommandException command) {
    System.err.println("RouterOS rejected the operation");
} catch (MikrotikFacadeException other) {
    System.err.println("Facade operation failed");
}
```

Unexpected connection loss marks the session `BROKEN`; the facade does
not reconnect or replay the last command. Application-level retries should
rebuild a new authenticated session and distinguish idempotent reads from
writes that may already have been applied. Never log raw values or
credentials as part of diagnostics.

## 9. Choosing APIs and documentation boundaries

| Goal | Recommended API |
| --- | --- |
| Routine typed RouterOS reads | `ip()`, `wifi()`, `interfaces()`, `queue()`, `snmp()`, `file()` |
| Authorized configuration changes | Typed `add`, `set...` and `remove` only where explicitly exposed |
| RouterOS filter investigation | [Raw-Words Cookbook](raw-protocol-words.md) |
| Complex server-side query expression | [RouterOsQuery](query-filtering.md) |
| Actual Java regex | `ClientSideFilter.regex`, applied after fetching records |
| Advanced session/Flow/error behavior | [Getting started](getting-started.md) |
| RouterOS 6/7 and wireless source choice | [Compatibility catalog](routeros-compatibility.md) |

This cookbook describes the implemented API, not every possible RouterOS
command. It does not claim production coverage of all firmware, hardware
or CAPsMAN combinations. For new facade methods, update the
[API coverage inventory](api-coverage.md), add an application-facing
example if needed, and run the normal tests before merging.
