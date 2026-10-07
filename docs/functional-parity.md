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

Implementation status starts as `INVENTORIED`. Rows whose required facade primitive is implemented and covered are marked `IMPLEMENTED`; COMPOSED rows still require consumer-side composition during the final ISPSup migration.

## Parity matrix

| Old method | Behavioral purpose / RouterOS command | Input → output / side effects | Old DTO | Target / class | Compatibility, errors and planned tests | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `monGetFirewallRulesByComment(comment)` | Read filter rules matching a comment via `/ip/firewall/filter/print` with query `comment`. | `String` → list; read-only; legacy closes the connection. | `mtFirewallRule` | `firewall().filter()` / `DIRECT` | Map stable rule fields; preserve every unknown field in `raw()`. Tests: 0/1/many, field mapping, unknown properties, trap/error without partial silent success. | `IMPLEMENTED` |
| `util_changeSNMPV3UserToWrite(userName)` | Find SNMP community `admin` via `/snmp/community/print`, then set `write-access=true` with `/snmp/community/set`. | Legacy ignores `userName`; returns `MethodCallResult`; write side effect; opens another handler for the write. | `MethodCallResult` | `snmp()` / `COMPOSED` | The name is misleading: source manipulates an SNMP community, not a generic SNMPv3 user. Do not preserve the ignored argument or hard-coded consumer policy. Verify `write-access` semantics on supported ROS profiles. | `INVENTORIED` |
| `util_CheckIfFileExists(fileName)` | Check a RouterOS file by name via `/file/print`, selecting `.id`. | Filename → legacy success/absence result; read-only. | `MethodCallResult` | `files()` / `DIRECT` | Normal absence becomes typed absence such as `Optional.empty()`, not an error DTO. Tests: present, absent, command/transport failure. | `INVENTORIED` |
| `analyze_SendFileFromGWViaSFTP(fileName)` | Instruct RouterOS to upload an existing file using `/tool/fetch` with SFTP, `upload=yes` and `src-path`. | Filename → legacy result; external network/file-transfer side effect to an ISPSup-specific destination. | `MethodCallResult` | `raw()` / `RAW_FALLBACK` | Destination host/path is application configuration and stays out of the facade. Verify `/tool/fetch` SFTP and completion semantics only if current ISPSup still needs this path. | `INVENTORIED` |
| `monGetDhcpPoolsOfNasAsArraylist()` | Read `/ip/pool/print`, split ranges, then count leases for `<poolName>-dhcp` using `/ip/dhcp-server/lease/print` with `count-only` and `ret` completion metadata. | No input → pools enriched with used/available counts; embeds ISPSup naming/capacity policy. | `DhcpIpPool` | `dhcpServer()` pool + lease primitives / `COMPOSED` | Keep `<poolName>-dhcp` and capacity calculation in ISPSup. Read `ret` structurally, never by fixed response-line position. Tests: one/multiple ranges, completion metadata, malformed/missing count. | `INVENTORIED` |
| `nas_GetQueuePcqNasAsArraylist()` | Read non-default queue types via `/queue/type/print`, query `default=no`. | No input → queue type list; read-only. | `MikrotikRouterQueueType` | `queue().type()` / `DIRECT` | PCQ-specific fields are optional for other kinds/schemas; raw record stays complete. Tests: PCQ, non-PCQ, missing optional fields, future properties. | `INVENTORIED` |
| `analyze_interfaceWifiRegistrationTableGet(capsIdentity)` | Read registrations from modern `/interface/wifi/registration-table/print` or legacy `/caps-man/registration-table/print`; enrich every row with DHCP-by-MAC using a fresh handler. | CAP/interface prefix → registrations with nested DHCP lease; read-only but legacy creates N+1 RouterOS sessions. | `mikrotik_registrationTableEntry` + `mikrotik_IpDhcpServerLease` | `wifi().registrationTable()` + `dhcpServer()` / `COMPOSED` | Do not copy empty/trap ⇒ legacy fallback. Use capability/source resolution and allow `COMPOSITE`. Normalize known `signal`/`rx-signal`. Prefix range-query semantics need fixtures. Test modern-only, legacy-only, both-path cases, empty valid result, aliases, raw preservation. | `INVENTORIED` |
| `analyze_interfaceWifiConfigsGetByCapsIdentity(capsIdentity)` | Sends `/interface/wifi/print` with `master-interface` range query, logs raw data, never maps any row. | CAP identity → always empty list. | `Mikrotik_remoteCapConfigEntry` (confirmed empty class) | none / `OBSOLETE` | Non-functional stub. Do not create a facade API for it. A future WiFi configuration feature needs a new requirement and tests. | `INVENTORIED` |
| `analyze_interfaceWifiCapsRemoteCapGet(macAddress)` | Find remote CAP by base MAC using modern `/interface/wifi/capsman/remote-cap/print` or legacy `/caps-man/remote-cap/print`. | Base MAC → one remote-CAP-shaped result; read-only; legacy also sends an unused `/system/routerboard/print`. | `mikrotik_remoteCapEntry` | `wifi().remoteCaps()` / `DIRECT` | Replace empty/trap fallback with explicit source resolution. Normalize known `board-name`/`board`. Remove unused routerboard command. Tests: modern, legacy, both stacks, empty, alias mapping, raw fields, cardinality. | `INVENTORIED` |
| `analyze_ipDhcpServerLeaseInfoGetByMAC(macAddress)` | Read `/ip/dhcp-server/lease/print` by `mac-address`. | MAC → legacy single DTO/default object; read-only. | `mikrotik_IpDhcpServerLease` | `dhcpServer().findLeaseByMac()` / `DIRECT` | Legacy parser stores the literal key helper instead of the received `active-client-id`; add regression test and do not reproduce. Agent IDs use explicit schemas only; unknown typed value stays empty while `raw()` remains complete. Tests: 0/1/multiple. | `INVENTORIED` |
| `analyze_ipDhcpServerLeaseInfoGet(ipAddress)` | Read `/ip/dhcp-server/lease/print` by `address`. | IP → legacy single DTO/default object; read-only. | `mikrotik_IpDhcpServerLease` | `dhcpServer().findLeaseByAddress()` / `DIRECT` | Same `active-client-id` parsing bug and schema rules as MAC lookup. Absence → `Optional.empty()`; ambiguous expected-single result → `MikrotikDataException`. | `INVENTORIED` |
| `pingAClient(ip)` | Router-originated `/ping` with address, interval `0.5` and count `10`. | IP → aggregate ping result; network traffic side effect only. | `PingResult` | `system().ping()` / `DIRECT` | Map host/size/TTL/time and sent/received/loss/min/avg/max RTT. Tests: normal replies, packet loss, no reply, malformed summary, trap/timeout. Do not return partial/default DTO after interruption. | `INVENTORIED` |
| `getLeaseForClientmac(mac)` | DHCP lookup by MAC, return only the lease address. | MAC → address string; legacy uses empty-string sentinel. | none | `dhcpServer().findLeaseByMac()` / `COMPOSED` | ISPSup maps `Optional<DhcpLease>` to the address it needs. No facade empty-string sentinel. | `INVENTORIED` |
| `getCMTSIp()` | Read `/ip/address/print` with comment `cmts-internal`, strip CIDR and decrement the final IPv4 octet to derive the CMTS peer address. | No input → derived IPv4 string; read-only. | none | `interfaces().addresses()` / `COMPOSED` | The comment and last-octet-minus-one rule are ISPSup network policy, not facade behavior. Facade tests cover address lookup; ISPSup tests cover peer derivation. Old source defines no general IPv6/boundary behavior. | `INVENTORIED` |
| `getFireWallStateForClientIp(clientIp)` | Actual source checks membership in firewall address list `active-clients` using `/ip/firewall/address-list/print`. | IP → boolean from existence of an address record; read-only. | none | `firewall().addressList()` / `COMPOSED` | Legacy sends query key `address-list`; Task 12 verifies and intentionally corrects this to the RouterOS address-list entry property `list`. Fixed list name and boolean interpretation stay in ISPSup. Tests: match/no match, exact schema/query fixture, trap/error. | `IMPLEMENTED` |
| `resetFireWallStateByClientIp(clientIp)` | Find Mangle rules by matching `src-address` and then `dst-address`; remove every collected id using `/ip/firewall/mangle/remove`. | IP → whether any rule existed; destructive write. | none | `firewall().mangle()` / `COMPOSED` | Legacy can collect the same id twice. Generic facade must not speculative-deduplicate, but ISPSup may explicitly deduplicate ids for this known operation. Tests: src-only, dst-only, both, duplicate id, none, removal failure. | `IMPLEMENTED` |
| `resetDHCPLeaseByClientIp(clientIp)` | Find lease id by address, then remove it with `/ip/dhcp-server/lease/remove`. | IP → whether a lease existed; destructive write. | none | `dhcpServer()` find + remove / `COMPOSED` | Legacy sends remove with empty id when no lease exists and keeps the last id on multiple matches. New composition must do neither. Tests: zero/one/multiple, successful removal, removal failure. | `INVENTORIED` |
| `isFireWallActivatedOnTheNas()` | Find Mangle rule with comment `disable access system temporarily`; old business semantics return true when that rule has `disabled=true`. | No input → ISPSup access-system boolean; read-only. | none | `firewall().mangle()` / `COMPOSED` | Fixed comment and inversion are consumer policy. Missing rule currently yields false. Tests: disabled true/false, no row, multiple rows, malformed boolean. | `IMPLEMENTED` |
| `setFireWallActivatedOnTheNas(setState)` | Find every Mangle rule with comment `disable access system temporarily`; set each rule's `disabled` property to `setState`. | Boolean → legacy success flag; write side effect; old code opens another handler per matching rule. | none | `firewall().mangle()` / `COMPOSED` | Task 12 reuses one healthy facade session and does not carry forward the new-connection-before-write workaround. Legacy returns true even for zero matches; final ISPSup semantics must be explicit. Tests: zero/one/many, writes, failure propagation. | `IMPLEMENTED` |
| `mikroTik_fetchSmallFileByNameViaApi(fileName)` | Intended to fetch small file contents; actual code ignores `fileName`, reads a fixed RouterOS file id with `/file/get`, and returns raw protocol text. | Filename parameter is ignored → raw response string; read-only. | none | `files()` + optional legacy `raw()` / `COMPOSED` | Do not reproduce fixed id or 4-KB/raw-string assumptions. On supported ROS7 use binary download and let the consumer decode. If ROS6 `/file/get` is truly required, use an explicit Raw fallback after resolving id by filename and verifying fixtures. | `INVENTORIED` |
| `getCurrentNasIp()` | Return mutable local handler field; no RouterOS command. | No input → host string. | none | none / `OBSOLETE` | Not a RouterOS feature. Session endpoint may exist for diagnostics if independently justified, but parity does not require this method. | `INVENTORIED` |
| `setCurrentNasIp(ip)` | Mutate local host field without rebuilding the underlying connection. | Host string → local mutation only. | none | none / `OBSOLETE` | Unsafe metadata/connection mismatch. A `MikrotikRtrApi` session is bound to its endpoint; connect a new session for another router. | `INVENTORIED` |

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

## Compatibility and fixture backlog discovered by Task 2

1. Verify the legacy address-list query field in `getFireWallStateForClientIp` (`address-list` vs RouterOS `list`) on representative RouterOS versions.
2. Verify range-query semantics used for CAP/interface-prefix matching in modern and legacy registration tables; do not preserve the lexical workaround blindly.
3. Build fixtures for modern-only, legacy-only, both path families present with one relevant, and both simultaneously relevant (`COMPOSITE`). Empty registration data is valid.
4. Record package/version evidence for `wireless`, `wifiwave2`, `wifi-qcom` and `wifi-qcom-ac` in the compatibility catalog.
5. Record explicit schemas for DHCP `agent-circuit-id`/`agent-remote-id`; never use fuzzy property-name discovery.
6. Verify `/snmp/community` `write-access` behavior before exposing a convenience setter.
7. Verify `/tool/fetch` SFTP upload semantics only if current ISPSup still needs that path.
8. Verify whether ROS6 file-content retrieval through `/file/get` remains required. ROS7.13+ binary download should use low-level `downloadFile`.
9. Verify the UI/business meaning of the global access-system boolean around the disabled Mangle rule before final ISPSup migration.

## Task-2 gate

All public methods of the supplied 2026-09-29 `mikrotikHandler` are accounted for above. Facade primitive requirements are therefore defined from real source rather than memory.

Before the final ISPSup migration handoff, re-scan the then-current ISPSup repository and update caller references. Before implementing a compatibility-dependent typed row, resolve its listed fixture/research gate. No row may be marked implemented solely because `raw()` technically exists.
