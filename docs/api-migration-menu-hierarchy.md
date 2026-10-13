# API migration: RouterOS-oriented menu hierarchy

**Applies to:** source consumers of the unreleased `mikrotik-facade:0.1.0-SNAPSHOT`.
This is a **breaking Java source and binary API change**. There are no deprecated
compatibility shims; rebuild the facade and the consuming application together.

The RouterOS command paths, typed data mapping, filters, transport, connection
lifecycle and error semantics are unchanged. The refactor changes Java packages,
entry points, methods and several type names.

## Entry point replacement (sync and async)

Replace calls as whole expressions. The `api.async()` tree mirrors the same
menu layout. The placeholder `api` means `MikrotikRtrApi`.

| Old expression | New expression |
| --- | --- |
| `api.interfaces().addresses()` | `api.ip().address().list()` |
| `api.interfaces().addresses(queries)` | `api.ip().address().list(queries)` |
| `api.dhcpServer().pools()` | `api.ip().pool().list()` |
| `api.dhcpServer().findLeaseByMac(mac)` | `api.ip().dhcpServer().lease().findByMac(mac)` |
| `api.dhcpServer().findLeaseByAddress(ip)` | `api.ip().dhcpServer().lease().findByAddress(ip)` |
| `api.dhcpServer().countLeases(queries)` | `api.ip().dhcpServer().lease().count(queries)` |
| `api.dhcpServer().removeLease(id)` | `api.ip().dhcpServer().lease().remove(id)` |
| `api.firewall().filter()` | `api.ip().firewall().filter()` |
| `api.firewall().mangle()` | `api.ip().firewall().mangle()` |
| `api.firewall().addressList()` | `api.ip().firewall().addressList()` |
| `api.snmp().communities()` | `api.snmp().community().list()` |
| `api.snmp().findCommunityByName(name)` | `api.snmp().community().findByName(name)` |
| `api.snmp().setCommunityProperties(id, props)` | `api.snmp().community().setProperties(id, props)` |
| `api.snmp().setWriteAccess(id, enabled)` | `api.snmp().community().setWriteAccess(id, enabled)` |
| `api.system().ping(request)` | `api.ping(request)` |
| `api.files()` | `api.file()` |

Every old expression using `api.async()` follows the corresponding mapping;
for example:

```java
// Old: api.async().interfaces().addresses();
api.async().ip().address().list();

// Old: api.async().dhcpServer().findLeaseByMac(mac);
api.async().ip().dhcpServer().lease().findByMac(mac);

// Old: api.async().system().ping(request);
api.async().ping(request);
```

## Class and import replacements

| Old import/type | New import/type |
| --- | --- |
| `facade.interfaces.InterfaceAddress` | `facade.ip.address.IpAddressV4` |
| `facade.interfaces.internal.InterfaceAddressMapper` | `facade.ip.address.internal.IpAddressV4Mapper` |
| `facade.dhcp.DhcpLease` | `facade.ip.dhcpserver.lease.DhcpLease` |
| `facade.dhcp.DhcpPool` | `facade.ip.pool.IpPool` |
| `facade.dhcp.DhcpServerApi` | `facade.ip.dhcpserver.lease.DhcpLeaseApi` (original lease operations) |
| `facade.dhcp.AsyncDhcpServerApi` | `facade.ip.dhcpserver.lease.AsyncDhcpLeaseApi` |
| `facade.firewall.*` | `facade.ip.firewall.*` (recursive package move) |
| `facade.files.*` | `facade.file.*` (recursive package move) |
| `facade.files.FilesApi` | `facade.file.FileApi` |
| `facade.files.AsyncFilesApi` | `facade.file.AsyncFileApi` |
| `facade.snmp.SnmpCommunity` | `facade.snmp.community.SnmpCommunity` |
| `facade.snmp.internal.SnmpCommunityMapper` | `facade.snmp.community.internal.SnmpCommunityMapper` |
| `facade.system.*` | `facade.ping.*` (recursive package move) |
| `facade.system.SystemApi` | `facade.ping.PingApi` (not returned by session root) |
| `facade.system.AsyncSystemApi` | `facade.ping.AsyncPingApi` |

Here `facade` abbreviates the **full prefix**
`io.github.praktimarc.mikrotik.facade`, not an actual package name.

The following types are intentional **new menu containers**, not direct 1:1
replacements for the old leaf classes:

- `facade.ip.IpApi` and `facade.ip.AsyncIpApi`
- `facade.ip.address.IpAddressApi` and `AsyncIpAddressApi`
- `facade.ip.pool.IpPoolApi` and `AsyncIpPoolApi`
- `facade.ip.dhcpserver.DhcpServerApi` and `AsyncDhcpServerApi`, both exposing `lease()`
- `facade.snmp.SnmpApi` and `AsyncSnmpApi`, both exposing `community()`

Do not globally replace every unqualified `DhcpServerApi` with
`DhcpLeaseApi` without inspecting imports: the **new** `DhcpServerApi`
is the menu container, while `DhcpLeaseApi` executes lease operations.

## Minimal use after migration

```java
import io.github.praktimarc.mikrotik.facade.MikrotikRtrApi;
import io.github.praktimarc.mikrotik.facade.ip.address.IpAddressV4;
import io.github.praktimarc.mikrotik.facade.ip.pool.IpPool;
import io.github.praktimarc.mikrotik.facade.ping.PingRequest;

import java.util.List;

// api is an authenticated, caller-owned MikrotikRtrApi session.
List<IpAddressV4> ipv4 = api.ip().address().list();
List<IpPool> pools = api.ip().pool().list();
api.ip().dhcpServer().lease().findByAddress("192.0.2.10");
api.ip().firewall().filter().findByComment("example-rule");
api.snmp().community().list();
api.file().findByName("flash/example.txt");
api.ping(new PingRequest("192.0.2.1", 3));
```

`InterfaceInfo`, `InterfaceMonitorEntry`, `QueueType`, the WiFi
compatibility API, `RawApi`, `RouterOsProperties`, `RouterOsQuery`
and `RouterOsRecord` retain their public names.

## Consumer migration checks

1. Rebuild/install the snapshot JAR and refresh Maven dependencies or the IDE
   classpath. The coordinate stays `0.1.0-SNAPSHOT` but compiled bytecode is
   not compatible with older copies.
2. Replace fully qualified imports, **then** chained API calls. Check all
   occurrences in Java source, tests and integration configuration.
3. Check source code that explicitly declares `DhcpServerApi`,
   `AsyncDhcpServerApi`, `SystemApi`, `FilesApi`, `InterfaceAddress`
   or `DhcpPool` variables.
4. Recompile rather than relying only on text substitution. No change to
   credentials, RouterOS protocol version or router configuration is required.

There is no supported `api.ipv6()` module yet. The current
`IpAddressV4` is a typed `/ip/address` row (an IPv4 CIDR assignment,
not a standalone IP string). A future `/ipv6/address` API will be separate.
