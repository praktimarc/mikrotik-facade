# Low-level client contract

## Baseline

The v1 facade is built against exactly:

```text
io.github.praktimarc:mikrotik:3.0.8-praktimarc.4
release commit: c170858efaac04fc78771903ef4c2bdbb6d35325
low-level Java baseline: 11
facade Java baseline: 17
```

The contract suite exists because the facade relies on behavioral guarantees that are
stronger than merely compiling against the public classes.

The pinned low-level POM and JAR are publicly resolvable from Maven Central. This was verified on 2026-10-08 by an external Maven build using a new, empty local repository. The dependency does not require GitHub Packages credentials or a locally installed JAR.

## Contract covered by the facade

`MikrotikJavaContractTest` uses the public `ApiConnection`, `ResultListener`,
`ConnectionListener` and public exception types against an independent loopback
RouterOS wire peer. It does not import or reflect into `me.legrange.mikrotik.impl.*`.

The suite pins these guarantees:

| Contract | Facade dependency |
| --- | --- |
| Multiple listener commands may be active simultaneously and replies are routed by independent tags. | Shared sync/async `CommandEngine` and Flow operations do not add a facade-level send lock. |
| `ResultListener.completed(Map)` receives generic `!done` metadata without `.tag`; the map is immutable. | `CommandResult.completion()`, DHCP `count-only`, ping summaries and future terminal metadata. |
| Tagged `!trap` is terminal only for that command and is surfaced as `ApiCommandException`; ordinary command failure does not break the connection. | Typed exception mapping and capability/source probing. |
| Unexpected fatal loss of an established connection notifies `ConnectionListener`. | Session transitions permanently to `BROKEN`. |
| Intentional `ApiConnection.close()` is idempotent and does not emit connection-loss notification. | Controlled facade `OPEN/CLOSING/CLOSED` lifecycle. |
| Fatal connection loss terminates active commands with `ApiConnectionException` and later submissions fail. | No transparent reconnect/replay and no hanging active facade futures. |
| `downloadFile` is byte-exact, chunked, preserves the remote filename/query, stages local output safely and coexists with tagged text listeners. | Task-16 binary download facade delegates to the low-level implementation instead of reimplementing `/file/read`. |
| Public string parsing translates supported boolean/comparison syntax to the expected RouterOS query stack, including repeated property names. | Task 19a `RouterOsQuery` serializes through the public low-level parser rather than internal query APIs. |
| Text properties larger than 60 kB survive the public API byte-for-byte as complete strings. | Client-side regex operates on full `RouterOsRecord` values and must not inherit historical word-truncation behavior. |

The binary contract fixture deliberately contains NUL bytes, non-UTF-8-looking byte values
and RouterOS-protocol-looking byte sequences. Payload data is therefore tested as bytes,
not text.

Task 19a also pins the supported public string-parser query grammar. Presence query words
(`?name` / `?-name`) remain outside the facade because this low-level release has no
safe public parser syntax for them; no internal `impl.*` workaround is permitted.

## Upgrade rule

Changing the `mikrotik` dependency version is not a mechanical dependency bump.

Before accepting a later low-level release:

1. run the normal facade test suite including `MikrotikJavaContractTest`;
2. inspect any changed public low-level contract;
3. update this document only when the facade intentionally accepts the new semantics;
4. never compensate for a broken low-level contract by adding hidden `impl.*` coupling in the facade.

## Verification environment

The current ChatGPT execution environment has Java/Javac but no Maven installation.
The Task-19 tests are therefore committed as executable Maven/JUnit tests, but
`mvn clean verify` is not claimed as executed here.
