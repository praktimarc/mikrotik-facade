# RouterOS real-router integration profiles

## Purpose

Real RouterOS integration tests are deliberately separate from normal public CI.
They are read-only and credential-gated.

Normal verification:

```bash
mvn clean verify
```

must not require a router or RouterOS credentials.

Explicit real-router verification:

```bash
mvn clean verify -Prouter-it
```

activates Maven Failsafe for classes matching `*RouterIT`. If the profile is enabled
but required environment variables are absent or invalid, the integration test fails
immediately instead of silently skipping all router tests.

## Environment variables

Required:

```text
MIKROTIK_IT_HOST
MIKROTIK_IT_USERNAME
MIKROTIK_IT_PASSWORD
MIKROTIK_IT_PROFILE
```

`MIKROTIK_IT_PASSWORD` must be present but may be an empty string because RouterOS
password policy belongs to the target router.

Optional:

```text
MIKROTIK_IT_TRANSPORT = PLAIN | TLS_VERIFIED | TLS_UNVERIFIED
MIKROTIK_IT_PORT      = 1..65535
```

Default transport is `PLAIN`; the facade then uses the normal RouterOS API default port.
`TLS_UNVERIFIED` is intentionally unsafe and should only be selected in an isolated test
environment where lack of server authentication is explicitly accepted.

No credential value belongs in source files, Maven properties, checked-in resource files,
test names or diagnostic `toString()` output.

## Current Task-19 real-router test

`RouterReadOnlyRouterIT` performs only read operations:

1. connect and authenticate through the public facade builder;
2. bootstrap the immutable RouterOS environment;
3. verify that RouterOS reports a non-blank version;
4. perform a synchronous generic interface read;
5. run several finite interface/address reads concurrently on the same authenticated session;
6. close the facade normally.

It does not create, change or remove RouterOS configuration.

## Profile matrix

The profile name documents what kind of target is being exercised. Task 19 does not
claim that all seven physical/virtual targets are already available.

| Profile | Intended target characteristics | Primary later profile-specific coverage |
| --- | --- | --- |
| `ROS6_LEGACY` | RouterOS 6 using the classic API; no assumption of modern WiFi menus. | ROS6 bootstrap, generic interfaces/firewall/DHCP and any verified ROS6 file compatibility requirements. |
| `ROS7_NO_WIFI` | RouterOS 7 target without authoritative wireless/WiFi manager data. | Empty-valid compatibility behavior and generic non-WiFi modules. |
| `ROS7_LEGACY_WIRELESS` | RouterOS 7 with legacy `wireless` package/menu available. | Legacy registration-table and CAPsMAN source selection. |
| `ROS7_MODERN_WIFI` | RouterOS 7 using modern WiFi packages such as `wifi-qcom` or `wifi-qcom-ac`. | Modern registration schema and source selection. |
| `LEGACY_CAPSMAN` | Legacy CAPsMAN manager enabled and authoritative. | `/caps-man/registration-table` and `/caps-man/remote-cap`. |
| `MODERN_WIFI_CAPSMAN` | New WiFi CAPsMAN manager enabled and authoritative. | `/interface/wifi/registration-table` and `/interface/wifi/capsman/remote-cap`. |
| `PARALLEL_CAPSMAN` | Legacy and modern managers both available/enabled where RouterOS permits it. | Composite source behavior, provenance retention and no generic deduplication. |

Profile-specific assertions may be added only when their actual target fixtures are known.
Do not infer package/menu behavior merely from the profile name inside generic library code.
