# Architecture of mikrotik-facade

This is an overview of the **current public library**, independent of any
particular application that consumes it. See [getting started](getting-started.md),
[practical examples](examples.md), and the
[low-level contract](low-level-contract.md) for implementation-facing detail.

## Scope and dependencies

- Java 17; Maven coordinates: `io.github.praktimarc:mikrotik-facade`.
- Classic RouterOS binary API via the public low-level
  `io.github.praktimarc:mikrotik:3.0.8-praktimarc.5` client.
- No RouterOS REST support or coupling to low-level `impl.*` packages.
- One `MikrotikRtrApi` instance owns one authenticated RouterOS session.

## Public API composition

The builder supplies host, credentials, verified-TLS or explicitly selected
transport, timeouts, optional bootstrap retries and an optional caller-managed
callback executor. `connect()` authenticates, reads environment information,
and initializes the session.

The root exposes both a typed API tree and a raw escape hatch:

| Public module | Responsibility |
| --- | --- |
| `ip().address()` | IPv4 assignments (`/ip/address`) |
| `ip().pool()` | IP pools independent of DHCP (`/ip/pool`) |
| `ip().dhcpServer().lease()` | DHCP leases (`/ip/dhcp-server/lease`) |
| `ip().firewall().filter()` | Firewall filter rules |
| `ip().firewall().mangle()` | Firewall Mangle rules |
| `ip().firewall().addressList()` | Firewall address lists |
| `wifi()` | Legacy/modern WiFi registration and remote-CAP resolution |
| `interfaces()` | Interfaces, IP addresses and traffic monitoring |
| `queue().type()` | Queue-type reads and PCQ parameters |
| `snmp().community()` | SNMP community reads and supported writes |
| `file()` | RouterOS file metadata and downloads |
| `ping(PingRequest)` | Finite router-originated root-level /ping |
| `raw()` | Explicit RouterOS commands and unmodified raw results |
| `async()` | Asynchronous counterparts with the same menu hierarchy |

The public typed entities preserve original RouterOS properties through
`raw()` so that additional firmware-specific fields remain accessible.

## Command execution

Typed commands are represented internally by immutable
`RouterOsCommand` values and `RouterOsOperation<T>` mappers.
`CommandEngine` submits them using the low-level listener API and maps
regular `!re` records separately from terminal `!done` metadata.
The same operation definitions back synchronous calls and asynchronous
`CompletableFuture` calls.

A session has explicit lifecycle states. Fatal connection loss marks the
session unusable and terminates active operations. The facade deliberately
does not reconnect and replay writes, because replay could duplicate
mutations.

Finite command timeouts and cancellations terminate local operations and
attempt RouterOS command cancellation on a best-effort basis. They do not
guarantee reversal of changes already made on the router.

## Continuous monitoring

Interface traffic monitoring uses Java `Flow.Publisher` and starts I/O
only after subscription. The subscription supports backpressure, bounded
queues and explicit cancellation. Closing the facade session cancels active
subscriptions.

## Version and source compatibility

`RouterOsEnvironment` captures RouterOS version and package knowledge at
bootstrap. Capability probes distinguish supported, definitively unsupported,
and unknown operations for the session.

WiFi registrations and CAPsMAN remote CAPs select legacy and/or modern
sources through manager/path relevance. Multiple authoritative sources can
produce a composite result; source provenance is preserved rather than
automatically deduplicated.

A successful empty result is distinct from an unsupported command.
Typed mappers normalize documented aliases while preserving raw values.
See the [compatibility catalog](routeros-compatibility.md) for known
source and schema variants.

## Query safety

RouterOS menus do not consistently honor every equality query.
Typed equality-query operations therefore verify returned record properties
client-side before mapping. Matching is case-insensitive and recognizes
RouterOS boolean aliases (`yes/no`, `true/false`); missing properties
do not match. Expected-single lookups check uniqueness **after** local
validation.

The Raw API deliberately does not add implicit filtering, because it
must expose the actual RouterOS result. See
[query filtering](query-filtering.md) for advanced server-side queries
and explicit client-side regex.

The DHCP `count-only` command has no per-record data to verify.
Filtered lease counts therefore read rows and count locally; an unfiltered
count retains RouterOS `count-only`.

**Mutations that use a previously looked-up RouterOS `.id` are not
atomic read-and-write transactions.** Applications must authorize and
validate their own write operations.

## Errors and diagnostics

Typed failures derive from `MikrotikFacadeException` and include separate
authentication, connection, command, timeout, data and file failures.
The diagnostics layer emits structural command and correlation metadata,
rather than credentials, configured hosts or arbitrary command values.
Applications should likewise avoid logging `raw()` for sensitive tables
such as SNMP communities.

## Validation and evolution

- `mvn clean verify` runs the unit, mapping, concurrency and low-level
  wire contract suite without RouterOS credentials.
- `mvn clean verify -Prouter-it` opts into read-only integration tests
  against an explicitly configured router.
- GitHub Actions also builds Javadoc and checks incoming and tracked
  files for secrets.
- Upgrading the low-level client requires rechecking the
  [pinned contract](low-level-contract.md), not merely changing its Maven version.

The public API remains an unreleased `0.1.0-SNAPSHOT` until an explicit
release and supported-version policy are published.
