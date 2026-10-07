# RouterOS Compatibility Catalog

This catalog records compatibility knowledge used by `mikrotik-facade`.
It separates three different questions:

1. **Capability** – can a feature exist on this session?
2. **Source resolution** – which RouterOS source or sources are authoritative for this feature?
3. **Schema resolution** – how are fields from the selected source interpreted?

An existing RouterOS path does not by itself make that path authoritative. A successful empty read is still a successful read and does not imply that a feature is unsupported or that a fallback source should be selected.

## Evidence status

| Status | Meaning |
| --- | --- |
| `DOCUMENTED` | Backed by stable RouterOS documentation used by the project. |
| `OBSERVED` | Seen in current RouterOS output, project fixtures, or verified integration behaviour. |
| `LEGACY_OBSERVED` | Present in the old ISPSup handler or historic RouterOS output and still relevant for migration. |
| `HEURISTIC` | Inference used only when unavoidable; must be isolated and explicitly tested. |

The status describes the evidence available to this project. It is not a claim that MikroTik documents every listed detail externally.

## Source-resolution strategies

| Strategy | Contract |
| --- | --- |
| `SINGLE` | One source is authoritative. Other existing paths are irrelevant to this operation. |
| `PREFERRED_FALLBACK` | Use the preferred source when it is successfully available. A successful empty result **does not** trigger fallback. |
| `CONDITIONAL` | An explicit compatibility condition selects exactly one source before the read is interpreted. |
| `COMPOSITE` | Multiple sources are simultaneously relevant. Records retain provenance and are not generically deduplicated. |

## Initial catalog

| Facade function / compatibility topic | RouterOS / package / hardware context | Candidate source(s) | Strategy | Known schema variants | Evidence | Fallback / rule | Fixture status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| WiFi registrations | Legacy CAPsMAN | `/caps-man/registration-table` + `/caps-man/manager` relevance probe | `SINGLE` or `COMPOSITE` | legacy registration fields, notably `rx-signal` | `DOCUMENTED` + `LEGACY_OBSERVED` | Manager path must be available and enabled. Empty registration table is valid and never causes fallback. | Task 13 covered |
| WiFi registrations | New WiFi / CAPsMAN | `/interface/wifi/registration-table` + `/interface/wifi/capsman` relevance probe | `SINGLE` or `COMPOSITE` | new WiFi registration fields, notably `signal` | `DOCUMENTED` + `OBSERVED` | Enabled WiFi CAPsMAN makes the source relevant. A verified local 7.13+ WiFi driver stack may also make it relevant after the registration path succeeds. Empty data remains valid. | Task 13 covered |
| WiFi registrations | Both managers relevant | both registration-table paths | `COMPOSITE` | source-specific registration fields | `DOCUMENTED` + `OBSERVED` | Preserve exact source provenance; no generic MAC/`.id` deduplication. | Task 13 covered |
| WiFi stack hints | `wireless`, `wifiwave2`, `wifi-qcom`, `wifi-qcom-ac` | immutable `/system/package` snapshot plus read-only manager/path probes | `CONDITIONAL` inputs | package/menu naming changed around RouterOS 7.13 | `DOCUMENTED` + `OBSERVED` | Package names narrow candidate knowledge but never prove data-source authority. Pre-7.13 `wifiwave2` does not create an unverified third typed source. Unavailable package information keeps applicable read-only probes possible. | Task 13 covered |
| WiFi signal normalization | stack-dependent | selected WiFi registration source | source-specific schema mapping | legacy `rx-signal`, modern `signal` | `DOCUMENTED` + `LEGACY_OBSERVED` | Known source alias wins; known alternate is accepted; malformed present typed values are data errors; all original fields stay in `raw()`. | Task 13 covered |
| DHCP client ID | DHCP lease data | `/ip/dhcp-server/lease/print` | `SINGLE` + explicit schema mapping | `client-id`, `active-client-id` | `LEGACY_OBSERVED` | Old handler stored the helper key instead of the received `active-client-id`; Task 11 maps the exact received value. Unknown/new fields remain in `raw()`. | Task 11 covered: legacy lease fixture |
| DHCP Circuit-ID / Option 82 | DHCP lease / relay-dependent | `/ip/dhcp-server/lease/print` | `SINGLE` source + schema resolution | `agent-circuit-id` / `active-agent-circuit-id`; `agent-remote-id` / `active-agent-remote-id` | `LEGACY_OBSERVED` + Task 11 fixtures | Active form wins when present. No fuzzy property-name matching and no speculative Option-82 decoding. Unknown representation yields empty typed value while `raw()` remains intact. | Task 11 covered: legacy, active-agent and unknown-agent fixtures |
| Interface inventory | generic RouterOS interfaces | `/interface/print` | `SINGLE` | stable name plus optional id/type/MTU/L2MTU/MAC/status/comment fields | `DOCUMENTED` + `OBSERVED` | `name` is required; hardware-/driver-dependent fields may be absent; unknown properties stay in `raw()`. | Task 14 covered |
| Interface IP addresses | RouterOS IP addressing | `/ip/address/print` | `SINGLE` | address plus optional network/interface/actual-interface/VRF/comment/status fields | `DOCUMENTED` + `LEGACY_OBSERVED` | Equality queries are generic; ISPSup `cmts-internal` meaning is not facade policy. | Task 14 covered |
| Interface traffic monitoring | generic interface traffic monitor | `/interface/monitor-traffic` | `SINGLE` streaming source | RX/TX packets/bits plus optional Fast-Path, drop, error and queue-drop counters | `DOCUMENTED` + `OBSERVED` | Missing optional counters are valid. Present malformed numeric counters are data errors. Cold Flow subscriptions retain all unknown sample fields in `raw()`; queue capacity is 64 and cancellation maps to RouterOS tag cancellation. | Task 14 covered |
| Queue types | queue-kind dependent | `/queue/type/print` | `SINGLE` | PCQ properties exist only for `kind=pcq`; rate/limit/burst quantities may have unit-decorated renderings | `DOCUMENTED` + `LEGACY_OBSERVED` | Preserve rate/limit/burst quantities as exact strings; type stable masks/boolean/time only; unknown fields remain in `raw()`. | Task 15 covered |
| SNMP communities | RouterOS SNMP community configuration | `/snmp/community/print`, `/snmp/community/set` | `SINGLE` | v1/v2c and v3 security fields share the community row | `DOCUMENTED` + `LEGACY_OBSERVED` | Community name is sensitive in diagnostics. Password/key properties have no typed getters. `write-access` is a stable boolean mutation. | Task 15 covered |
| Router-originated ping | finite `/ping` | `/ping` with mandatory positive `count` | `SINGLE` finite command | per-reply records carry cumulative statistics on current RouterOS; completion summary also accepted | `DOCUMENTED` + `LEGACY_OBSERVED` | 100% loss is normal data. Multicast may return more replies than requests and negative packet-loss percentages; preserve RouterOS semantics. Missing usable summary is a data error. | Task 15 covered |
| File download | RouterOS file API with low-level binary-read support | metadata: `/file/print`; binary content: low-level `downloadFile` using `/file/read` | `CONDITIONAL` binary capability | metadata fields vary; binary read is chunked internally by low-level library | `DOCUMENTED` + `OBSERVED` | Metadata always uses CommandEngine. Binary transfer reuses low-level staging/size validation. No guessed version cutoff; only definitive missing-command/chunk-parameter Category-0 evidence becomes `UNSUPPORTED`. Local I/O stays `MikrotikFileException`. | Task 16 covered |
| `/system/package` bootstrap knowledge | systems where package listing is available or explicitly unavailable | `/system/package` | `SINGLE` | available empty list vs unavailable information | `OBSERVED` | `Optional.of(emptyList())` and unavailable package information are distinct states. | Task 5 coverage |

## Firewall compatibility

| Facade function / topic | RouterOS source | Strategy | Typed fields / semantics | Evidence | Rule | Fixture/test status |
| --- | --- | --- | --- | --- | --- | --- |
| Firewall filter rules | `/ip/firewall/filter/print` | `SINGLE` | `.id`, chain, protocol, src/dst address, src/dst port, in/out interface and interface-list, bytes, packets, invalid, dynamic, disabled, comment | `LEGACY_OBSERVED` | Unknown properties remain in `raw()`; empty result is valid. | Task 12 covered |
| Firewall Mangle rules | `/ip/firewall/mangle/print` | `SINGLE` | same proven rule surface as filter | `LEGACY_OBSERVED` | Generic layer does not deduplicate rows/ids. Mangle remove/set use exact RouterOS `.id`. | Task 12 covered |
| Firewall Address List | `/ip/firewall/address-list/print` | `SINGLE` | `.id`, `list`, address, timeout, creation-time, dynamic, disabled, comment | `DOCUMENTED` + observed | Membership query uses `list`, not the legacy handler's `address-list` key. `address-list` is a different firewall-rule matcher/action property. | Task 12 covered |

## Capability probe rules

Active capability probes in v1 are read-only RouterOS `/print` commands. Definitive `SUPPORTED` and `UNSUPPORTED` results may be cached for the authenticated session. `UNKNOWN` is never negatively cached, and technical probe failures leave capability state unchanged so a later operation may retry or use other compatibility knowledge.

A successful empty `/print` result can still prove that the path exists. It does **not** prove that the source is authoritative for a feature; source selection remains the responsibility of the feature-specific resolver.

For Task 13 manager/path discovery, RouterOS command category `0` on the exact read-only menu probe is treated as definitive path absence only while no contradictory `SUPPORTED` knowledge exists. Permission, transport, timeout, malformed-data and other technical failures are propagated and never downgraded to `UNSUPPORTED`.

## Provenance and merging

`COMPOSITE` operations retain the exact source identifier beside every `RouterOsRecord`. The generic compatibility layer deliberately performs no MAC-address, RouterOS `.id`, name, or other record deduplication. A domain module may merge records only when it has a separately documented semantic rule proving that such a merge is correct.

## Diagnostics

Compatibility diagnostics expose only stable feature names, strategy names, and source identifiers. They do not include command arguments, queries, RouterOS record values, credentials, or secret-bearing free-form rationale.

## Maintenance rule

Before implementing a typed module, update the relevant rows with the actual RouterOS version/package/hardware evidence, exact property variants, fallback semantics, and concrete test fixtures. If a new case is not documented or observed reliably, prefer an absent typed value plus preserved raw data over a plausible-looking guess.
