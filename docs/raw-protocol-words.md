# Exact RouterOS protocol words

The facade exposes two intentionally different raw APIs:

- `api.raw().command("/ip/address/print").query("interface", "ether1").execute()`
  uses a structured builder and the low-level library's pseudo-command parser.
- `api.raw().words("/ip/address/print", "?interface=ether1")` bypasses that
  parser and forwards the exact caller-supplied **RouterOS API UTF-8 words**.
  Only an internally managed `.tag` word and the required empty sentence
  terminator are added by the low-level transport.

Both APIs use the same authenticated connection, command timeout, callback
engine, RouterOS error mapping and TCP/TLS transport selection. The asynchronous
mirror is `api.async().raw().words(...)`.

## Compare RouterOS CAPsMAN query variations

```java
String command = "/interface/wifi/capsman/remote-cap/print";
String mac = "D0:EA:11:8A:2F:9B";

var all = api.raw().words(command);
var queryStandard = api.raw().words(command, "?base-mac=" + mac);
var queryAlternate = api.raw().words(command, "?=base-mac=" + mac);
var queryPresence = api.raw().words(command, "?base-mac");

System.out.println("Unfiltered: " + all.records().size());
System.out.println("Standard query: " + queryStandard.records().size());
System.out.println("Alternative query: " + queryAlternate.records().size());
System.out.println("Presence query: " + queryPresence.records().size());
```

For a reproducible report, also inspect each returned record. Equal counts
do not alone prove that a query was ignored. The facade deliberately does
**not** apply hidden client-side filtering to these raw results.

You can pass a full immutable list too:

```java
RawCommandResult result = api.raw().words(List.of(
    "/interface/wifi/capsman/remote-cap/print",
    "?base-mac=D0:EA:11:8A:2F:9B"
));
```

Import `io.github.praktimarc.mikrotik.facade.raw.RawCommandResult` and
`java.util.List` when explicitly declaring these types.

An API query word is not a CLI `where` clause: supply the protocol spelling
directly, including its leading `?`. For example, `=base-mac=...` is a
command parameter, **not** a query, and the target `print` menu may reject
it with `unknown parameter base-mac`.

The method is intended for expert diagnostics, not untrusted end-user text.
It can execute destructive RouterOS operations. Words must be nonempty, the
first must be an absolute command path, no word can contain NUL, and a caller
cannot supply a `.tag=` word. The transport uses UTF-8 text words, not
arbitrary binary payloads. It does not capture raw inbound `!trap` sentences
byte-for-byte; RouterOS traps are still mapped into the facade exception
hierarchy. The exact outbound request words are those supplied by the caller
plus the internal tag and terminator.

## Development dependency

This feature requires `io.github.praktimarc:mikrotik:3.0.8-praktimarc.5-SNAPSHOT`.
It is **not yet published on Maven Central**. Build and install the
`praktimarc/mikrotik-java` source from commit
`8bf0c94a5b394e4f460b8beee83d8daaf4a74c7e` first:

```sh
mvn -B clean install
```

Then build/install this facade snapshot. The CI performs the low-level
source installation from the pinned GitHub commit before building the facade.
The existing `.4` JAR does **not** support `executeWords` and must not remain
on the consumer runtime classpath.
