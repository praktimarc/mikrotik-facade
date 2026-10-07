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
| WiFi registrations | Legacy CAPsMAN | `/caps-man/registration-table` | `SINGLE` or condition-selected | legacy registration fields | `LEGACY_OBSERVED` | Empty table is valid; do not infer wrong source from zero clients. | Task 13 fixture required |
| WiFi registrations | New WiFi / CAPsMAN | `/interface/wifi/registration-table` | `SINGLE` or condition-selected | new WiFi registration fields | `OBSERVED` | Empty table is valid; path existence alone is not authority. | Task 13 fixture required |
| WiFi registrations | Both stacks relevant | both registration-table paths | `COMPOSITE` | source-specific registration fields | `OBSERVED` | Preserve source provenance; no generic MAC/ID deduplication. | Task 13 fixture required |
| WiFi stack hints | `wireless`, `wifiwave2`, `wifi-qcom`, `wifi-qcom-ac` | `/system/package` plus read-only path probes | `CONDITIONAL` inputs | package names are hints, not data-source proof | `OBSERVED` | Package absence or unavailable package snapshot reduces prior knowledge; it does not make the session unusable. | Task 13 fixture required |
| WiFi signal normalization | stack-dependent | selected WiFi registration source | source-specific schema mapping | `rx-signal`, `signal` | `OBSERVED` | Map only explicit known field variants; keep all raw fields. | Task 13 fixture required |
| DHCP client ID | DHCP lease data | `/ip/dhcp-server/lease/print` | `SINGLE` + explicit schema mapping | `client-id`, `active-client-id` | `LEGACY_OBSERVED` | Old handler stored the helper key instead of the received `active-client-id`; Task 11 maps the exact received value. Unknown/new fields remain in `raw()`. | Task 11 covered: legacy lease fixture |
| DHCP Circuit-ID / Option 82 | DHCP lease / relay-dependent | `/ip/dhcp-server/lease/print` | `SINGLE` source + schema resolution | `agent-circuit-id` / `active-agent-circuit-id`; `agent-remote-id` / `active-agent-remote-id` | `LEGACY_OBSERVED` + Task 11 fixtures | Active form wins when present. No fuzzy property-name matching and no speculative Option-82 decoding. Unknown representation yields empty typed value while `raw()` remains intact. | Task 11 covered: legacy, active-agent and unknown-agent fixtures |
| Interface counters | hardware / driver dependent | `/interface/...` source selected by module | `SINGLE` or `CONDITIONAL` | optional counters vary by driver/hardware | `OBSERVED` | Missing optional counter is not a data error unless the typed contract marks it required. | Task 14 fixture required |
| File download | RouterOS-version dependent | RouterOS file API / low-level binary download support | `CONDITIONAL` | version-dependent file capabilities | `OBSERVED` | Prefer the verified low-level binary implementation where supported; raw fallback only when older RouterOS support is explicitly proven necessary. | Task 15 fixture required |
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

## Provenance and merging

`COMPOSITE` operations retain the exact source identifier beside every `RouterOsRecord`. The generic compatibility layer deliberately performs no MAC-address, RouterOS `.id`, name, or other record deduplication. A domain module may merge records only when it has a separately documented semantic rule proving that such a merge is correct.

## Diagnostics

Compatibility diagnostics expose only stable feature names, strategy names, and source identifiers. They do not include command arguments, queries, RouterOS record values, credentials, or secret-bearing free-form rationale.

## Maintenance rule

Before implementing a typed module, update the relevant rows with the actual RouterOS version/package/hardware evidence, exact property variants, fallback semantics, and concrete test fixtures. If a new case is not documented or observed reliably, prefer an absent typed value plus preserved raw data over a plausible-looking guess.
