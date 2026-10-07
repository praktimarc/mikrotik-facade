# ISPSup Functional-Parity Inventory

## Purpose

This document is the authoritative v1 parity inventory for migrating the intended RouterOS behavior of the legacy ISPSup `mikrotikHandler` into `mikrotik-facade`.

Functional parity does **not** mean method parity. ISPSup-specific policy remains in ISPSup; the facade provides reusable RouterOS primitives and typed models. A parity row is complete only when the old intended behavior is available directly, can be composed from facade primitives, has an explicitly accepted Raw fallback, or is intentionally obsolete.

## Source basis and confidence

The inventory was extracted from the newest available ISPSup artifacts supplied for analysis:

- `mikrotikHandler.java` snapshot dated 2026-09-29, 2,710 lines. This is the behavioral authority for the rows below.
- DTO snapshot `dtos.zip` dated 2026-09-29, including the RouterOS-related DTOs referenced by the handler.
- `LoginServiceImpl(4).java` dated 2026-05-21 plus the 2026-07-20 R01 session-management review diff as caller evidence.
- ISPSup project context dated 2026-08-25 for surrounding application architecture.

The connected `praktimarc/ISPSup` GitHub repository currently contains no source files. Therefore the handler and DTO inventory is complete for the supplied 2026-09-29 snapshots, while **current caller coverage must be re-verified against the real ISPSup repository before the final post-v1 migration handoff is generated**. Absence of a caller in the older caller snapshot is not treated as proof that a handler function is unused.

No credentials, internal destination details or other secrets from legacy sources are reproduced here.

## Classification

| Class | Meaning |
| --- | --- |
| `DIRECT` | The legacy purpose maps directly to a reusable typed facade operation. |
| `COMPOSED` | ISPSup reconstructs the old behavior from multiple or more general facade primitives. |
| `RAW_FALLBACK` | The behavior remains possible through `raw()`, with an explicit rationale for no typed v1 convenience API. |
| `OBSOLETE` | The old method is a broken stub, unsafe state mutation, workaround, or non-RouterOS accessor that must not be translated as a facade API. |

Implementation status started as `INVENTORIED`. After Task 17 every row must end in exactly one terminal state: `IMPLEMENTED`, `ACCEPTED_RAW_FALLBACK`, or `INTENTIONALLY_OBSOLETE`. COMPOSED rows marked `IMPLEMENTED` mean the reusable facade primitives are implemented; consumer-side policy composition still occurs during ISPSup migration.

## Parity matrix

| Old method | Behavioral purpose / RouterOS command | Input → output / side effects | Old DTO | Target / class | Compatibility, errors and planned tests | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `monGetFirewallRulesByComment(comment)` | Read filter rules matching a comment via `/ip/firewall/filter/print` with query `comment`. | `String` → list; read-only; legacy closes the connection. | `mtFirewallRule` | `firewall().filter()` / `DIRECT` | Map stable rule fields; preserve every unknown field in `raw()`. Tests: 0/1/many, field mapping, unknown properties, trap/error without partial silent success. | `IMPLEMENTED` |
| `util_changeSNMPV3UserToWrite(userName)` | Find SNMP community `admin` and set `write-access=true` via `/snmp/community`. | Legacy ignores `userName`; write side effect. | `MethodCallResult` | `snmp()` / `COMPOSED` | Task 15 exposes generic typed communities, expected-single lookup by name, generic property mutation and `setWriteAccess`. It does not preserve the ignored argument, hard-coded `admin` policy, MethodCallResult, or fresh-session write workaround. Community-name/password diagnostics are redacted. | `IMPLEMENTED` |
| `util_CheckIfFileExists(fileName)` | Check a RouterOS file by exact name through `/file/print`. | Filename → presence/absence; read-only. | `MethodCallResult` | `files().findByName()` / `DIRECT` | Task 16 returns `Optional<RouterFile>` instead of a result DTO. Absence is normal empty data; duplicate exact-name rows are a data error; command/transport failures keep typed exceptions. | `IMPLEMENTED` |
| `analyze_SendFileFromGWViaSFTP(fileName)` | Instruct RouterOS to upload an existing file using `/tool/fetch` with SFTP, `upload=yes` and `src-path`. | Filename → external network/file-transfer side effect to an ISPSup-specific destination. | `MethodCallResult` | `raw()` / `RAW_FALLBACK` | Task 17 verifies that the structured raw builder can express the required `/tool/fetch` SFTP upload shape. Destination host/path/credentials and success interpretation remain ISPSup policy; no typed v1 upload API is justified. | `ACCEPTED_RAW_FALLBACK` |
| `monGetDhcpPoolsOfNasAsArraylist()` | Read `/ip/pool/print`, split ranges, then count leases for `<poolName>-dhcp` using `/ip/dhcp-server/lease/print` with `count-only` and `ret` completion metadata. | No input → pools enriched with used/available counts; embeds ISPSup naming/capacity policy. | `DhcpIpPool` | `dhcpServer()` pool + lease primitives / `COMPOSED` | Task 17 adds `pools()` and `countLeases(properties)`. Pool rows preserve multiple ranges without duplicating the RouterOS row; count-only uses a valueless command flag and terminal `ret`. `<poolName>-dhcp`, range-capacity arithmetic and presentation remain ISPSup policy. | `IMPLEMENTED` |
| `nas_GetQueuePcqNasAsArraylist()` | Read non-default queue types via `/queue/type/print`, query `default=no`. | No input → queue type list; read-only. | `MikrotikRouterQueueType` | `queue().type()` / `DIRECT` | Task 15 implements generic exact-property queue-type reads. PCQ fields are optional; rate/limit/burst quantities remain exact strings to tolerate unit-decorated RouterOS renderings, stable masks/boolean/time are typed, and all unknown properties remain in `raw()`. ISPSup applies `default=no`. | `IMPLEMENTED` |
| `analyze_interfaceWifiRegistrationTableGet(capsIdentity)` | Read registrations from modern `/interface/wifi/registration-table/print` or legacy `/caps-man/registration-table/print`; legacy code also enriches every row with DHCP-by-MAC using a fresh handler. | CAP/interface prefix → registrations with nested DHCP lease; read-only but legacy creates N+1 RouterOS sessions. | `mikrotik_registrationTableEntry` + `mikrotik_IpDhcpServerLease` | `wifi().registrationTable()` + `dhcpServer()` / `COMPOSED` | Task 13 provides source-resolved legacy/modern registrations, `COMPOSITE`, provenance, empty-valid semantics and `signal`/`rx-signal` normalization. It deliberately does not copy empty/trap fallback, the lexical CAP-prefix range workaround, or N+1 DHCP sessions. ISPSup filters the returned registrations for the requested CAP/interface convention and enriches required rows through `dhcpServer()` on the same facade session. | `IMPLEMENTED` |
| `analyze_interfaceWifiConfigsGetByCapsIdentity(capsIdentity)` | Sends `/interface/wifi/print` with `master-interface` range query, logs raw data, never maps any row. | CAP identity → always empty list. | `Mikrotik_remoteCapConfigEntry` (confirmed empty class) | none / `OBSOLETE` | Confirmed non-functional stub: always-empty DTO/list despite RouterOS I/O. Task 17 intentionally creates no facade API; any future WiFi-config requirement must be specified independently. | `INTENTIONALLY_OBSOLETE` |
| `analyze_interfaceWifiCapsRemoteCapGet(macAddress)` | Find remote CAP by base MAC using modern `/interface/wifi/capsman/remote-cap/print` or legacy `/caps-man/remote-cap/print`. | Base MAC → expected single source-aware remote CAP; read-only. | `mikrotik_remoteCapEntry` | `wifi().remoteCaps()` / `DIRECT` | Task 17 uses enabled-manager source resolution, supports legacy `board` and modern `board-name`, preserves provenance/raw data, treats empty results as valid, removes the unused routerboard command, and rejects ambiguous expected-single matches. | `IMPLEMENTED` |
| `analyze_ipDhcpServerLeaseInfoGetByMAC(macAddress)` | Read `/ip/dhcp-server/lease/print` by `mac-address`. | MAC → zero or one typed lease; read-only. | `mikrotik_IpDhcpServerLease` | `dhcpServer().findLeaseByMac()` / `DIRECT` | Implemented in Task 11 with expected-single cardinality, exact active-client-id mapping and explicit agent-id schemas. Task 17 confirms it as the direct parity primitive. | `IMPLEMENTED` |
| `analyze_ipDhcpServerLeaseInfoGet(ipAddress)` | Read `/ip/dhcp-server/lease/print` by `address`. | IP → zero or one typed lease; read-only. | `mikrotik_IpDhcpServerLease` | `dhcpServer().findLeaseByAddress()` / `DIRECT` | Implemented in Task 11. Absence is `Optional.empty()` and multiple matches are `MikrotikDataException`; legacy default-object and active-client-id bugs are not reproduced. | `IMPLEMENTED` |
| `pingAClient(ip)` | Router-originated `/ping` with address, interval `0.5` and count `10`. | IP → aggregate ping result; network traffic side effect only. | `PingResult` | `system().ping()` / `DIRECT` | Task 15 provides mandatory finite count, optional validated interval, typed reply/status records and cumulative statistics. ISPSup uses count=10 and interval=500ms. Packet loss including 100% is normal data; RouterOS multicast negative-loss semantics are preserved; missing/malformed summary is a data error. | `IMPLEMENTED` |
| `getLeaseForClientmac(mac)` | DHCP lookup by MAC, return only the lease address. | MAC → consumer-selected address; legacy used empty-string sentinel. | none | `dhcpServer().findLeaseByMac()` / `COMPOSED` | Task 11 primitive is complete. ISPSup maps the optional lease to `lease.address()` and chooses its own absence representation; facade never returns an empty-string sentinel. | `IMPLEMENTED` |
| `getCMTSIp()` | Read `/ip/address/print` with comment `cmts-internal`, strip CIDR and decrement the final IPv4 octet to derive the CMTS peer address. | No input → derived IPv4 string; read-only. | none | `interfaces().addresses()` / `COMPOSED` | Task 14 implements generic typed `/ip/address/print` reads and exact-property queries. The comment and last-octet-minus-one rule remain ISPSup network policy; facade tests cover address mapping/query while ISPSup tests must cover peer derivation and IPv4 boundary assumptions. | `IMPLEMENTED` |
| `getFireWallStateForClientIp(clientIp)` | Actual source checks membership in firewall address list `active-clients` using `/ip/firewall/address-list/print`. | IP → boolean from existence of an address record; read-only. | none | `firewall().addressList()` / `COMPOSED` | Legacy sends query key `address-list`; Task 12 verifies and intentionally corrects this to the RouterOS address-list entry property `list`. Fixed list name and boolean interpretation stay in ISPSup. Tests: match/no match, exact schema/query fixture, trap/error. | `IMPLEMENTED` |
| `resetFireWallStateByClientIp(clientIp)` | Find Mangle rules by matching `src-address` and then `dst-address`; remove every collected id using `/ip/firewall/mangle/remove`. | IP → whether any rule existed; destructive write. | none | `firewall().mangle()` / `COMPOSED` | Legacy can collect the same id twice. Generic facade must not speculative-deduplicate, but ISPSup may explicitly deduplicate ids for this known operation. Tests: src-only, dst-only, both, duplicate id, none, removal failure. | `IMPLEMENTED` |
| `resetDHCPLeaseByClientIp(clientIp)` | Find lease by address and remove its exact RouterOS id. | IP → whether a lease existed; destructive write. | none | `dhcpServer()` find + remove / `COMPOSED` | Task 17 exposes `DhcpLease.id()` and `removeLease(id)`. ISPSup composes expected-single lookup then removes only when present. Empty id removal and last-row-wins ambiguity from the legacy implementation are intentionally eliminated. | `IMPLEMENTED` |
| `isFireWallActivatedOnTheNas()` | Find Mangle rule with comment `disable access system temporarily`; old business semantics return true when that rule has `disabled=true`. | No input → ISPSup access-system boolean; read-only. | none | `firewall().mangle()` / `COMPOSED` | Fixed comment and inversion are consumer policy. Missing rule currently yields false. Tests: disabled true/false, no row, multiple rows, malformed boolean. | `IMPLEMENTED` |
| `setFireWallActivatedOnTheNas(setState)` | Find every Mangle rule with comment `disable access system temporarily`; set each rule's `disabled` property to `setState`. | Boolean → legacy success flag; write side effect; old code opens another handler per matching rule. | none | `firewall().mangle()` / `COMPOSED` | Task 12 reuses one healthy facade session and does not carry forward the new-connection-before-write workaround. Legacy returns true even for zero matches; final ISPSup semantics must be explicit. Tests: zero/one/many, writes, failure propagation. | `IMPLEMENTED` |
| `mikroTik_fetchSmallFileByNameViaApi(fileName)` | Intended to read RouterOS file content; legacy implementation ignored `fileName`, used a fixed `.id` and returned raw protocol text. | Filename → binary-safe local download result in the facade; read-only. | none | `files().download()` / `COMPOSED` | Task 16 deliberately does not reproduce fixed-id, 4-KB or raw-text assumptions. The facade verifies the requested filename and delegates byte-exact transfer to the hardened low-level `downloadFile`; callers decode text themselves only when the file format is known to be text. | `IMPLEMENTED` |
| `getCurrentNasIp()` | Return mutable local handler field; no RouterOS command. | No input → host string. | none | none / `OBSOLETE` | Not a RouterOS feature. A facade session is bound to the endpoint used to create its underlying connection; parity requires no mutable host accessor. | `INTENTIONALLY_OBSOLETE` |
| `setCurrentNasIp(ip)` | Mutate local host field without rebuilding the underlying connection. | Host string → local mutation only. | none | none / `OBSOLETE` | Unsafe metadata/connection mismatch. Task 17 explicitly closes this as obsolete: connect a new facade session for another router. | `INTENTIONALLY_OBSOLETE` |

## Old DTO coverage

The 2026-09-29 DTO snapshot confirms the legacy shapes below. The facade does not preserve their GWT/ISPSup naming; it preserves RouterOS information plus complete `raw()` data.

| Old DTO | Relevant legacy fields | Migration direction |
| --- | --- | --- |
| `mtFirewallRule` | id, chain, protocol, src/dst address, src/dst port, in/out interface and interface-list, bytes, packets, invalid, dynamic, comment | typed firewall rule + `raw()` |
| `DhcpIpPool` | id, pool name, range, calculated first/last address, available/used counts | RouterOS pool/range record; utilization/business calculation composed by consumer |
| `MikrotikRouterQueueType` | name, kind, PCQ rate/total limit/burst rate/burst time/burst threshold | typed queue type with optional PCQ fields + `raw()` |
| `mikrotik_registrationTableEntry` | id, interface, SSID, MAC, uptime, signal, rates, packets/bytes, bps, VLAN, authorized, EAP identity, nested DHCP lease | `WifiRegistration`; DHCP enrichment remains consumer composition |
| `mikrotik_remoteCapEntry` | address, identity, board, serial, version, base MAC, common name, state, connected time, uptime | typed remote-CAP record + `raw()` |
| `mikrotik_IpDhcpServerLease` | address/MAC/client IDs, server/status/timing, active values, host, agent IDs, radius/dynamic/blocked/disabled/comment | `DhcpLease` + `raw()`; volatile typed fields optional |
| `PingResult` | host, size, TTL, time, sent/received, loss, min/avg/max RTT | typed ping result |
| `MethodCallResult` | success, info message, critical-error flag | not migrated; use return type/`Optional` plus facade exceptions |
| `Mikrotik_remoteCapConfigEntry` | no fields | obsolete with non-functional handler stub |
| `clientMacIpBundle` | MAC, IPv4, IPv6 | already deprecated in legacy DTO; no current handler parity row |

## Required facade primitives derived from the inventory

- `firewall().filter()`: property-based reads including comment; typed rule + raw data.
- `firewall().addressList()`: query entries by stable address-list properties; typed entry + raw data.
- `firewall().mangle()`: query by source/destination/comment, remove by id, set disabled state.
- `dhcpServer()`: find lease by MAC/address, remove by id, lease listing/count support, IP-pool reads.
- `queue().type()`: list/query queue types with optional PCQ concepts.
- `wifi().registrationTable()`: explicit source resolution across legacy CAPsMAN and modern WiFi stacks.
- `wifi().remoteCaps()`: find remote CAP by base MAC with source-aware schema mapping.
- `files()`: metadata lookup by name and binary download using the low-level implementation where supported.
- `snmp()`: generic SNMP community lookup/property mutation sufficient to change `write-access` without ISPSup policy.
- `system().ping()`: typed finite ping result.
- `interfaces().addresses()`: IP-address records/query including comment matching.
- `raw()`: `/tool/fetch` SFTP upload, and only if still required a documented legacy file-content fallback.

The facade must **not** add ISPSup-specific methods for `active-clients` semantics, CMTS peer derivation from `cmts-internal`, the `disable access system temporarily` convention, `<poolName>-dhcp` naming, ISPSup SFTP destination/configuration, DHCP enrichment of every WiFi registration, or mutable current-NAS state.

## Compatibility and migration follow-up status

The compatibility questions discovered by Task 2 are no longer an unqualified implementation backlog:

1. Address-list membership schema was resolved in Task 12: the entry property is `list`; the legacy `address-list` query is intentionally not reproduced.
2. CAP/interface lexical range-query behavior was intentionally removed in Task 13. Registration records are resolved by source first; ISPSup performs its own CAP/interface filtering.
3. Modern-only, legacy-only, composite and empty-valid WiFi registration behavior is covered by Task-13 tests/fixtures.
4. `wireless`, `wifiwave2`, `wifi-qcom` and `wifi-qcom-ac` package/menu knowledge is recorded in the compatibility catalog and exercised by Task 13.
5. DHCP relay agent schemas are explicit and covered by Task-11 fixtures; no fuzzy field discovery is used.
6. SNMP community `write-access` is exposed as a generic community mutation in Task 15 with sensitive diagnostic handling.
7. RouterOS SFTP upload is accepted in Task 17 as a structured `RAW_FALLBACK`; current ISPSup caller/configuration use still needs re-verification when the real source tree is available.
8. ROS6 `/file/get` content retrieval is not part of the typed v1 facade. Task 16 uses the verified low-level binary path and does not add an unproven compatibility fallback. Reconsider only if the current ISPSup source inventory proves a remaining ROS6 requirement.
9. The global access-system boolean around the fixed Mangle comment remains intentional ISPSup business policy. Its RouterOS primitives are implemented; final UI/business semantics belong to the consumer migration review.


## Task-2 gate

All public methods of the supplied 2026-09-29 `mikrotikHandler` are accounted for above. Facade primitive requirements are therefore defined from real source rather than memory.

Before the final ISPSup migration handoff, re-scan the then-current ISPSup repository and update caller references. Before implementing a compatibility-dependent typed row, resolve its listed fixture/research gate. No row may be marked implemented solely because `raw()` technically exists.
