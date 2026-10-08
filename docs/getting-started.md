# Getting started with mikrotik-facade

`mikrotik-facade` is a Java 17 library for the classic MikroTik RouterOS binary API. It is a higher-level consumer of `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4`, not an HTTP/REST client.

## Requirements

- JDK 17 or newer and Maven.
- An accessible RouterOS API service (TCP 8729 for API TLS, or TCP 8728 for plain API).
- A RouterOS account authorized to run the commands used by your application.
- TLS certificates trusted by your JVM, or a caller-configured `SSLContext` for an internal CA.

The low-level client is **published to Maven Central**. A clean-cache Maven consumer build downloaded its POM and JAR anonymously on 2026-10-08; no custom repository, locally installed JAR or publishing token is needed.

The facade is currently a `0.1.0-SNAPSHOT` project in this source repository. The following local build command produces its JAR; **do not assume the facade itself is available on Maven Central**:

```bash
mvn -B -U clean verify
```

To consume a local checkout from another Maven project, first use `mvn install` in the facade checkout or publish an approved future facade release to a repository. Never use `systemPath` or commit a dependency JAR to the consumer repository.

## Connect and read synchronously

```java
import io.github.praktimarc.mikrotik.facade.MikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.dhcp.DhcpLease;
import io.github.praktimarc.mikrotik.facade.transport.ApiTransport;
import java.time.Duration;
import java.util.Optional;

public class RouterExample {
    public static void main(String[] args) throws Exception {
        String host = System.getenv("ROUTEROS_HOST");
        String user = System.getenv("ROUTEROS_USERNAME");
        String password = System.getenv("ROUTEROS_PASSWORD");

        try (MikrotikRtrApi api = MikrotikRtrApi.builder()
                .host(host)
                .credentials(user, password)
                .transport(ApiTransport.tlsVerified())
                .connectTimeout(Duration.ofSeconds(10))
                .commandTimeout(Duration.ofSeconds(30))
                .connect()) {

            System.out.println(api.environment().systemInfo());
            Optional<DhcpLease> lease =
                    api.dhcpServer().findLeaseByMac("AA:BB:CC:DD:EE:FF");
            lease.ifPresent(value ->
                    System.out.println(value.address().orElse("<no address>")));
        }
    }
}
```

Replace the sample MAC address with a real one. The facade performs connection, authentication and environment discovery before `connect()` returns. One `MikrotikRtrApi` instance owns one authenticated session; use try-with-resources to close it. All input credentials come from the environment, not committed source.

### Select a transport

| Mode | Usage | Default port |
| --- | --- | --- |
| `ApiTransport.tlsVerified()` | Recommended; JVM trust store and hostname verification. | 8729 |
| `ApiTransport.tlsVerified(SSLContext)` | Certificate and hostname verification with custom trusted CA/context. | 8729 |
| `ApiTransport.plain()` | Unencrypted binary API, only for a protected environment where plaintext is accepted. | 8728 |
| `ApiTransport.tlsUnverified()` | **Unsafe**: accepts all server certificates without hostname validation; development/troubleshooting only. | 8729 |
| `ApiTransport.custom(SocketFactory, int)` | Advanced custom socket factory; caller is responsible for its security. | Caller-supplied |

`.port(int)` overrides the transport's default port. Connection and command timeouts are separate, positive whole-millisecond `Duration` values representable as an `int` number of milliseconds. `.bootstrapRetries(int)` applies to eligible *bootstrap* failures, not ordinary commands or rejected authentication. No transparent reconnect or replay occurs after session failure. A supplied `callbackExecutor(Executor)` stays owned by the caller.

## Asynchronous operation

The `async()` tree mirrors the typed API and uses `CompletableFuture`:

```java
import io.github.praktimarc.mikrotik.facade.MikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.dhcp.DhcpLease;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

CompletableFuture<Optional<DhcpLease>> request =
        api.async().dhcpServer().findLeaseByMac("AA:BB:CC:DD:EE:FF");
request.thenAccept(lease ->
        lease.ifPresent(value -> System.out.println(value.address())));
```

The `api` variable is an already-connected `MikrotikRtrApi` as constructed above. Wait for or otherwise manage the Future **before** closing the session. Async callbacks are dispatched through the session's callback executor; a caller-supplied executor is never shut down by the facade. `cancel(...)` and finite-command timeouts produce a single local terminal result with best-effort cancellation of the associated RouterOS command; cancellation is not a guarantee that RouterOS undoes work already performed. As with other `CompletableFuture` uses, attaching a continuation after a future has completed does not guarantee that continuation's calling thread.

## Raw commands and terminal metadata

The synchronous Raw API retains all unknown RouterOS fields and exposes terminal `!done` metadata independently of ordinary `!re` records:

```java
import io.github.praktimarc.mikrotik.facade.raw.RawCommandResult;

RawCommandResult result = api.raw()
        .command("/ip/address/print")
        .query("interface", "ether1")
        .property(".id")
        .property("address")
        .execute();

result.records().forEach(row -> System.out.println(row.asMap()));
System.out.println(result.completion().asMap());
```

Use `api.raw().execute("/system/resource/print")` when only regular records are needed. Query builders support exact equality and the explicit `RouterOsQuery` expression tree. `ClientSideFilter.regex(...)` is a clearly named, post-fetch filter, **not** a RouterOS server-side regex. Supported operators, grammar limits and examples are in [query filtering](query-filtering.md).

Raw commands can mutate configuration: treat inputs as trusted, apply router-side permissions, and do not log command arguments or credentials. The facade's diagnostics deliberately redact sensitive values.

## Continuous interface monitoring with Flow

`interfaces().monitor("ether1")` is a cold `Flow.Publisher`: monitoring begins on subscription, not construction. Subscribers control demand and must cancel when they no longer need samples:

```java
import io.github.praktimarc.mikrotik.facade.interfaces.InterfaceMonitorEntry;
import java.util.concurrent.Flow;

Flow.Publisher<InterfaceMonitorEntry> publisher =
        api.interfaces().monitor("ether1");

publisher.subscribe(new Flow.Subscriber<>() {
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription s) {
        subscription = s;
        subscription.request(1);
    }

    @Override
    public void onNext(InterfaceMonitorEntry item) {
        System.out.println(item.rxBitsPerSecond());
        subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }

    @Override
    public void onComplete() {
        System.out.println("Monitoring finished");
    }
});
```

Keep a reference to the subscription when monitoring must be stopped explicitly, then call `subscription.cancel()`. Samples are bounded and delivered serially on the callback executor. If the subscriber cannot keep up, overflow is signalled with `MikrotikBackpressureException` (no silent dropping). Closing the session cancels active monitoring subscriptions.

## Files and cancellation

`api.files().findByName(name)` checks an exact RouterOS filename. Binary downloads use the async API `api.async().files().download(remoteFile, localPath)`, which first checks for the file and then delegates to the pinned low-level binary-safe implementation:

```java
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import io.github.praktimarc.mikrotik.facade.files.FileDownloadResult;

CompletableFuture<FileDownloadResult> download =
        api.async().files().download(
                "flash/example.bin",
                Path.of("example.bin"));
```

The low-level transfer is blocking but runs on a dedicated bounded worker pool, not the callback executor. Cancelling the public Future is **local best-effort**: the low-level chunk tags are not public, so this is not guaranteed RouterOS remote cancellation. Closing the session terminates active transfers. See the [low-level contract](low-level-contract.md).

## Failure and session lifecycle

All checked facade failures derive from `MikrotikFacadeException`, including:

- `MikrotikAuthenticationException`: rejected authentication during bootstrap.
- `MikrotikConnectionException`: lost/broken session or failed controlled close.
- `MikrotikCommandException`: RouterOS command failure; available structured command details are sanitized.
- `MikrotikTimeoutException`: finite command timeout, a `MikrotikCommandException`.
- `MikrotikBackpressureException`: bounded stream overflow, a `MikrotikCommandException`.
- `MikrotikDataException`: missing/malformed/inconsistent RouterOS data.
- `MikrotikFileException`: file transfer or local filesystem failure.
- `MikrotikUnsupportedFeatureException`: a definitively unsupported RouterOS operation.

A normal `close()` moves the session toward `CLOSED` and prevents new operations. Unexpected fatal connection loss instead marks it `BROKEN` and terminates active operations; there is no automatic session recovery. Create a fresh facade session after a broken connection. Successful empty results must not be confused with unsupported paths.

See the [RouterOS compatibility catalog](routeros-compatibility.md), [seven real-device test profiles](routeros-test-profiles.md), [functional parity inventory](functional-parity.md), and [release readiness](release-readiness.md). Real-router tests are **opt-in** via `mvn clean verify -Prouter-it` and require documented environment configuration. The normal 244-test CI suite runs without RouterOS credentials.
