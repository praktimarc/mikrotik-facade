# Practical Java examples

A small cookbook for the public Java 17 `mikrotik-facade` API. All names, IP addresses,
MAC addresses, and file paths below are **synthetic examples**. Replace them with values
from your own test environment. The documentation examples use the **classic RouterOS
binary API**, not REST.

Most snippets are read-only. Operations that can change a router are separated and
explicitly marked.

## 1. Build and add the dependency

The facade is currently **`0.1.0-SNAPSHOT`**, built from this repository; it is **not
published to Maven Central**. The lower-level `io.github.praktimarc:mikrotik` dependency
is available from Maven Central.

In a local checkout of this repository:

~~~bash
mvn -B -U clean install
~~~

In a separate Maven application's `pom.xml`, after the local install:

~~~xml
<dependency>
    <groupId>io.github.praktimarc</groupId>
    <artifactId>mikrotik-facade</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
~~~

Use JDK 17 or newer. For a different machine or CI runner, build/install the facade
there or publish an approved release to a Maven repository first.

## 2. Connect securely (complete `main` method)

Supply `ROUTEROS_HOST`, `ROUTEROS_USERNAME` and `ROUTEROS_PASSWORD` as environment
variables. Do not commit passwords or real router inventories. TLS verification
requires a valid certificate and a trusted issuing CA; see
[getting started](getting-started.md#select-a-transport).

~~~java
import io.github.praktimarc.mikrotik.facade.MikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.transport.ApiTransport;
import java.time.Duration;

public final class ExampleApp {
    public static void main(String[] args) throws Exception {
        try (MikrotikRtrApi api = MikrotikRtrApi.builder()
                .host(System.getenv("ROUTEROS_HOST"))
                .credentials(
                        System.getenv("ROUTEROS_USERNAME"),
                        System.getenv("ROUTEROS_PASSWORD"))
                .transport(ApiTransport.tlsVerified())
                .connectTimeout(Duration.ofSeconds(10))
                .commandTimeout(Duration.ofSeconds(30))
                .connect()) {

            System.out.println(
                    "RouterOS " + api.environment().systemInfo().version());
            api.ip().dhcpServer().lease().findByAddress("192.0.2.10")
                    .ifPresent(lease -> System.out.println(
                            "Example lease: " + lease.address().orElse("(missing)")));
        }
    }
}
~~~

The rest of this page assumes `api` is already connected and is used inside the `try` block. Paste individual snippets there and let your IDE add
imports for the referenced API classes. The available entry points are `api.ip()` (IPv4 addresses, pools, DHCP and firewall),
`api.interfaces()`, `api.wifi()`, `api.queue()`, `api.snmp()`,
`api.file()`, `api.ping(request)` and `api.raw()`.
Asynchronous calls use the same hierarchy under `api.async()`.

## 3. Find and count DHCP leases (read-only)

~~~java
// Find by exact configured lease address or MAC.
var byAddress = api.ip().dhcpServer().lease().findByAddress("192.0.2.10");
var byMac = api.ip().dhcpServer().lease().findByMac("02:00:00:00:00:01");

byAddress.ifPresent(lease -> {
    System.out.println("ID: " + lease.id().orElse("(missing)"));
    System.out.println("IP: " + lease.address().orElse("(missing)"));
    System.out.println("MAC: " + lease.macAddress().orElse("(missing)"));
    System.out.println("State: " + lease.status().orElse("(unknown)"));
});

// Optional.empty() means no matching lease; duplicate matches are a data error.
if (byMac.isEmpty()) {
    System.out.println("No lease with the example MAC");
}

// Count all leases efficiently using RouterOS count-only.
var noFilters = RouterOsProperties.builder().build();
long total = api.ip().dhcpServer().lease().count(noFilters);

// With a filter, the facade validates real returned properties before counting.
var serverFilter = RouterOsProperties.builder()
        .set("server", "example-dhcp")
        .build();
long onServer = api.ip().dhcpServer().lease().count(serverFilter);

System.out.println("Total: " + total + ", example server: " + onServer);
~~~

Import `io.github.praktimarc.mikrotik.facade.RouterOsProperties`. Filtered lease
counts transfer matching records instead of trusting a remote `count-only` response;
this can cost more bandwidth on large tables.

## 4. Inspect WiFi registrations and CAPsMAN (read-only)

~~~java
// The facade chooses legacy/new WiFi sources based on manager and version data.
var registrations = api.wifi().registrationTable();
for (var client : registrations) {
    System.out.println(client.interfaceName().orElse("(unknown)")
            + " / " + client.macAddress().orElse("(unknown)")
            + " / " + client.signalDbm());
}

// Java regex filtering is performed client-side.
var capClients = api.wifi().registrationTable(
        ClientSideFilter.regex("interface", "^cap-[0-9]+-"));
System.out.println("Matching clients: " + capClients.size());

// This lookup reads remote CAPs and checks their real base-mac property locally.
api.wifi().findRemoteCapByBaseMac("02:00:00:00:00:01")
        .ifPresent(cap -> System.out.println(
                cap.identity().orElse("(unnamed)") + " from " + cap.source()));
~~~

Import `io.github.praktimarc.mikrotik.facade.ClientSideFilter`. A disabled or
unavailable CAPsMAN manager can yield an empty list; an empty list is not
automatically a transport error. No RouterOS source records are silently
deduplicated.

## 5. Find firewall Filter, Mangle and Address List entries (read-only)

~~~java
// All supplied equality properties are checked again against each returned row.
var criteria = RouterOsProperties.builder()
        .set("chain", "forward")
        .set("comment", "example-rule")
        .build();

var filterRules = api.ip().firewall().filter().find(criteria);
for (var rule : filterRules) {
    System.out.println("Filter ID: " + rule.id().orElse("(missing)"));
}

var mangleRules =
        api.ip().firewall().mangle().findBySourceAddress("192.0.2.0/24");
System.out.println("Matching Mangle rules: " + mangleRules.size());

var addressListEntries =
        api.ip().firewall().addressList().findByListAndAddress(
                "example-list", "192.0.2.10");
System.out.println("Matching address-list entries: " + addressListEntries.size());
~~~

Import `RouterOsProperties` as shown above. The equality checks compare actual
returned property strings case-insensitively; an absent property never matches.
A lookup result is **not** automatically an authorization to modify or delete the
returned router configuration.

## 6. List interfaces, IP addresses and queue types (read-only)

~~~java
api.interfaces().list().forEach(iface ->
        System.out.println("Interface: " + iface.name()));

var addresses = api.ip().address().list(
        RouterOsProperties.builder()
                .set("interface", "ether1")
                .build());
addresses.forEach(address ->
        System.out.println("Address: " + address.address()));

// RouterOS boolean representations such as no and false are equivalent
// for client-side verification of typed equality-query results.
var nonDefaultTypes = api.queue().type().find(
        RouterOsProperties.builder().set("default", "no").build());
nonDefaultTypes.forEach(type ->
        System.out.println("Queue type: " + type.name()
                + ", kind: " + type.kind().orElse("(unknown)")));
~~~

## 7. Read SNMP communities without exposing secrets

~~~java
api.snmp().community().findByName("example-community")
        .ifPresent(community ->
                System.out.println("Found community ID: "
                        + community.id().orElse("(missing)")));
~~~

Community names and sensitive values can appear in the raw RouterOS records. Do
not print `raw()`, passwords, communities, or full configuration data to shared logs.

## 8. List and download a RouterOS file

~~~java
api.file().findByName("flash/example.txt")
        .ifPresent(file -> System.out.println(
                file.name() + " size=" + file.sizeBytes()));

// A download writes a file on the local computer: pick an intentional path.
var download = api.async().file().download(
        "flash/example.txt",
        java.nio.file.Path.of("example.txt"));
var downloaded = download.get(30, java.util.concurrent.TimeUnit.SECONDS);
System.out.println("Download complete: " + downloaded);
~~~

Downloads run asynchronously; complete or cancel pending operations before
closing the session. Remote cancellation of an in-progress binary transfer is
best-effort, not guaranteed.

## 9. Ping from the router (read-only)

~~~java
var result = api.ping(new PingRequest("192.0.2.1", 3));
System.out.println("Sent: " + result.sent());
System.out.println("Received: " + result.received());
System.out.println("Loss: " + result.packetLossPercent() + "%");
~~~

Import `io.github.praktimarc.mikrotik.facade.ping.PingRequest`. `192.0.2.1`
is a documentation-only IP address; use an appropriate test target to get
meaningful ping responses.

## 10. Raw commands and explicitly client-side regex (read-only)

~~~java
var raw = api.raw()
        .command("/ip/address/print")
        .query("interface", "ether1")
        .property("address")
        .property("interface")
        .execute();

raw.records().forEach(record ->
        System.out.println(record.find("address").orElse("(missing)") + " / "
                + record.find("interface").orElse("(missing)") ));

// For raw results, apply a separate explicit local filter when needed.
var matching = raw.records(
        ClientSideFilter.regex("address", "^192\\.0\\.2\\."));
System.out.println("Matching example prefix: " + matching.size());
~~~

Unlike typed equality APIs, the raw API intentionally preserves RouterOS query
behavior and does **not** automatically verify server-side filters. Complex
server-side expressions are supported through `RouterOsQuery`; see
[query filtering](query-filtering.md). Raw commands may also mutate router
configuration, so use only trusted commands and inputs.

## 11. Asynchronous read (read-only)

~~~java
var future = api.async().ip().dhcpServer().lease()
        .findByAddress("192.0.2.10");

var lease = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
System.out.println(lease.isPresent() ? "Found" : "Not found");
~~~

`api.async()` mirrors the typed facade APIs. Manage futures before closing the
session. Cancelling a Future does not imply that an already-issued remote
mutation was rolled back.

## 12. Remove a DHCP lease by ID (destructive; explicitly opt-in)

This is an illustration of the ID-based mutation API, **not** a command to run
against production. In the example, `approved` is deliberately `false`. Change
it only in your own application after explicit operator authorization.

~~~java
String targetIp = "192.0.2.10";
String targetMac = "02:00:00:00:00:01";
boolean approved = false; // Replace with an explicit authorization decision.

var candidate = api.ip().dhcpServer().lease().findByAddress(targetIp);
if (approved && candidate.isPresent()) {
    var lease = candidate.orElseThrow();
    boolean identityMatches =
            lease.address().map(targetIp::equals).orElse(false)
            && lease.macAddress()
                    .map(targetMac::equalsIgnoreCase).orElse(false);
    if (identityMatches) {
        String routerOsId = lease.id().orElseThrow();
        api.ip().dhcpServer().lease().remove(routerOsId);
    }
}
~~~

The facade uses `/ip/dhcp-server/lease/remove` with the RouterOS `.id`
argument; it does **not** delete by IP address. The lookup checks the returned
properties locally and rejects multiple matching leases. **Read and delete are
still two separate commands**, so they are not atomic: the router state can
change between lookup and mutation. Apply additional business rules such as
DHCP-server ownership where required.

## 13. Troubleshooting and safe defaults

- **Connection fails:** check API service/port, the router's firewall,
  credentials, TLS certificates and the JVM trust store.
- **`Optional.empty()`:** no matching record was found; a disabled manager
  or unavailable feature can have a different cause. Consult the
  [compatibility catalog](routeros-compatibility.md).
- **`MikrotikDataException`:** a typed result was malformed or a lookup that
  expects at most one matching row found duplicates. Do not convert such
  failures to a successful single-row result.
- **Query behavior:** typed equality operations verify values actually
  returned by RouterOS. This prevents unrelated matches, but cannot recover
  results that a RouterOS server-side filter wrongly omits.
- **No secrets in source or logs:** use environment variables or your
  application's secret management; avoid printing complete raw records.
- **Testing without a device:** run `mvn clean verify` in the repository.
  The optional `router-it` profile accesses explicitly configured test
  routers and is not part of normal CI.

More details: [getting started](getting-started.md),
[query/filter semantics](query-filtering.md),
[RouterOS compatibility](routeros-compatibility.md), and
[release readiness](release-readiness.md).


## Exact RouterOS API words (diagnostic)

When testing RouterOS query handling, bypass the pseudo-CLI string parser:

~~~java
var query = api.raw().words(
        "/interface/wifi/capsman/remote-cap/print",
        "?base-mac=D0:EA:11:8A:2F:9B");
System.out.println(query.records().size());
~~~

See [raw protocol words](raw-protocol-words.md). The word-level API requires
an unpublished development dependency until the next low-level release.
