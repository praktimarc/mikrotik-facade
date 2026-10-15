# Low-level client contract

## Baseline

The v1 facade is built against exactly:

```text
io.github.praktimarc:mikrotik:3.0.8-praktimarc.5-SNAPSHOT
development commit: b16901723e7609da7dd8d1a8623da5e2169001f6
low-level Java baseline: 11
facade Java baseline: 17
```

The contract suite exists because the facade relies on behavioral guarantees that are
stronger than merely compiling against the public classes.

The previous low-level version, 3.0.8-praktimarc.4, was verified from Maven Central on 2026-10-08. The current exact-words development dependency is a newer **unpublished snapshot**; build/install it locally from the pinned mikrotik-java commit before building this facade. CI installs it from the source commit before verification.

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
| `downloadFile` is byte-exact, chunked, preserves the remote filename/query, stages local output safely and coexists with tagged text listeners. | Binary downloads delegate to the low-level implementation instead of reimplementing `/file/read`. |
| Public string parsing translates supported boolean/comparison syntax to the expected RouterOS query stack, including repeated property names. | `RouterOsQuery` serializes through the public low-level parser rather than internal query APIs. |
| Text properties larger than 60 kB survive the public API byte-for-byte as complete strings. | Client-side regex operates on full `RouterOsRecord` values and must not inherit historical word-truncation behavior. |

The binary contract fixture deliberately contains NUL bytes, non-UTF-8-looking byte values
and RouterOS-protocol-looking byte sequences. Payload data is therefore tested as bytes,
not text.

The low-level API now exposes `executeWords(List<String>, ResultListener)` to
send exact UTF-8 API words, preserving ordering and bypassing the parser.
The existing string-parser and binary-file contracts remain in use.

The contract suite also pins the supported public string-parser query grammar. Presence query words
(`?name` / `?-name`) remain outside the **structured parser API**; they can now be sent using `raw().words(...)` without any internal `impl.*` workaround.

## Upgrade rule

Changing the `mikrotik` dependency version is not a mechanical dependency bump.

Before accepting a later low-level release:

1. run the normal facade test suite including `MikrotikJavaContractTest`;
2. inspect any changed public low-level contract;
3. update this document only when the facade intentionally accepts the new semantics;
4. never compensate for a broken low-level contract by adding hidden `impl.*` coupling in the facade.

## Verification environment

On 2026-10-08, the published low-level POM and JAR were retrieved anonymously from Maven Central into a new, empty local Maven repository in an external consumer build. After fixing facade test compilation and a callback-thread test race, an external Java 17 Windows run of `mvn -B -U clean verify` at facade commit `3e1534e9d71cdf821f7f5614780d49a75fe0a130` passed **244 tests, 0 failures, 0 errors, 0 skipped**, built the facade JAR, and reported `BUILD SUCCESS`.

The standard test suite includes the low-level contract tests without RouterOS credentials. Real-router read-only integration tests remain opt-in through the `router-it` Maven profile. GitHub Actions CI uses Java 17 and runs the standard `mvn -B -U clean verify --file pom.xml` on pushes and pull requests to `main`; CI success must be confirmed separately from the recorded local test run.
