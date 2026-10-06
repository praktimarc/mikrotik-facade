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

Before the facade becomes independently buildable in clean Maven CI, `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4` must be resolvable from an approved Maven repository.

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
- [ ] Run:

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

- [ ] `ApiConnectionException` → `MikrotikConnectionException`.
- [ ] public `ApiCommandException` → `MikrotikCommandException`.
- [ ] `ApiDataException` → `MikrotikDataException`.
- [ ] no `impl.*` imports.
- [ ] Category preserved only when `hasCategory()` is true.
- [ ] Original cause retained.
- [ ] Password, PSK, authentication response, private key and SNMP secret samples never appear in rendered diagnostics.
- [ ] Arbitrary Raw commands still pass through redaction.

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

- [ ] `!re` records and `!done` completion metadata remain separate.
- [ ] `ret` and arbitrary future completion properties survive.
- [ ] Sync and Async go through the same internal engine.
- [ ] no facade-level send lock exists.
- [ ] no facade tag allocator/dispatcher exists.
- [ ] cancellation before returned tag is remembered and propagated once tag exists.
- [ ] cancellation after tag uses `ApiConnection.cancel(tag)`.
- [ ] timeout produces `MikrotikTimeoutException` and best-effort cancel.
- [ ] `done`, `trap`, timeout, cancel, close and connection-loss races produce exactly one logical terminal result.
- [ ] interrupted Sync wait restores interrupt flag and becomes the agreed facade command error.
- [ ] Low-Level processor callback performs no user code.

Use controlled fake `ApiConnection` implementations and latches, not timing-dependent sleeps.

---

# Task 8 – Public Sync/Async facade trees and Raw API

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/async/AsyncMikrotikRtrApi.java

src/main/java/io/github/praktimarc/mikrotik/facade/raw/RawApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/raw/AsyncRawApi.java
src/main/java/io/github/praktimarc/mikrotik/facade/raw/RawCommandBuilder.java
src/main/java/io/github/praktimarc/mikrotik/facade/raw/RawCommandResult.java
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

- [ ] Raw read works through shared engine.
- [ ] Raw write completion exposes `ret`.
- [ ] Unknown fields survive.
- [ ] Raw execution performs no typed capability filtering.
- [ ] RouterOS unsupported raw command produces normal `MikrotikCommandException`.
- [ ] Async public future completion occurs through callback executor.
- [ ] operation after controlled close throws synchronously.
- [ ] operation on broken session follows connection-error semantics.

---

# Task 9 – Flow streaming engine

**Files:**

```text
src/main/java/io/github/praktimarc/mikrotik/facade/internal/stream/RouterOsPublisher.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/stream/RouterOsSubscription.java
src/main/java/io/github/praktimarc/mikrotik/facade/internal/stream/SerialDelivery.java
```

Tests:

- [ ] Publisher is cold.
- [ ] each subscription starts its own RouterOS operation.
- [ ] `request(1)` produces at most one delivered item.
- [ ] saturating demand arithmetic.
- [ ] `request(0)`/negative request terminates according to Flow rules.
- [ ] bounded queue.
- [ ] overflow → `MikrotikBackpressureException` + best-effort cancel.
- [ ] no silent dropping.
- [ ] events remain ordered.
- [ ] callbacks for one subscription never execute concurrently.
- [ ] cancel yields no later `onNext`, `onComplete` or `onError`.
- [ ] stream has no generic overall command timeout.
- [ ] slow subscriber never blocks RouterOS I/O thread.

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

- [ ] definitive `SUPPORTED/UNSUPPORTED` caches.
- [ ] technical probe failure remains `UNKNOWN` and is not negatively cached.
- [ ] probes are read-only.
- [ ] empty successful result does not mean unsupported.
- [ ] existing path does not automatically mean authoritative data source.
- [ ] COMPOSITE preserves source provenance.
- [ ] no generic deduplication by MAC/id.
- [ ] Compatibility diagnostics explain source selection without exposing secrets.

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

- [ ] Circuit-ID and related volatile fields use explicit known schemas only.
- [ ] Unknown representation returns empty typed value while `raw()` remains intact.
- [ ] no “find any property containing circuit” heuristic.
- [ ] 0-or-1 lookup returns `Optional`.
- [ ] multiple rows where exactly one is expected → `MikrotikDataException`.

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

- [ ] Generic typed/property-based Filter, Mangle and Address List querying.
- [ ] empty result is normal.
- [ ] common stable mutations have convenience methods where justified by the parity inventory.
- [ ] flexible rule creation uses `RouterOsProperties`.
- [ ] raw data remains attached to every typed rule.

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

- [ ] Legacy-only CAPsMAN.
- [ ] new-WiFi-only CAPsMAN.
- [ ] both path families present but only one relevant.
- [ ] both stacks simultaneously relevant → COMPOSITE.
- [ ] empty registration table remains valid.
- [ ] `rx-signal` and `signal` normalize to the same typed concept.
- [ ] all source raw fields remain preserved.

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

- [ ] normal typed interface reads.
- [ ] hardware-/driver-dependent optional counters.
- [ ] absent counter is not a parsing error unless contract says required.
- [ ] unknown counters remain in `raw()`.
- [ ] monitoring uses the shared Flow engine.
- [ ] monitor cancellation maps to RouterOS cancellation.

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

- [ ] no blindly mirrored CLI tree.
- [ ] stable concepts typed.
- [ ] flexible or volatile RouterOS properties remain accessible through raw data/property writes.
- [ ] schema compatibility rules documented in `routeros-compatibility.md`.
- [ ] every relevant old-handler parity row covered by tests.

If the parity matrix shows that one of these areas is large enough for an independent review unit, split this task before implementation rather than creating oversized classes.

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

- [ ] metadata calls use normal engine.
- [ ] successful binary download returns `FileDownloadResult`.
- [ ] local IOException → `MikrotikFileException`.
- [ ] RouterOS command failure remains `MikrotikCommandException`.
- [ ] transport loss remains `MikrotikConnectionException`.
- [ ] async download runs on bounded blocking executor.
- [ ] callback executor is not occupied by transfer itself.
- [ ] Future cancellation is documented/local best-effort and does not pretend to have an unavailable remote chunk tag.
- [ ] facade close terminates transfer via connection shutdown.
- [ ] unsupported RouterOS binary read → `MikrotikUnsupportedFeatureException`.

No upload and no byte-stream publisher in v1.

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

- [ ] no intended old-handler behavior remains unaccounted for.
- [ ] no row is marked complete solely because `raw()` technically exists unless `RAW_FALLBACK` was explicitly accepted.
- [ ] every known compatibility-dependent row points to catalog documentation and a test/fixture.
- [ ] old bugs/workarounds have migration notes.

---

# Task 18 – Logging, diagnostics and security verification

Test the finished system as a whole:

- [ ] SLF4J API only; no forced backend.
- [ ] lifecycle INFO diagnostics.
- [ ] capability/source DEBUG diagnostics.
- [ ] no ordinary RouterOS `!trap` automatically treated as an internal ERROR.
- [ ] sensitive values absent from all tested log levels.
- [ ] arbitrary raw command secrets redacted.
- [ ] Connection/state identifiers useful for diagnostics without exposing credentials.
- [ ] compatibility fallback warnings are actionable.

Run full test suite:

```text
mvn clean verify
```

---

# Task 19 – Low-level contract and real-Router integration suite

Add a focused contract suite for the assumptions made about `mikrotik-java .4`:

- [ ] concurrent listener commands.
- [ ] generic completion metadata.
- [ ] terminal command errors.
- [ ] ConnectionListener unexpected-loss notification.
- [ ] intentional close does not issue connection-loss notification.
- [ ] active command failure on connection loss.
- [ ] binary-download contract.

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

---

# Task 20 – Public documentation and release-readiness

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
