# mikrotik-facade

High-level Java facade for the classic MikroTik RouterOS API.

## Status

The v1 feature set is implemented and has passed the Java 17 CI suite; the facade is still an unreleased `0.1.0-SNAPSHOT`. Release readiness and real-router integration remain separate gates. See [Getting started](docs/getting-started.md), [architecture](docs/architecture.md), and [release readiness](docs/release-readiness.md).

## Baseline

- Java 17
- Maven
- Low-level client: `io.github.praktimarc:mikrotik:3.0.8-praktimarc.5`
- SLF4J API for logging; the library does not force a backend
- JUnit 5 for tests

## Scope

The facade provides a typed, CLI-oriented API with raw access, shared synchronous/asynchronous execution, Flow-based streaming, RouterOS capability/source/schema compatibility handling, with no dependencies on an application's business models.

RouterOS REST is not part of v1.

Diagnostics are session/operation-correlated and intentionally structural: command values, credentials, RouterOS tags and configured host values are not emitted by the facade logging layer.

## Dependency note

The facade pins `io.github.praktimarc:mikrotik:3.0.8-praktimarc.5`, the signed low-level release with exact RouterOS protocol-word support. Maven resolves it from the standard Maven Central repository, without a custom repository, publishing credentials, `systemPath`, checked-in JAR or local snapshot installation. The facade itself remains an unreleased `0.1.0-SNAPSHOT`.


## Build outputs and Eclipse/Ant distribution

Every normal `mvn -U clean package`, `verify` or `install` creates
three facade JARs in `target/` (binary, `-sources` and `-javadoc`) and
a ready-to-unpack `target/mikrotik-facade-0.1.0-SNAPSHOT-dist.zip`.
The ZIP contains `lib/` with the facade and its runtime dependencies, and
`sources/` and `javadoc/` with both facade and low-level attachments.

For non-Maven Eclipse/Ant projects, replace old runtime JARs with the files
from `lib/`. Set the two IDE attachments once using the archive's
`sources/` and `javadoc/` folders; see
[distribution and Eclipse setup](docs/distribution.md).
Source/Javadoc JARs are **not** required in the runtime classpath or WAR.

## Quick start

This is a Java 17 library for the **classic RouterOS binary API**, not REST.
The facade is currently a locally built `0.1.0-SNAPSHOT` and is **not yet
published to Maven Central**. Clone this repository and run:

~~~bash
mvn -B -U clean install
~~~

Then add it to the consumer application's `pom.xml`:

~~~xml
<dependency>
    <groupId>io.github.praktimarc</groupId>
    <artifactId>mikrotik-facade</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
~~~

The following Java example connects using verified TLS and credentials from
environment variables. `192.0.2.10` is a **documentation-only address**; substitute
values from your own test environment.

~~~java
import io.github.praktimarc.mikrotik.facade.MikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.transport.ApiTransport;

public class Example {
    public static void main(String[] args) throws Exception {
        try (MikrotikRtrApi api = MikrotikRtrApi.builder()
                .host(System.getenv("ROUTEROS_HOST"))
                .credentials(
                        System.getenv("ROUTEROS_USERNAME"),
                        System.getenv("ROUTEROS_PASSWORD"))
                .transport(ApiTransport.tlsVerified())
                .connect()) {

            System.out.println("RouterOS " + api.environment().systemInfo().version());
            api.ip().dhcpServer().lease().findByAddress("192.0.2.10")
                    .ifPresent(lease -> System.out.println(
                            "Lease ID: " + lease.id().orElse("(missing)")));
        }
    }
}
~~~

**Next:** [Practical examples / Java cookbook](docs/examples.md) covers DHCP, firewall
Filter/Mangle/Address Lists, WiFi/CAPsMAN, interfaces, queues, SNMP, files, ping,
raw queries, async calls and guarded ID-based deletion. No live credentials or
production-specific values are included.

## Documentation

- [Raw RouterOS API words — cookbook (queries, CAPsMAN, async and errors)](docs/raw-protocol-words.md)
- [Raw RouterOS continuous streaming (`follow`, `listen`, cancellation)](docs/raw-streaming.md)
- [Typed RouterOS log streaming (`api.log().follow()` / `followOnly()`)](docs/log-streaming.md)

- [Getting started, examples and failure semantics](docs/getting-started.md)
- [Practical Java examples and common workflows](docs/examples.md)
- [Advanced Java cookbook: all typed modules and guarded writes](docs/advanced-cookbook.md)
- [Public API documentation coverage and CI gate](docs/api-coverage.md)
- [Release-readiness and remaining gates](docs/release-readiness.md)
- [RouterOS compatibility catalog](docs/routeros-compatibility.md)
- [Architecture and execution model](docs/architecture.md)
- [Snapshot API migration: RouterOS menu hierarchy](docs/api-migration-menu-hierarchy.md)
- [Low-level API contract](docs/low-level-contract.md)

## Verification

The low-level consumer contract is pinned to `mikrotik 3.0.8-praktimarc.5`
(release commit `14c61d155faf224d1c0827a66aec4d16c44fca72`).
The contract tests use only the public low-level API and an independent loopback RouterOS
wire peer.

Normal verification does not require RouterOS credentials:

```bash
mvn clean verify
```

GitHub Actions runs Maven verification **and Javadoc generation** on every push to `main`, on pull requests targeting `main`, and when manually dispatched. The job uses Temurin Java 17 on Ubuntu, fetches dependencies anonymously from Maven Central, and does not enable the real-router test profile. The [documentation build and security checks](https://github.com/praktimarc/mikrotik-facade/actions/runs/38377906737) passed on Java 17. The release-readiness checklist records the outstanding publication and real-router verification gates.

Read-only real-router integration tests are opt-in:

```bash
mvn clean verify -Prouter-it
```

The `router-it` profile requires `MIKROTIK_IT_HOST`, `MIKROTIK_IT_USERNAME`,
`MIKROTIK_IT_PASSWORD` and `MIKROTIK_IT_PROFILE`. See
`docs/routeros-test-profiles.md` for transport options and the seven documented target
profiles, and `docs/low-level-contract.md` for the exact low-level guarantees.


## Queries and regex filtering

Advanced server-side query composition is available through `RouterOsQuery`:

```java
RouterOsQuery query =
        RouterOsQuery.eq("interface", "cap-a")
                .or(RouterOsQuery.eq("interface", "cap-b"));

RawCommandResult result =
        mtApi.raw()
             .command("/interface/print")
             .query(query)
             .execute();
```

For real regular expressions use the explicitly client-side API:

```java
List<WifiRegistration> registrations =
        mtApi.wifi().registrationTable(
                ClientSideFilter.regex("interface", "^cap-[0-9]+-"));
```

Do not emulate regex/prefix matching with RouterOS `>` / `<` queries. Presence
queries (`exists/notExists`) remain unavailable through the structured v1 parser,
but `raw().words(...)` can transmit RouterOS `?name` / `?-name` query words verbatim.
See `docs/query-filtering.md`.
