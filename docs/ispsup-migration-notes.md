# ISPSup Migration Notes for `mikrotik-facade`

## Scope

These notes accompany `docs/functional-parity.md`. They capture migration consequences discovered from the legacy ISPSup `mikrotikHandler` and DTOs.

This is **not** the final Claude Code migration handoff. That handoff is generated only after the facade API and parity matrix are implemented and stable.

## Source status

The newest available handler and DTO artifacts are dated 2026-09-29. The connected `praktimarc/ISPSup` GitHub repository currently contains no source files, so current caller discovery cannot yet be authoritative from GitHub.

Caller evidence comes from an older complete `LoginServiceImpl` snapshot plus a later security-review diff. Re-scan actual current ISPSup source before the final migration. The September handler already obtains MikroTik API credentials through `DeviceConfig`; no credential value from older snapshots is part of the facade contract or these notes.

## Lifecycle migration

Legacy `mikrotikHandler` connects/authenticates in its constructor, normally closes after a public method, retries bootstrap by reusing the same low-level object, sometimes creates extra handlers to work around writes, and can mutate `currentNasIp` independently of the connection.

`MikrotikRtrApi` instead exposes a fully bootstrapped one-session/one-connection object, creates a fresh low-level connection for configured bootstrap retry, never transparently reconnects or replays operations after breakage, and is owned with try-with-resources or an explicit longer-lived owner. A different router means a new facade session.

ISPSup migration must remove the one-handler-per-sub-operation pattern rather than reproduce it.

## Error and result migration

- ordinary 0-or-1 absence → `Optional.empty()`;
- malformed or ambiguous expected-single result → `MikrotikDataException`;
- RouterOS command rejection → `MikrotikCommandException`;
- connection/session failure → `MikrotikConnectionException`;
- timeout → `MikrotikTimeoutException`;
- unsupported typed feature → `MikrotikUnsupportedFeatureException`;
- file-specific local failure → `MikrotikFileException`;
- unknown RouterOS fields remain available through entity `raw()`.

ISPSup converts these outcomes to RPC/UI messages at its boundary. The facade does not recreate `MethodCallResult`.

## DTO migration

| Legacy DTO | Facade direction | Consumer responsibility |
| --- | --- | --- |
| `mtFirewallRule` | typed firewall rule | Interpret ISPSup-specific comments/list membership outside facade. |
| `DhcpIpPool` | typed IP-pool/range data | Capacity/utilization and `<pool>-dhcp` naming stay in ISPSup. |
| `MikrotikRouterQueueType` | typed queue type | UI formatting and PCQ presentation. |
| `mikrotik_registrationTableEntry` | `WifiRegistration` | Join/enrich with DHCP explicitly; no N+1 sessions inside WiFi API. |
| `mikrotik_remoteCapEntry` | typed remote-CAP entity | Presentation only. |
| `mikrotik_IpDhcpServerLease` | `DhcpLease` | Handle optional/volatile circuit and remote-id values. |
| `PingResult` | typed ping result | RPC/UI conversion as needed. |
| `MethodCallResult` | no facade equivalent | Map typed return/exception semantics at service boundary. |
| `Mikrotik_remoteCapConfigEntry` | none from parity | Empty class paired with non-functional stub. |
| `clientMacIpBundle` | none from current parity | Already deprecated in legacy source; do not revive it. |

## Caller evidence from available ISPSup snapshots

These references are evidence, not a guarantee of the current caller set:

| Legacy handler method | Observed consumer(s) |
| --- | --- |
| `util_CheckIfFileExists` | `LoginServiceImpl.getConfigFileContent(...)` |
| `monGetDhcpPoolsOfNasAsArraylist` | `LoginServiceImpl.monFetchDhcpInfoForNas(...)` |
| `nas_GetQueuePcqNasAsArraylist` | `LoginServiceImpl.nas_FetchQueueTypeInfoForNas(...)` |
| `analyze_interfaceWifiRegistrationTableGet` | `LoginServiceImpl.gateway_fetchRegistrationTableOfCapByName(...)` |
| `analyze_interfaceWifiCapsRemoteCapGet` | `gateway_fetchDhcpLeaseInfoByInterface(...)` (result ignored there) and `gateway_fetchRemoteCapByLease(...)` |
| `analyze_ipDhcpServerLeaseInfoGet` | `LoginServiceImpl.gateway_fetchDhcpLeaseInfoByInterface(...)` |
| `pingAClient` | `LoginServiceImpl.pingAnIpFromRouter(...)` |
| `getLeaseForClientmac` | SNMP request/reset workflows in `LoginServiceImpl` |
| `getCMTSIp` | DOCSIS/CMTS status, upstream, downstream and temperature workflows |
| `getFireWallStateForClientIp` | `LoginServiceImpl.findFirewallStateForCPE(...)` |
| `resetFireWallStateByClientIp` | `LoginServiceImpl.deleteFirewallrulesForCPE(...)` |
| `resetDHCPLeaseByClientIp` | `LoginServiceImpl.deleteDHCPLeaseForCPE(...)` |
| `isFireWallActivatedOnTheNas` | `LoginServiceImpl.monGetFirewallStateForNAS(...)` |
| `setFireWallActivatedOnTheNas` | `LoginServiceImpl.monSetFirewallStateOnNas(...)` |

No caller observed in the older complete snapshot is **not** proof of non-use because the handler snapshot is newer.

## Technical debt to delete, not translate

### `executeCommandAgainstMikrotikBug`
Delete the extra-session write workaround. The `.4` low-level baseline owns atomic sentence writes, tag allocation and response routing.

### Manual protocol parsing
Replace string concatenation/splitting of `!re`/`!done` with structured `CommandResult` and `RouterOsRecord` handling.

### Swallowed errors and sentinel values
Do not preserve false/empty-string/default-DTO/partial-data fallbacks for technical errors. Preserve only genuine business absence.

### DHCP `active-client-id` parsing bug
Both lease readers parse `active-client-id` but store the field-name helper string instead of the received value. Add a mapper regression test.

### Empty-result WiFi fallback
Do not infer wrong RouterOS generation from empty modern WiFi data. Capability, source selection and schema remain separate; use `COMPOSITE` when both stacks matter.

### WiFi N+1 sessions
Registration parsing currently opens a fresh handler for every station's DHCP lookup. Keep `WifiRegistration` pure and compose enrichment explicitly with the same session where appropriate.

### Unused `/system/routerboard/print`
Remote-CAP lookup sends this command but never consumes its response. Remove it.

### SNMP method naming and ignored argument
`util_changeSNMPV3UserToWrite(userName)` ignores the argument and manipulates a fixed SNMP community. Expose generic community primitives; consumer chooses policy.

### Hard-coded small-file id
`mikroTik_fetchSmallFileByNameViaApi(fileName)` ignores `fileName` and reads a fixed id. Resolve by name and use binary download where supported; retain legacy Raw access only if demonstrated necessary.

### Mutable NAS address
`setCurrentNasIp` changes only metadata, not the connection. Remove it; sessions remain endpoint-bound.

### Application-specific SFTP destination
Move deployment destination/configuration to ISPSup. The public facade must not know it.

## Semantic corrections discovered from source

### Client firewall state uses Address List

The real September handler implements `getFireWallStateForClientIp` through `/ip/firewall/address-list/print` and the fixed ISPSup list `active-clients`. Earlier design examples used Mangle as an illustrative assumption. The reusable primitive is `firewall().addressList()`; ISPSup keeps the meaning entry exists ⇒ client access/firewall state.

Mangle remains required by separate reset/global-state methods.

### Global access-system state is consumer policy

The comment `disable access system temporarily` identifies a Mangle rule. Old semantics treat a disabled rule as an active access system, so `setFireWallActivatedOnTheNas(true)` writes `disabled=true`. The facade exposes the rule and `setDisabled`; the inversion belongs to ISPSup and needs consumer tests.

## Final migration handoff requirements

After facade stabilization, regenerate the migration handoff from finished facade code and then-current ISPSup source. Per old method include classification, new facade calls, imports, DTO mapping, exception and `Optional` changes, session ownership, compatibility implications, actual callers, tests, and debt to remove.

Destructive actions such as Mangle-rule and DHCP-lease removal must document ordering and uncertain-outcome behavior. No automatic replay is allowed after an ambiguous connection failure.

## Documentation impact

Task 2 changes design/migration documentation only. The binding architecture/plan examples that described `getFirewallStateForClientIP` as a Mangle lookup are corrected to Address List so they no longer contradict the real-source inventory.
