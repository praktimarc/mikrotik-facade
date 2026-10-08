# mikrotik-facade

High-level Java facade for the classic MikroTik RouterOS API.

## Status

The project is in initial implementation. The architecture and implementation plan are tracked under `docs/superpowers/`.

## Baseline

- Java 17
- Maven
- Low-level client: `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4`
- SLF4J API for logging; the library does not force a backend
- JUnit 5 for tests

## Scope

The facade provides a typed, CLI-oriented API with raw access, shared synchronous/asynchronous execution, Flow-based streaming, RouterOS capability/source/schema compatibility handling, and no dependency on ISPSup/GWT DTOs.

RouterOS REST is not part of v1.

Diagnostics are session/operation-correlated and intentionally structural: command values, credentials, RouterOS tags and configured host values are not emitted by the facade logging layer.

## Dependency note

The low-level baseline `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4` is published to Maven Central. Its POM and JAR were successfully downloaded from the default Central repository during an external clean-cache build on 2026-10-08. No custom Maven repository, credentials, `systemPath`, or checked-in dependency JAR is required. A subsequent `mvn -B -U clean verify` on Windows with Java 17 passed all 244 tests and produced `mikrotik-facade-0.1.0-SNAPSHOT.jar`.


## Verification

The low-level consumer contract is pinned to `mikrotik 3.0.8-praktimarc.4`
(release commit `c170858efaac04fc78771903ef4c2bdbb6d35325`).
The contract tests use only the public low-level API and an independent loopback RouterOS
wire peer.

Normal verification does not require RouterOS credentials:

```bash
mvn clean verify
```

GitHub Actions runs the same Maven verification on every push to `main`, on pull requests targeting `main`, and when manually dispatched. The job uses Temurin Java 17 on Ubuntu, fetches dependencies anonymously from Maven Central, and does not enable the real-router test profile.

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
queries (`exists/notExists`) are also deliberately absent in v1 because the pinned
low-level public parser cannot safely emit RouterOS `?name` / `?-name` query words.
See `docs/query-filtering.md`.
