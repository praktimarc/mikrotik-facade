# mikrotik-facade v1 – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use subagent-driven development or executing-plans task-by-task. Every task follows TDD. Repository creation, Commit, Push, PR, Merge, Tag and Release remain separate approval gates.

**Goal:** Build `praktimarc/mikrotik-facade`, a Java-17 high-level RouterOS facade with shared Sync/Async execution, Flow streaming, typed models plus raw access, robust RouterOS compatibility resolution and complete functional parity with the intended behavior of the old ISPSup `mikrotikHandler`.

**Architecture:** CLI-oriented public modules use small internal typed operations on top of one listener-based `CommandEngine`. `mikrotik-java 3.0.8-praktimarc.4` owns transport, tags and response routing; the facade owns lifecycle, timeout/cancellation, Futures/Flow, typed models, capability/source/schema resolution and diagnostics.

**Tech Stack:**

```text
Java 17
Maven
io.github.praktimarc:mikrotik:3.0.8-praktimarc.4
JUnit 5
SLF4J API
java.util.concurrent.Flow
```

**Repository:**

```text
praktimarc/mikrotik-facade
```

**Maven coordinates:**

```text
io.github.praktimarc:mikrotik-facade
```

**Root Java package:**

```text
io.github.praktimarc.mikrotik.facade
```

**Approved architecture spec target:**

```text
docs/superpowers/specs/2026-10-06-mikrotik-facade-architecture.md
```

**Implementation plan target:**

```text
docs/superpowers/plans/2026-10-06-mikrotik-facade-v1.md
```

## Verification milestone (2026-10-08)

The low-level dependency `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4` has been publicly published and resolved from a fresh Maven repository. The facade passed `mvn -B -U clean verify` on Windows at commit `3e1534e9d71cdf821f7f5614780d49a75fe0a130` (244 tests) and [GitHub Actions run 37836340839](https://github.com/praktimarc/mikrotik-facade/actions/runs/37836340839) on Java 17 / Ubuntu (244 tests). Earlier per-task references to a missing Maven binary or pending Maven runs are **historical records of those task-specific environments**, not the current project status. Task-20 documentation and targeted hygiene checks passed in [CI run 37837148351](https://github.com/praktimarc/mikrotik-facade/actions/runs/37837148351), including 244 tests and successful Javadoc generation. Javadoc produced 100 non-fatal missing-tag warnings, documented as residual documentation-quality work.

## Global Constraints

- Java 17 for the facade.
- Low-level baseline exactly `3.0.8-praktimarc.4` initially.
- No dependency on `me.legrange.mikrotik.impl.*`.
- No RouterOS REST API.
- No transparent reconnect or automatic command replay.
- One `MikrotikRtrApi` represents one authenticated RouterOS session.
- Sync and Async share one listener-based execution pipeline.
- Unknown RouterOS properties must remain available through `raw()`.
- Capability, source selection and schema interpretation remain separate concepts.
- User callbacks never execute on RouterOS I/O threads.
- Sensitive credentials/configuration values must never appear unredacted in diagnostics.
- Binary download uses the existing low-level binary implementation.
- Functional parity does not mean method parity.
- TDD for every implementation task.
- Commit and Push are now pre-authorized for this project; PR, Merge, Tag, Release and Deployment remain separate gates.

## External prerequisite

**Fulfilled on 2026-10-08:** `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4` is now published to Maven Central and has been resolved anonymously from a fresh Maven repository. The facade is independently buildable in clean Java 17 Maven CI.

Do not solve this with:

```text
systemPath
checked-in dependency JAR
machine-specific repository paths
```

Local development may temporarily use a locally installed artifact, but this must not become part of the public project contract.

## Review Focus

1. A connection can fail during the small interval between low-level connect, listener registration, login and environment discovery without producing a half-initialized facade.
2. Completion, timeout, cancellation, close and connection-loss races must never produce two terminal outcomes.
3. A RouterOS path that exists but returns zero rows must never automatically be classified as unsupported or as the wrong data source.
4. Unknown/volatile RouterOS fields must survive mapping even when no typed interpretation exists.
5. Secret-containing arbitrary Raw commands must remain redacted even when TRACE logging is enabled.

---

# Task 1 – Repository and Maven foundation

**Files:**

```text
pom.xml
README.md
.gitignore
src/main/java/io/github/praktimarc/mikrotik/facade/package-info.java
src/test/java/io/github/praktimarc/mikrotik/facade/BuildBaselineTest.java
docs/superpowers/specs/2026-10-06-mikrotik-facade-architecture.md
docs/superpowers/plans/2026-10-06-mikrotik-facade-v1.md
```

**Produces:**

```text
Java 17 Maven project
JUnit 5 test execution
SLF4J API dependency
mikrotik-java dependency declaration
approved spec and plan in-repo
```

Steps:

- [x] Create the repository after explicit repository-creation approval.
- [x] Add the approved architecture spec, including the Functional-Parity clarification.
- [x] Create Maven project using `io.github.praktimarc:mikrotik-facade`.
- [x] Add Java-17 compiler configuration.
- [x] Add JUnit 5 and SLF4J API.
- [x] Add `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4` without `systemPath`.
- [x] Add `BuildBaselineTest` checking the facade can load public low-level API classes such as `ApiConnection`, `ApiCommandException`, `ApiDataException` and `ConnectionListener`.
- [x] Run (verified externally and in GitHub Actions on 2026-10-08):

```text
mvn clean verify
```

Expected: build succeeds when the approved Maven dependency source is available.

**Current environment limitation:** the execution environment used for Task 1 has Java but no Maven and no usable remote Maven resolution for `.4`; therefore the actual Maven/JUnit verification remains pending. Offline checks verified POM structure, Java-17 release target, absence of `systemPath`, absence of `impl.*` use in production code, and compilation of the production package with `javac --release 17`.

---

# Task 2 – Extract the real Functional-Parity inventory

This task happens before implementing the typed modules.

**Files:**

```text
docs/functional-parity.md
docs/ispsup-migration-notes.md
```

**Input:** actual current ISPSup `mikrotikHandler.java` and directly coupled old DTOs/callers.

For every intended old function record:

```text
old method
behavioral purpose
RouterOS path/command
inputs
output
side effects
old DTO
target facade module
DIRECT / COMPOSED / RAW_FALLBACK / OBSOLETE
ROS6/legacy behavior
known version/package dependency
known schema differences
error behavior
planned tests
implementation status
```

Example:

```text
getFirewallStateForClientIP
→ COMPOSED
→ firewall().addressList() generic query
→ /ip/firewall/address-list/print
→ ISPSup itself interprets active-clients membership as client firewall state
```

Rules:

- [x] Do not invent behavior missing from the old source.
- [x] Mark old bugs/workarounds explicitly instead of reproducing them.
- [x] Identify every typed facade primitive needed to make every non-obsolete row implementable.
- [x] Identify undocumented RouterOS behavior needing fixtures or research.
- [x] Review the completed matrix before module implementation.

This task determines the exact final set of typed module methods. No later module may silently omit a parity row.

---

# Task 3 – Core public raw models and exception hierarchy

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/RouterOsEntity.java
src/main/java/io/github/praktimarc/mikrotik/facade/RouterOsRecord.java
src/main/java/io/github/praktimarc/mikrotik/facade/RouterOsProperties.java

src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikFacadeException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikConnectionException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikAuthenticationException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikCommandException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikTimeoutException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikBackpressureException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikUnsupportedFeatureException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikDataException.java
src/main/java/io/github/praktimarc/mikrotik/facade/exception/MikrotikFileException.java
```

Tests must establish:

- [x] `RouterOsRecord` is immutable.
- [x] Unknown property names and exact original string values survive unchanged.
- [x] Required and optional access differ cleanly.
- [x] Typed conversion failure becomes `MikrotikDataException`.
- [x] `RouterOsProperties` preserves deterministic insertion order and rejects null keys/values.
- [x] Exception causes survive mapping.
- [x] RouterOS category can distinguish “missing” from legitimate category `0`.

Run:

```text
mvn -Dtest=RouterOsRecordTest,RouterOsPropertiesTest,*ExceptionTest test
```

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven command remains pending. The Task-3 production and test sources were compiled for Java 17, all 13 Task-3 tests passed with a local compatible JUnit runner, and `javadoc -Xdoclint:all` completed without warnings.

---

# Task 4 – Transport configuration and builder validation

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/MikrotikRtrApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/MikrotikRtrApiBuilder.java
src/main/java/io/github/praktimarc/mikrotik/facade/transport/ApiTransport.java
```

Public concepts:

```text
plain
tlsUnverified
tlsVerified
custom SocketFactory/SSLContext escape hatch
custom port
connect timeout
command timeout
bootstrap retry policy
callback executor
credentials
```

Tests:

- [x] Plain defaults to 8728.
- [x] TLS modes default to 8729.
- [x] Explicit port overrides default.
- [x] Invalid timeout/port/retry configuration fails before networking.
- [x] Unverified TLS is visibly named as such.
- [x] Builder diagnostics never expose password values.

No real connection logic yet.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. The Task-4 production and test sources were compiled for Java 17, all 13 Task-4 tests passed with a local compatible JUnit runner, `javac --release 17 -Xlint:all` completed without warnings, and `javadoc -Xdoclint:all` completed without warnings. Verified TLS additionally enforces hostname/endpoint identification.

---

# Task 5 – Environment models and bootstrap lifecycle

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/environment/RouterOsEnvironment.java
src/main/java/io/github/praktimarc/mikrotik/facade/environment/RouterOsSystemInfo.java
src/main/java/io/github/praktimarc/mikrotik/facade/environment/RouterOsPackage.java

src/main/java/io/github/praktimarc/mikrotik/facade/internal/session/SessionState.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/session/SessionLifecycle.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/session/BootstrapConfig.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/session/Bootstrapper.java
```

Bootstrap contract:

```text
validate
→ connect
→ register ConnectionListener
→ login
→ /system/resource
→ /system/package
→ create environment
→ OPEN
```

Tests:

- [x] No facade instance escapes before full successful bootstrap.
- [x] `/system/resource` malformed → `MikrotikDataException`.
- [x] `/system/package` explicitly unavailable → valid environment with unavailable package information.
- [x] Transport loss during bootstrap → `MikrotikConnectionException`.
- [x] Authentication rejection → `MikrotikAuthenticationException`.
- [x] Technical login failure may follow configured bootstrap retry.
- [x] Authentication rejection is not retried.
- [x] Each retry creates a fresh `ApiConnection`.
- [x] `environment()` returns immutable session snapshot.
- [x] Fatal idle connection loss transitions `OPEN → BROKEN`.
- [x] Intentional close does not create `BROKEN`.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. The final Task-5 JUnit sources compile against the verified public low-level signatures and all 13 Task-5 tests pass in the local compatible JUnit harness. Production sources compile with Java 17 and `-Xlint:all`; Javadoc/doclint completes without warnings.

---

# Task 6 – Low-level exception mapping and safe diagnostics

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/internal/error/ExceptionMapper.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/diagnostic/CommandDiagnosticRenderer.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/diagnostic/SecretRedactor.java
```

Tests:

- [x] `ApiConnectionException` → `MikrotikConnectionException`.
- [x] public `ApiCommandException` → `MikrotikCommandException`.
- [x] `ApiDataException` → `MikrotikDataException`.
- [x] no `impl.*` imports.
- [x] Category preserved only when `hasCategory()` is true.
- [x] Original cause retained.
- [x] Password, PSK, authentication response, private key and SNMP secret samples never appear in rendered diagnostics.
- [x] Arbitrary Raw commands still pass through redaction.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. All 9 Task-6 tests pass in the local compatible JUnit harness. Production sources compile for Java 17 without production warnings, Javadoc/doclint completes without warnings, and no `me.legrange.mikrotik.impl.*` import is present.

---

# Task 7 – RouterOsCommand, CommandResult and shared CommandEngine

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/internal/command/RouterOsCommand.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/command/CommandResult.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/command/OperationContext.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/command/CommandEngine.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/operation/RouterOsOperation.java
```

Core rule:

```text
all normal facade commands
→ ApiConnection.execute(String, ResultListener)
```

Tests:

- [x] `!re` records and `!done` completion metadata remain separate.
- [x] `ret` and arbitrary future completion properties survive.
- [x] Sync and Async go through the same internal engine.
- [x] no facade-level send lock exists.
- [x] no facade tag allocator/dispatcher exists.
- [x] cancellation before returned tag is remembered and propagated once tag exists.
- [x] cancellation after tag uses `ApiConnection.cancel(tag)`.
- [x] timeout produces `MikrotikTimeoutException` and best-effort cancel.
- [x] `done`, `trap`, timeout, cancel, close and connection-loss races produce exactly one logical terminal result.
- [x] interrupted Sync wait restores interrupt flag and becomes the agreed facade command error.
- [x] Low-Level processor callback performs no user code.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. The final Task-7 sources compile for Java 17 against the verified public low-level signatures, all 13 controlled Task-7 tests pass with latches/manual scheduling instead of timing-dependent sleeps, `-Xlint:all` reports no warnings from the new production classes, and Javadoc/doclint completes without warnings.

Use controlled fake `ApiConnection` implementations and latches, not timing-dependent sleeps.

---

# Task 8 – Public Sync/Async facade trees and Raw API

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/MikrotikRtrApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/MikrotikRtrApiBuilder.java
src/main/java/io/github/praktimarc/mikrotik/facade/async/AsyncMikrotikRtrApi.java

src/main/java/io/github/praktimarc/mikrotik/facade/raw/RawApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/raw/AsyncRawApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/raw/RawCommandBuilder.java
src/main/java/io/github/praktimarc/mikrotik/facade/raw/RawCommandResult.java

src/main/java/io/github/praktimarc/mikrotik/facade/internal/command/CommandEngine.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/session/SessionLifecycle.java

src/test/java/io/github/praktimarc/mikrotik/facade/RawApiIntegrationTest.java
```

Required semantics:

```text
raw().execute(...)
→ convenience record list

raw().command(...).execute()
→ RawCommandResult(records, completion)

async().raw()
→ mirrored API
```

Tests:

- [x] Raw read works through shared engine.
- [x] Raw write completion exposes `ret`.
- [x] Unknown fields survive.
- [x] Raw execution performs no typed capability filtering.
- [x] RouterOS unsupported raw command produces normal `MikrotikCommandException`.
- [x] Async public future completion occurs through callback executor.
- [x] operation after controlled close throws synchronously.
- [x] operation on broken session follows connection-error semantics.

Additional Task-8 integration coverage also verifies that async convenience cancellation still reaches the low-level RouterOS tag, controlled close best-effort cancels active finite async work, and caller-provided callback executors remain caller-owned.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. Eight controlled runtime scenarios pass on the final Task-8 implementation. The final `RawApiIntegrationTest` contains 11 JUnit test cases and compiles against the verified public `mikrotik-java 3.0.8-praktimarc.4` signatures. New production sources compile for Java 17 without production warnings and Javadoc/doclint completes without warnings.

---

# Task 9 – Flow streaming engine

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/internal/stream/RouterOsPublisher.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/stream/RouterOsSubscription.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/stream/SerialDelivery.java
```

Tests:

- [x] Publisher is cold.
- [x] each subscription starts its own RouterOS operation.
- [x] `request(1)` produces at most one delivered item.
- [x] saturating demand arithmetic.
- [x] `request(0)`/negative request terminates according to Flow rules.
- [x] bounded queue.
- [x] overflow → `MikrotikBackpressureException` + best-effort cancel.
- [x] no silent dropping.
- [x] events remain ordered.
- [x] callbacks for one subscription never execute concurrently.
- [x] cancel yields no later `onNext`, `onComplete` or `onError`.
- [x] stream has no generic overall command timeout.
- [x] slow subscriber never blocks RouterOS I/O thread.

Additional Task-9 race coverage verifies cancellation before the low-level tag is returned and the local-mapping-after-`!done` case, where a buffered record mapping failure must still produce `onError` rather than a stale `onComplete`.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. The final Task-9 JUnit source passes all 14 controlled tests. The three production classes compile with Java 17 and `-Xlint:all` without warnings, Javadoc/doclint completes without warnings, and no `me.legrange.mikrotik.impl.*` import is present.

---

# Task 10 – Compatibility framework and catalog

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/internal/capability/CapabilityRegistry.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/capability/CapabilityState.java

src/main/java/io/github/praktimarc/mikrotik/facade/internal/compat/DataSourcePlan.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/compat/DataSourceStrategy.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/compat/FeatureSourceResolver.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/compat/ResolvedRecord.java

docs/routeros-compatibility.md
```

Required strategies:

```text
SINGLE
PREFERRED_FALLBACK
CONDITIONAL
COMPOSITE
```

Tests:

- [x] definitive `SUPPORTED/UNSUPPORTED` caches.
- [x] technical probe failure remains `UNKNOWN` and is not negatively cached.
- [x] probes are read-only.
- [x] empty successful result does not mean unsupported.
- [x] existing path does not automatically mean authoritative data source.
- [x] COMPOSITE preserves source provenance.
- [x] no generic deduplication by MAC/id.
- [x] Compatibility diagnostics explain source selection without exposing secrets.

Additional Task-10 coverage verifies that explicit `UNKNOWN` probe results are retried, conflicting definitive cache knowledge is rejected instead of silently flipped, a successfully empty preferred source does not trigger fallback, and fallback is used only when the preferred result set is unavailable.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. All 12 Task-10 tests pass in the local compatible JUnit harness. The six production classes compile for Java 17 without production warnings, Javadoc/doclint completes without warnings, and no `me.legrange.mikrotik.impl.*` import is present.

---

# Task 11 – DHCP vertical slice

Exact public methods are finalized from the parity matrix.

Initial expected classes:

```text
src/main/java/io/github/praktimarc/mikrotik/facade/dhcp/DhcpServerApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/dhcp/AsyncDhcpServerApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/dhcp/DhcpLease.java
src/main/java/io/github/praktimarc/mikrotik/facade/dhcp/internal/DhcpLeaseMapper.java
src/main/java/io/github/praktimarc/mikrotik/facade/dhcp/internal/DhcpCircuitIdResolver.java
```

Tests derive from actual parity rows and fixtures.

Mandatory compatibility rule:

- [x] Circuit-ID and related volatile fields use explicit known schemas only.
- [x] Unknown representation returns empty typed value while `raw()` remains intact.
- [x] no “find any property containing circuit” heuristic.
- [x] 0-or-1 lookup returns `Optional`.
- [x] multiple rows where exactly one is expected → `MikrotikDataException`.

Finalized public v1 surface in this slice:

```java
mtApi.dhcpServer().findLeaseByMac(mac);
mtApi.dhcpServer().findLeaseByAddress(address);

mtApi.async().dhcpServer().findLeaseByMac(mac);
mtApi.async().dhcpServer().findLeaseByAddress(address);
```

The typed `DhcpLease` surface is restricted to the fields proven by the legacy DHCP DTO/parser inventory. RouterOS `.id` and every unknown/new field remain available through `raw()` rather than being invented as additional typed DTO fields. The legacy `active-client-id` parser defect is intentionally not reproduced.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. Eleven controlled DHCP/runtime scenarios pass, including the two root wiring assertions for `dhcpServer()` and `async().dhcpServer()`. The three final JUnit sources contain 11 test methods and compile against the verified project/runtime signatures. The five new DHCP production classes and the two root wiring changes compile for Java 17 without production warnings; Javadoc/doclint completes without warnings.

This task serves as the first complete typed reference module.

---

# Task 12 – Firewall module

Expected structure:

```text
firewall/FirewallApi.java
firewall/AsyncFirewallApi.java
firewall/filter/...
firewall/mangle/...
firewall/addresslist/...
```

The module must provide general RouterOS primitives rather than ISPSup-specific business methods.

Example parity:

```text
getFirewallStateForClientIP
→ COMPOSED
→ firewall().addressList().find(...)
```

Tests:

- [x] Generic typed/property-based Filter, Mangle and Address List querying.
- [x] empty result is normal.
- [x] common stable mutations have convenience methods where justified by the parity inventory.
- [x] flexible rule creation uses `RouterOsProperties`.
- [x] raw data remains attached to every typed rule.

Finalized Task-12 surface:

```java
mtApi.firewall().filter()
mtApi.firewall().mangle()
mtApi.firewall().addressList()

mtApi.async().firewall().filter()
mtApi.async().firewall().mangle()
mtApi.async().firewall().addressList()
```

Reads use equality properties via `RouterOsProperties`; flexible adds use the same property model and expose terminal `ret` as `Optional<String>`. Filter and Mangle provide `setDisabled(id, state)`; Mangle additionally provides `remove(id)`. No ISPSup-specific `active-clients` or global access-system semantics are embedded in the facade.

The legacy `getFireWallStateForClientIp` query used `address-list` under `/ip/firewall/address-list`. Task 12 corrects this to the RouterOS entry property `list`; `address-list` remains a separate firewall rule matcher/action property and is not used for address-list membership lookup.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. The controlled Task-12 runtime harness passes 12/12 scenarios. The five final JUnit sources contain 13 test methods and compile against the verified signatures. Production sources compile for Java 17 without production warnings and Javadoc/doclint completes without warnings.

---

# Task 13 – WiFi / CAPsMAN compatibility module

Expected structure:

```text
wifi/WifiApi.java
wifi/AsyncWifiApi.java
wifi/WifiRegistration.java
wifi/internal/WifiRegistrationMapper.java
wifi/internal/RegisteredClientsSourceResolver.java
```

Initial catalog must include:

```text
/caps-man/registration-table
/interface/wifi/registration-table

wireless
wifiwave2
wifi-qcom
wifi-qcom-ac
```

Tests with fixtures:

- [x] Legacy-only CAPsMAN.
- [x] new-WiFi-only CAPsMAN.
- [x] both path families present but only one relevant.
- [x] both stacks simultaneously relevant → COMPOSITE.
- [x] empty registration table remains valid.
- [x] `rx-signal` and `signal` normalize to the same typed concept.
- [x] all source raw fields remain preserved.


Finalized Task-13 surface:

```text
mtApi.wifi().registrationTable()
mtApi.async().wifi().registrationTable()
```

`WifiRegistration` keeps exact source provenance and the complete raw RouterOS record. Known registration fields are normalized without flattening source-specific or list-shaped data. In particular, legacy `rx-signal` and modern `signal` map to the same dBm concept; rates and paired packet/byte values remain lossless strings where RouterOS semantics are not a single scalar.

Source selection is explicit. Legacy CAPsMAN relevance is selected through `/caps-man/manager/print`; modern WiFi CAPsMAN uses `/interface/wifi/capsman/print`. An installed RouterOS 7.13+ `wifi-qcom`/`wifi-qcom-ac` local driver is an additional modern-source hint, but `/interface/wifi/registration-table/print` must succeed before that standalone source is selected. Package names never prove authority by themselves. If both managers are relevant, the plan is `COMPOSITE`; no MAC/id deduplication occurs. A successful empty table is valid and never triggers fallback.

The pre-7.13 `wifiwave2` name is retained as compatibility knowledge only. v1 does not silently invent a third `/interface/wifiwave2/...` source without a verified requirement and fixture.

DHCP enrichment and CAP/interface-prefix filtering stay in ISPSup composition. The old N+1 handler creation and lexical range-query workaround are not reproduced.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven test command remains pending. The controlled Task-13 runtime harness passes 14/14 scenarios. Task-13 production and JUnit sources compile against verified/signature-compatible Java 17 surfaces; Javadoc/doclint reports no warnings from the new WiFi sources. The repository's two older facade-wiring tests also require replacement of their stale `new RouterOsEnvironment()` construction with the existing environment factory API.

---

# Task 14 – Interfaces and monitoring

Expected structure:

```text
interfaces/InterfacesApi.java
interfaces/AsyncInterfacesApi.java
interfaces/InterfaceInfo.java
interfaces/InterfaceMonitorEntry.java
interfaces/internal/...
```

Tests:

- [x] normal typed interface reads.
- [x] hardware-/driver-dependent optional counters.
- [x] absent counter is not a parsing error unless contract says required.
- [x] unknown counters remain in `raw()`.
- [x] monitoring uses the shared Flow engine.
- [x] monitor cancellation maps to RouterOS cancellation.


Finalized Task-14 surface:

```text
mtApi.interfaces().list()
mtApi.interfaces().list(properties)
mtApi.interfaces().addresses()
mtApi.interfaces().addresses(properties)
mtApi.interfaces().monitor(interfaceName)

mtApi.async().interfaces().list(...)
mtApi.async().interfaces().addresses(...)
```

`InterfaceInfo`, `InterfaceAddress`, and `InterfaceMonitorEntry` are immutable `RouterOsEntity` types that preserve every received property through `raw()`. Only the semantically required interface name and IP address value are required; hardware-, driver-, and RouterOS-version-dependent counters/flags remain optional. Present malformed typed values still fail with `MikrotikDataException`.

Monitoring is a cold `Flow.Publisher` backed by the existing `RouterOsPublisher` engine and the continuous `/interface/monitor-traffic` command. The per-subscription bounded queue is 64 records. Flow cancellation maps to the existing RouterOS tag cancellation path. A session-scoped `StreamRegistry` makes controlled `MikrotikRtrApi.close()` cancel active monitor subscriptions before finite commands and also closes the race where registration happens after close has begun.

`interfaces().addresses(...)` provides the reusable primitive required by ISPSup `getCMTSIp()`; the fixed `cmts-internal` comment and last-octet-minus-one rule remain consumer policy and are not encoded in the facade.

**Current environment verification:** Maven is not installed in the execution environment, so the exact Maven/JUnit command remains pending. All Task-14 production and JUnit sources compile under `javac --release 17` against verified/signature-compatible surfaces. A focused runtime harness passes 9/9 mapping, optional-counter, raw-preservation, query-path, and stream-registry scenarios. The integration tests additionally cover cold monitoring, sample mapping, tag cancellation, and session-close cancellation when run under the project JUnit environment.

---

# Task 15 – Queue, SNMP and System modules

Exact method surface comes from the approved parity inventory.

Expected top-level structures:

```text
queue/...
snmp/...
system/...
```

Rules:

- [x] no blindly mirrored CLI tree.
- [x] stable concepts typed.
- [x] flexible or volatile RouterOS properties remain accessible through raw data/property writes.
- [x] schema compatibility rules documented in `routeros-compatibility.md`.
- [x] every relevant old-handler parity row covered by tests.

If the parity matrix shows that one of these areas is large enough for an independent review unit, split this task before implementation rather than creating oversized classes.


Finalized Task-15 surface:

```text
mtApi.queue().type().list()
mtApi.queue().type().find(properties)
mtApi.snmp().communities()
mtApi.snmp().findCommunityByName(name)
mtApi.snmp().setCommunityProperties(id, properties)
mtApi.snmp().setWriteAccess(id, enabled)
mtApi.system().ping(request)

mtApi.async().queue().type()...
mtApi.async().snmp()...
mtApi.async().system().ping(request)
```

Queue-type mapping keeps RouterOS rate/limit/burst quantities lossless as strings because legitimate RouterOS renderings can contain unit suffixes such as `KiB`; stable masks, booleans and burst-time are typed. PCQ fields remain optional for non-PCQ queue kinds and every source property remains in `raw()`.

SNMP exposes communities rather than the misleading legacy “SNMPv3 user” naming. The legacy hard-coded `admin` policy stays in ISPSup. Community lookup reads the typed community list and filters locally so the community value is not placed into a command query. Generic property writes and `setWriteAccess` reuse the same authenticated session. Credential-bearing authentication/encryption password fields have no typed getters and remain available only through `raw()`. Diagnostics additionally treat the `name` argument as sensitive specifically for `/snmp/community/...` paths.

Ping requests are always finite because `count > 0` is mandatory. Optional intervals must be positive whole milliseconds. `PingResult` retains all reply/status records and uses the latest usable cumulative summary from normal records, or terminal completion properties when supplied by the transport. 100% loss is normal data. RouterOS multicast semantics can produce `received > sent` and negative packet-loss percentages, so those values are preserved rather than rejected.

Maven is not installed in the execution environment; exact Maven/JUnit verification is therefore still performed through the project's usual external build when available.

---

# Task 16 – Files module and binary download

**Files:**

```text
files/FilesApi.java
files/AsyncFilesApi.java
files/RouterFile.java
files/FileDownloadResult.java
files/internal/...
```

Semantics:

```text
metadata/list/find
→ CommandEngine

binary download
→ ApiConnection.downloadFile(...)
```

Tests:

- [x] metadata calls use normal engine.
- [x] successful binary download returns `FileDownloadResult`.
- [x] local IOException → `MikrotikFileException`.
- [x] RouterOS command failure remains `MikrotikCommandException`.
- [x] transport loss remains `MikrotikConnectionException`.
- [x] async download runs on bounded blocking executor.
- [x] callback executor is not occupied by transfer itself.
- [x] Future cancellation is documented/local best-effort and does not pretend to have an unavailable remote chunk tag.
- [x] facade close terminates transfer via connection shutdown.
- [x] unsupported RouterOS binary read → `MikrotikUnsupportedFeatureException`.

No upload and no byte-stream publisher in v1.


Finalized Task-16 surface:

```text
mtApi.files().list()
mtApi.files().find(properties)
mtApi.files().findByName(name)
mtApi.files().download(remoteFile, localPath)

mtApi.async().files().list()
mtApi.async().files().find(properties)
mtApi.async().files().findByName(name)
mtApi.async().files().download(remoteFile, localPath)
```

Metadata is handled exclusively through the normal `CommandEngine` and `/file/print`. `findByName` returns `Optional.empty()` for absence and raises `MikrotikDataException` for duplicate exact-name matches. `RouterFile` keeps stable metadata fields typed and preserves every unknown field through `raw()`.

Binary content is delegated exclusively to the low-level `ApiConnection.downloadFile(...)` implementation. The facade does not duplicate `/file/read`, chunking, part-file staging, size validation or cleanup. Successful transfers return `FileDownloadResult(remoteFile, localFile, bytesWritten)`.

Async binary transfers use a session-owned bounded executor with 2 workers and a queue capacity of 16. Transfer work never runs on the callback executor. Public completion is dispatched through the callback executor. Future cancellation is local best-effort only; the facade does not claim immediate RouterOS chunk cancellation because low-level chunk tags are not exposed.

Controlled session close first blocks new/queued transfers, then closes the RouterOS connection, then shuts down the binary executor while callback infrastructure is still alive. This prevents queued transfers from starting during close and terminates running reads via connection shutdown.

No binary upload, byte-stream publisher or InputStream API is added in v1. Legacy RouterOS-to-SFTP upload remains an explicitly documented raw consumer fallback.

**Verification in this environment:** Maven remains unavailable. The Task-16 executor and files API compile under `javac --release 17` against signature-compatible project/low-level stubs, and a focused executor runtime harness passes.

---

# Task 17 – Complete remaining parity rows

Return to:

```text
docs/functional-parity.md
```

For every row:

```text
DIRECT
COMPOSED
RAW_FALLBACK
OBSOLETE
```

verify one of:

```text
implemented typed facade primitive
verified composition using facade primitives
intentional raw access with rationale
documented obsolete behavior
```

Tests:

- [x] no intended old-handler behavior remains unaccounted for.
- [x] no row is marked complete solely because `raw()` technically exists unless `RAW_FALLBACK` was explicitly accepted.
- [x] every known compatibility-dependent row points to catalog documentation and a test/fixture.
- [x] old bugs/workarounds have migration notes.


Task 17 closes every parity row from the supplied 2026-09-29 handler snapshot.

New facade primitives:

```text
dhcpServer().pools()
dhcpServer().countLeases(properties)
dhcpServer().removeLease(id)

wifi().remoteCaps()
wifi().findRemoteCapByBaseMac(mac)

async mirrors for all finite operations
```

`RouterOsCommand` now supports explicit valueless flags such as `count-only`; the raw command builder exposes the same primitive. DHCP lease counts read terminal `ret` structurally. Pool rows are preserved one-for-one and expose comma-separated RouterOS ranges as an immutable list; ISPSup-specific pool splitting, capacity arithmetic and `<poolName>-dhcp` naming remain consumer composition.

Remote CAPs use manager-driven source resolution. Enabled legacy and modern CAPsMAN managers can both be authoritative, producing `COMPOSITE` results with exact provenance. A successful empty modern remote-CAP result never triggers legacy fallback. Known `board-name` / `board` aliases normalize to one typed field; raw data remains complete.

The legacy WiFi-config method is intentionally obsolete because it always returned an empty list into an empty DTO. Mutable current-NAS getter/setter behavior is also intentionally obsolete because a facade session is bound to one connection endpoint. RouterOS-to-SFTP upload is an accepted structured `raw()` fallback; Task 17 verifies that `/tool/fetch` upload arguments can be expressed without adding ISPSup destination policy to the facade.

Terminal parity statuses are now restricted to `IMPLEMENTED`, `ACCEPTED_RAW_FALLBACK`, and `INTENTIONALLY_OBSOLETE`. A parity gate test rejects any remaining table row outside those statuses.

The current connected `praktimarc/ISPSup` repository still contains only a README, so the final re-scan of current production callers remains a separate migration gate and is not evidence against facade parity completion.

---

# Task 18 – Logging, diagnostics and security verification

Test the finished system as a whole:

- [x] SLF4J API only; no forced backend.
- [x] lifecycle INFO diagnostics.
- [x] capability/source DEBUG diagnostics.
- [x] no ordinary RouterOS `!trap` automatically treated as an internal ERROR.
- [x] sensitive values absent from all tested log levels.
- [x] arbitrary raw command secrets redacted.
- [x] Connection/state identifiers useful for diagnostics without exposing credentials.
- [x] compatibility fallback warnings are actionable.

Run full test suite:

```text
mvn clean verify
```


Task-18 implementation notes:

- One internal `FacadeDiagnostics` context is bound to each real facade session.
- Correlation uses local identifiers only: `session-N` and `op-N`. Host/IP, username, credentials and low-level RouterOS tags are not logged.
- INFO is limited to coarse session lifecycle (`ready`, `closing`, `closed`).
- DEBUG covers finite-command start/completion/cancellation, ordinary RouterOS command rejection, capability decisions and source selection.
- WARN covers timeout, unexpected connection loss, malformed/data failures and actual compatibility fallback.
- ERROR is reserved for unexpected internal runtime/invariant failures. Ordinary RouterOS `!trap` never maps to ERROR automatically.
- Command diagnostics are structural only: path plus argument/query key names, flags and requested property names. No command value is rendered.
- RouterOS command-error sanitization removes every actual argument/query value reflected in the RouterOS message before the normal secret-key heuristics run. This protects arbitrary future raw-command secrets as well as known credential names.
- Builder/bootstrap `toString()` output no longer exposes the configured RouterOS host.
- SLF4J remains API-only. Tests use an internal sink abstraction and do not introduce a logging backend.

Verification status in the current execution environment:

```text
javac --release 17 / focused runtime checks: available
mvn clean verify: NOT RUN — Maven is not installed in this environment
```

The Maven gate remains mandatory before claiming a release-ready v1 build; it is intentionally not recorded as executed here.

---

# Task 19 – Low-level contract and real-Router integration suite

Add a focused contract suite for the assumptions made about `mikrotik-java .4`:

- [x] concurrent listener commands.
- [x] generic completion metadata.
- [x] terminal command errors.
- [x] ConnectionListener unexpected-loss notification.
- [x] intentional close does not issue connection-loss notification.
- [x] active command failure on connection loss.
- [x] binary-download contract.

Real RouterOS tests remain separately enabled/credential-gated.

Document profiles for eventual real tests:

```text
ROS6 legacy
ROS7 no WiFi
ROS7 legacy wireless
ROS7 modern WiFi
legacy CAPsMAN
new WiFi CAPsMAN
parallel CAPsMAN where available
```

Normal public CI must not require router credentials.


Task-19 finalization:

- The exact low-level baseline is release commit `c170858efaac04fc78771903ef4c2bdbb6d35325` (`release: v3.0.8-praktimarc.4`).
- `MikrotikJavaContractTest` exercises the public low-level API against an independent loopback RouterOS wire peer. Contract tests do not import or reflect into `me.legrange.mikrotik.impl.*`.
- The binary contract uses a hostile 70,013-byte payload, validates byte-exact output, multi-chunk reads, filename/query preservation, safe local staging completion and coexistence with a tagged text listener.
- Normal Surefire explicitly excludes `*IT`. The `router-it` Maven profile activates Failsafe only for `*RouterIT`.
- `RouterReadOnlyRouterIT` is credential-gated through environment variables and fails fast when the explicit profile is enabled without valid configuration. The current integration test is read-only.
- Seven target profiles are documented in `docs/routeros-test-profiles.md`; Task 19 does not claim that all seven real targets are currently available.
- The consumer contract and dependency-upgrade rule are documented in `docs/low-level-contract.md`.

Historical Task-19 environment note: Maven was not available in that execution session, so that session did not execute the committed JUnit/Maven suite. Superseded by the external Windows and GitHub Actions verification recorded in the 2026-10-08 milestone above. No real-router test is claimed, because no credentialed target was supplied.

---

# Task 19a – Advanced server queries and client-side regex filtering

**Files:**

```text
RouterOsQuery.java
ClientSideFilter.java
internal/command/RouterOsCommand.java
raw/RawCommandBuilder.java
raw/RawCommandResult.java
wifi/WifiApi.java
wifi/AsyncWifiApi.java
docs/query-filtering.md
```

Requirements:

- [x] preserve existing simple equality-query API.
- [x] support repeated property names through a real expression tree.
- [x] support server-side `eq`, `notEq`, `lt`, `gt`, `not`, `and`, `or`.
- [x] do not implement regex as a fake RouterOS query operator.
- [x] provide explicit Java-side regex/predicate filtering.
- [x] provide sync/async WiFi registration convenience.
- [x] provide raw-result client-side filtering convenience.
- [x] redact all advanced-query values from diagnostics and reflected command failures.
- [x] verify the pinned low-level parser emits the intended RouterOS query stack.
- [x] add a >60-kB text-property low-level regression test.
- [x] document that `exists/notExists` is blocked by the pinned public low-level parser.

Compatibility decision:

```text
RouterOS protocol supports presence query words (?name / ?-name)
mikrotik-java .4 public string parser cannot safely emit them
→ v1 facade does not pretend to support exists/notExists
→ requires a future low-level public raw-query-word API and version bump
```

Client-side regex is intentionally named as such. It uses Java `Pattern` /
`Matcher.find()` after RouterOS records have been returned and therefore has different
performance characteristics from a server-side query.

---

# Task 20 – Public documentation and release-readiness

**Implementation status (2026-10-08):** Getting-started guide and release-readiness checklist added; README and historical Maven notes updated. Normal 244-test verification was successful on Windows and in GitHub Actions. Javadoc generation plus targeted source/artifact hygiene checks also passed in CI run 37837148351 (Javadoc emitted 100 non-fatal warnings, tracked separately). Real-router profiles and final ISPSup caller re-scan remain explicitly separate gates. No facade tag or release created.

**Files:**

```text
README.md
docs/getting-started.md
docs/routeros-compatibility.md
docs/functional-parity.md
docs/ispsup-migration-notes.md
```

Document:

```text
Builder/connect examples
Sync/Async examples
Flow example
Raw API
TLS modes
timeout/cancellation semantics
BROKEN vs CLOSED
environment()
compatibility behavior
binary-download cancellation limitation
exception hierarchy
supported RouterOS profiles
```

Final checks:

```text
mvn clean verify
Javadocs build without errors
no impl.* dependency
no checked-in credentials
no checked-in binary dependency workaround
no unresolved intended parity row
```

No Tag, Release or publication without separate approval.

---

# Post-v1 implementation deliverable – ISPSup migration handoff

After the facade implementation and final parity matrix are complete, create a separate **Claude Code handoff for ISPSup**.

It must be generated from the final code and the final parity matrix, not from the current architectural assumptions.

For every migrated old method it will specify:

```text
old mikrotikHandler method
classification: DIRECT / COMPOSED / RAW_FALLBACK / OBSOLETE
new facade calls
required imports
old DTO → new model mapping
changed exception handling
changed Optional/result handling
changed lifecycle/session ownership
compatibility implications
caller changes
tests to update/add
```

Example shape:

```text
getFirewallStateForClientIP
→ COMPOSED
→ mtApi.firewall().mangle().find(...)
→ ISPSup keeps the business interpretation "matching rule = firewall state"
```

The handoff must also identify places where old technical debt should be deleted instead of translated.

This handoff is explicitly a later task and is not created before the facade API has stabilized.

---

# Self-review result

**Spec coverage:** All approved architectural sections 1 and 2A–2L are represented.

**Known deliberate limitation:** Exact typed methods for the old ISPSup functionality cannot yet be enumerated because the actual current `mikrotikHandler.java` method inventory is not present in this planning context. Task 2 therefore makes the real-source Functional-Parity inventory a mandatory gate before typed module work. This is preferable to inventing APIs from memory.

**Type consistency:** Public root package, exception family, lifecycle concepts, command/result separation and Sync/Async structure are consistent throughout the plan.

**Critical races:** Explicitly assigned to Task 7 and Task 9.

**Compatibility ambiguity:** Explicitly assigned to Task 10 plus per-module fixture tests.

**Security:** Redaction is both an implementation concern and an independently tested requirement.

**Scope control:** Binary upload, REST, transparent reconnect, auto-replay and generic heuristic field discovery remain outside v1.
