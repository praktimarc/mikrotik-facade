# v1 release-readiness checklist

This document distinguishes **verified build/distribution evidence** from **remaining release decisions**. No `mikrotik-facade` tag, GitHub Release, or Maven Central deployment is implied by this checklist.

## Verified on 2026-10-08

| Gate | Evidence / scope |
| --- | --- |
| Public low-level dependency | `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4` POM and JAR downloaded anonymously from Maven Central into a fresh local Maven repository. |
| Facade Java 17 build | At facade commit `3e1534e9d71cdf821f7f5614780d49a75fe0a130`, `mvn -B -U clean verify` on Windows built the facade JAR and ran **244 tests, 0 failures, 0 errors, 0 skipped**. |
| CI baseline | GitHub Actions [run 37836340839](https://github.com/praktimarc/mikrotik-facade/actions/runs/37836340839) succeeded under Ubuntu / Temurin 17 with the same 244 tests. |
| Dependency integrity | Consumer `pom.xml` pins the exact low-level version, without extra Maven repositories or `systemPath`. |
| Intentional parity | The legacy-handler parity matrix uses the terminal statuses `IMPLEMENTED`, `ACCEPTED_RAW_FALLBACK` and `INTENTIONALLY_OBSOLETE` for the actual method rows (not a promise of current ISPSup caller migration). |

## Automated gates

```bash
mvn -B -U clean verify
mvn -B -U org.apache.maven.plugins:maven-javadoc-plugin:3.11.2:javadoc
```

GitHub Actions on `main` runs both commands, checks for forbidden low-level internal-package imports in `src/main/java`, and rejects selected tracked credential/archive extensions. Keep Javadocs uncompromised: a Javadoc failure is a release gate, not an excuse to turn off doclint. The CI result for the added Javadoc step must be reviewed before calling this specific gate passed.

This is a **targeted repository hygiene check**, not a complete security audit. The existing JUnit suite covers redacted diagnostics, query/command semantics, backpressure and failure mapping. Review environment files and arbitrary resources manually before any public release; never commit actual device credentials or private keys.

## Open before a first facade release

1. **Real RouterOS integration:** Seven target profiles are documented in [routeros-test-profiles.md](routeros-test-profiles.md). Read-only tests are optional/credential-gated, and a passing mock/loopback suite must not be represented as coverage of all real devices, RouterOS versions or wireless stacks.
2. **Current ISPSup caller inventory:** The parity matrix was prepared from supplied September 2026 handler/DTO snapshots. Confirm real callers in the then-current ISPSup sources before the final migration handoff; this is a **consumer-migration gate**, not a missing low-level dependency.
3. **Release policy:** Decide version, supported RouterOS matrix, public API stability, licensing/attribution, artifacts, release notes and maintenance expectations. `0.1.0-SNAPSHOT` is a development baseline, not a released coordinate.
4. **Manual approvals:** Tag creation, GitHub Release, Maven Central upload and irreversible publication each require explicit separate approval.

## Deliberate v1 exclusions

- No RouterOS REST API, hidden `me.legrange.mikrotik.impl.*` API coupling or automatic reconnect/replay.
- No server-side regex simulation via lexicographic queries; presence-query words `?name` / `?-name` need a future public low-level parser capability.
- No binary upload API; the existing low-level binary download's cancellation cannot promise remote cancellation.

For migration semantics and source evidence, see [functional-parity.md](functional-parity.md) and [ispsup-migration-notes.md](ispsup-migration-notes.md). For the exact low-level contract, see [low-level-contract.md](low-level-contract.md).
