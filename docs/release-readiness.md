# v1 release-readiness checklist

This document distinguishes **verified build/distribution evidence** from **remaining release decisions**. No `mikrotik-facade` tag, GitHub Release, or Maven Central deployment is implied by this checklist.

## Verified on 2026-10-08

| Gate | Evidence / scope |
| --- | --- |
| Public low-level dependency | `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4` POM and JAR downloaded anonymously from Maven Central into a fresh local Maven repository. |
| Facade Java 17 build | At facade commit `3e1534e9d71cdf821f7f5614780d49a75fe0a130`, `mvn -B -U clean verify` on Windows built the facade JAR and ran **244 tests, 0 failures, 0 errors, 0 skipped**. |
| CI baseline | GitHub Actions [run 37836340839](https://github.com/praktimarc/mikrotik-facade/actions/runs/37836340839) succeeded under Ubuntu / Temurin 17 with the same 244 tests. |
| Dependency integrity | Consumer `pom.xml` pins the exact low-level version, without extra Maven repositories or `systemPath`. |

## Automated gates

```bash
mvn -B -U clean verify
mvn -B -U org.apache.maven.plugins:maven-javadoc-plugin:3.11.2:javadoc
```

GitHub Actions on `main` runs both commands, checks for forbidden low-level internal-package imports in `src/main/java`, and rejects selected tracked credential/archive extensions. The expanded [CI run 37837148351](https://github.com/praktimarc/mikrotik-facade/actions/runs/37837148351) completed successfully: 244 tests passed, the targeted hygiene check passed and Javadoc generation returned `BUILD SUCCESS`. **Documentation quality caveat:** Javadoc reported `100 warnings`, predominantly missing `@param` and `@return` tags. These are non-fatal for the present build but must not be misreported as warning-free; clean them in a separate documentation-quality pass before imposing a zero-warning release policy.

This is a **targeted repository hygiene check**, not a complete security audit. The existing JUnit suite covers redacted diagnostics, query/command semantics, backpressure and failure mapping. Review environment files and arbitrary resources manually before any public release; never commit actual device credentials or private keys.

## Unpublished word-level transport dependency

The `raw().words(...)` API currently requires the unpublished
`mikrotik:3.0.8-praktimarc.5-SNAPSHOT` from source. Build/install this
low-level snapshot before compiling the facade; the exact pinned SHA and
diagnostic examples are in [raw protocol words](raw-protocol-words.md).
Do not describe this snapshot as Maven Central ready until a new signed
low-level release has been staged and published.

## Snapshot API change

The public API now follows RouterOS menu paths more closely. This is an
intentional breaking change in the unreleased `0.1.0-SNAPSHOT`; the
[API migration table](api-migration-menu-hierarchy.md) lists required source
and import replacements for early adopters. The underlying RouterOS operations
and low-level dependency remain unchanged.

## Open before a first facade release

1. **Real RouterOS integration:** Seven target profiles are documented in [routeros-test-profiles.md](routeros-test-profiles.md). Read-only tests are optional/credential-gated, and a passing mock/loopback suite must not be represented as coverage of all real devices, RouterOS versions or wireless stacks.
2. **Release policy:** Decide version, supported RouterOS matrix, public API stability, licensing/attribution, artifacts, release notes and maintenance expectations. `0.1.0-SNAPSHOT` is a development baseline, not a released coordinate.
3. **Manual approvals:** Tag creation, GitHub Release, Maven Central upload and irreversible publication each require explicit separate approval.

## Deliberate v1 exclusions

- No RouterOS REST API, hidden `me.legrange.mikrotik.impl.*` API coupling or automatic reconnect/replay.
- No server-side regex simulation via lexicographic queries; presence-query words `?name` / `?-name` need a future public low-level parser capability.
- No binary upload API; the existing low-level binary download's cancellation cannot promise remote cancellation.

For the execution design, see [architecture.md](architecture.md), for RouterOS behavior see [routeros-compatibility.md](routeros-compatibility.md), and for the exact low-level contract see [low-level-contract.md](low-level-contract.md).

## Public example data and history

Public tests and documentation use synthetic RouterOS devices, addresses and credentials. GitHub Actions runs Gitleaks against incoming commits and the current checkout. These automated checks supplement manual review; they cannot demonstrate that historic commits contain no confidential context. Removing files from the current branch does not erase them from Git history.
