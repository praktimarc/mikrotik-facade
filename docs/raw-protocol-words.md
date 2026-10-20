# Raw RouterOS API words — Java cookbook

Use `api.raw().words(...)` when you need **exact classic RouterOS binary API
words**, especially to diagnose filtering differences between RouterOS menus
and versions. This interface bypasses the low-level pseudo-CLI string parser.
It does **not** use RouterOS REST or CLI `where` syntax.

**Requirements:** Java 17, the current locally built `mikrotik-facade`
(`0.1.0-SNAPSHOT`), and `io.github.praktimarc:mikrotik:3.0.8-praktimarc.5`.
Both JARs must be available at runtime; the facade JAR does not embed its
dependencies. The `MikrotikRtrApi api` variable in the snippets below is
an already connected, authenticated session created as shown in
[getting started](getting-started.md). Use a read-only RouterOS account for
diagnostics. All addresses and MAC addresses below are examples.

**Contents:** [API choice](#1-which-api-to-use) ·
[protocol words](#2-api-word-syntax) ·
[read operations](#3-read-only-examples) ·
[boolean stack](#4-combine-queries-and-understand-the-stack) ·
[CAPsMAN diagnosis](#5-capsman-query-diagnostics) ·
[results and errors](#6-completion-metadata-and-failure-handling) ·
[asynchronous calls](#7-asynchronous-words-and-cancellation) ·
[limits and safety](#8-limitations-and-safety) ·
[sources](#9-protocol-references).

## 1. Which API to use?

| Purpose | Java API | Behavior |
| --- | --- | --- |
| Normal application operations | `api.ip()`, `api.wifi()`, etc. | Typed methods, compatibility handling; some queries are verified client-side |
| Structured low-level commands | `api.raw().command("/interface/print").query("type", "ether").execute()` | Builder serializes through the low-level pseudo-CLI parser |
| Exact RouterOS words | `api.raw().words("/interface/print", "?type=ether")` | Bypasses string parser and sends each supplied word unchanged |
| Async exact words | `api.async().raw().words(...)` | Returns `CompletableFuture<RawCommandResult>` |
| Local filtering | `result.records(ClientSideFilter.regex(...))` | Filters returned records **after** RouterOS responds; not a server-side regex |

The raw-words API intentionally **does not** perform hidden client-side
post-filtering. For actual application behavior, prefer typed methods where
available; use exact words to investigate RouterOS and protocol behavior.

## 2. API word syntax

Each Java string after the command path is **one complete API word**, not a
CLI token. The low-level transport encodes word lengths and appends the empty
sentence terminator. The session manages `.tag` for correlation.

| Exact word | Meaning |
| --- | --- |
| `/interface/print` | First word: absolute RouterOS command path |
| `?type=ether` | Equality query (server-side) |
| `?=type=ether` | Alternate equality spelling accepted by RouterOS protocol |
| `?name` / `?-name` | Property present / absent |
| `?>comment=` / `?<distance=10` | Greater-than / less-than query |
| `?#|` / `?#&!` | Boolean stack operations (OR / AND then NOT) |
| `=.proplist=.id,name,type` | `print` argument restricting returned properties |
| `=count-only=` | Command argument; counts appear in completion metadata where supported |
| `=comment=hello world` | Argument value with spaces; still a single word |
| `.tag=...` | Reserved for the connection; **never pass it yourself** |

Important distinctions:

- A query starts with `?`; an attribute/command argument starts with `=`.
  `=base-mac=...` is **not** an equality query and can be rejected by a
  `print` command as an unknown argument.
- `=.proplist=...` is an argument, not a `?` query and not a caller-managed
  `.tag` word. The list of returned properties is not a security boundary;
  RouterOS may return additional fields.
- The API does not implement CLI `where` or regex `~` query operators.
- Query word **order matters**, especially when using `?#...` operators.
  Ordinary argument word order must not be relied upon.
- A value that contains spaces or `=` characters is passed literally:
  no CLI quoting or string splitting is necessary.

## 3. Read-only examples

### 3.1 Read interfaces, preserving unknown properties

```java
var result = api.raw().words("/interface/print");

System.out.println("Records: " + result.records().size());
result.records().forEach(row ->
        System.out.println(row.find("name").orElse("(unnamed)")));
```

Avoid printing complete records in production: RouterOS properties can contain
sensitive information. Use the smallest necessary property selection.

### 3.2 Equality query plus property selection

```java
var result = api.raw().words(
        "/interface/print",
        "?type=ether",
        "=.proplist=.id,name,type,disabled");

result.records().forEach(row ->
        System.out.println(row.find("name").orElse("(missing)")));
```

This sends one equality query and one attribute. Do not assume that a menu
actually applied the query just because no error was returned.

### 3.3 Property presence and absence

```java
var withComments = api.raw().words(
        "/ip/route/print", "?comment");

var withoutComments = api.raw().words(
        "/ip/route/print", "?-comment");

System.out.println(withComments.records().size());
System.out.println(withoutComments.records().size());
```

`?comment` means the property **has a value**; it is not a string or
substring match. Presence depends on the properties supplied by that menu.

### 3.4 Comparison query

```java
var result = api.raw().words(
        "/ip/route/print",
        "?>comment=",
        "=.proplist=.id,dst-address,comment");
```

This comparison is an example from MikroTik's API documentation. Do **not**
use `?<...` / `?>...` as substitutes for regex or prefix searching.

### 3.5 Exact full-word List (including spaces and equals signs)

```java
import java.util.List;

var words = List.of(
        "/interface/print",
        "?name=ether1",
        "=.proplist=name,comment");

var result = api.raw().words(words);
```

The list is an immutable snapshot of the complete sentence **excluding**
the automatically managed tag and terminator. An argument such as
`=comment=has spaces and a=b` is transmitted as exactly one word, although
writing to the router should be reserved for a controlled test environment.

## 4. Combine queries and understand the stack

RouterOS evaluates each query word against each item using a boolean stack.
At the end, **any remaining false value means no match**. Independent query
terms therefore behave like AND; use explicit `?#` operations for OR or NOT.
These examples reflect MikroTik's documented protocol semantics. Individual
RouterOS menus can still implement filtering incorrectly.

### 4.1 AND: two independent conditions

```java
var enabledEthernet = api.raw().words(
        "/interface/print",
        "?type=ether",
        "?disabled=false",
        "=.proplist=.id,name,type,disabled");
```

The two remaining conditions must both be true.

### 4.2 OR: Ethernet or VLAN (documented RouterOS example)

```java
var etherOrVlan = api.raw().words(
        "/interface/print",
        "?type=ether",
        "?type=vlan",
        "?#|",
        "=.proplist=name,type");
```

The `?#|` word pops the two conditions and pushes their logical OR.
**Do not omit it**: without it, the two equality tests are ANDed.

### 4.3 NOT of AND: documented RouterOS pattern

```java
var routes = api.raw().words(
        "/ip/route/print",
        "?>distance=1",
        "?gateway=172.16.1.1",
        "?#&!",
        "=.proplist=.id,dst-address,distance,gateway");
```

`?#&!` combines the two tests with AND and then negates the result.
This mirrors the API manual's sample expression, **not** a recommended
production policy or a regex mechanism. Comparison semantics are RouterOS
semantics, not Java's numeric or string comparison rules.

### 4.4 Raw wire words versus the structured builder

```java
var structured = api.raw()
        .command("/interface/print")
        .query("type", "ether")
        .property("name")
        .execute();

var exactWords = api.raw().words(
        "/interface/print",
        "?type=ether",
        "=.proplist=name");
```

Both return `RawCommandResult` but use different serialization paths.
For a bug report, show the **actual words** sent and the returned data rather
than assuming the two paths are equivalent for every RouterOS version.

## 5. CAPsMAN query diagnostics

Modern WiFi CAPsMAN exposes the documented
`/interface/wifi/capsman/remote-cap` menu; classic CAPsMAN uses
`/caps-man/remote-cap`. The menus and available properties differ; do not
assume that `base-mac` exists on the classic menu. Check package/driver
availability and unfiltered response fields first. These examples are
**protocol diagnostics**, not proof that a given firmware correctly applies
filters.

### 5.1 Compare four variants on modern WiFi CAPsMAN

```java
String menu = "/interface/wifi/capsman/remote-cap/print";
String mac = "D0:EA:11:8A:2F:9B"; // synthetic example

var all = api.raw().words(menu);
var normal = api.raw().words(menu, "?base-mac=" + mac);
var alternate = api.raw().words(menu, "?=base-mac=" + mac);
var present = api.raw().words(menu, "?base-mac");

System.out.printf("all=%d normal=%d alternate=%d present=%d%n",
        all.records().size(),
        normal.records().size(),
        alternate.records().size(),
        present.records().size());
```

The alternate equality spelling is allowed by the RouterOS protocol. The
presence query means only "has base-mac", not "equals the supplied MAC".
For a useful comparison also inspect the individual returned `base-mac`
values locally, without posting full MAC addresses or identity fields publicly:

```java
long localMatches = all.records().stream()
        .filter(row -> row.find("base-mac")
                .map(value -> value.equalsIgnoreCase(mac))
                .orElse(false))
        .count();

System.out.println("Unfiltered records matching locally: " + localMatches);
System.out.println("Server equality returned: " + normal.records().size());
```

Compare the exact returned identities or record IDs privately, not only the
counts. Matching counts can conceal incorrect result sets.

### 5.2 Compare results without silently altering raw data

```java
var filtered = api.raw().words(menu, "?base-mac=" + mac);
long incorrectRows = filtered.records().stream()
        .filter(row -> row.find("base-mac")
                .map(value -> !value.equalsIgnoreCase(mac))
                .orElse(true))
        .count();

System.out.println("Server-returned rows failing local check: " + incorrectRows);
```

This diagnostic evaluates the response but **does not replace or post-filter**
`filtered.records()`. If the server wrongly *omits* records, local filtering
cannot recover them.

### 5.3 Reproduce safely across RouterOS versions

1. Record RouterOS version, installed wireless/WiFi packages, device role,
   selected CAPsMAN menu and a sanitized property list.
2. Run an **unfiltered** read first. Check that the target property actually
   occurs in the reply before relying on it in a query.
3. Execute the standard equality, alternate equality and presence variants.
   Inspect record IDs and values, not just counts.
4. Compare against the locally checked unfiltered set, using an authorized
   read-only account. Document any `!trap` error separately.
5. Repeat on relevant RouterOS versions or CAPsMAN implementations. Report
   observations as device-specific; test fixtures cannot establish live
   RouterOS server behavior.

For responsible bug reports, include exact sanitized API words, actual vs
expected results, software versions and repeatability. Avoid publishing
router credentials, full configuration dumps, private MAC/serial data or
sensitive debug logs.

## 6. Completion metadata and failure handling

### 6.1 `!re` records and `!done` completion are separate

```java
import io.github.praktimarc.mikrotik.facade.raw.RawCommandResult;

RawCommandResult result = api.raw().words(
        "/interface/print",
        "=.proplist=name,type");

System.out.println("Data rows: " + result.records().size());
System.out.println("Completion keys: " + result.completion().asMap().keySet());
```

`records()` contains the normal `!re` data sentences. `completion()`
contains the final `!done` metadata and may be empty. Do not infer that
an empty record list is an error. For commands returning completion `ret`
(such as supported `count-only` variants), inspect `completion().find("ret")`,
not the record list:

```java
var counted = api.raw().words("/interface/print", "=count-only=");
System.out.println(counted.completion().find("ret").orElse("(not supplied)"));
```

Treat counts as **server-reported** values and verify them if a menu is known
to mishandle query filters.

### 6.2 Catch command errors and timeouts separately

```java
import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikTimeoutException;

try {
    var result = api.raw().words("/interface/print", "?type=ether");
    System.out.println("Rows: " + result.records().size());
} catch (MikrotikTimeoutException e) {
    System.err.println("Facade command timeout");
} catch (MikrotikCommandException e) {
    System.err.println("RouterOS command rejected"
            + e.category().stream().mapToObj(c -> " (category " + c + ")")
                    .findFirst().orElse(""));
} catch (MikrotikFacadeException e) {
    System.err.println("Facade/connection failure: "
            + e.getClass().getSimpleName());
}
```

`!trap` responses are translated to the facade's exception hierarchy; this
is **not** a byte-exact inbound protocol sniffer. Error messages can contain
environment details; avoid dumping raw exception causes or secrets in shared
reports.

## 7. Asynchronous words and cancellation

`api.async().raw().words(...)` uses the same authenticated TCP/TLS session,
correlation tags and timeout handling, but returns a
`CompletableFuture<RawCommandResult>` instead of blocking for the response.

```java
import io.github.praktimarc.mikrotik.facade.raw.RawCommandResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

CompletableFuture<RawCommandResult> request =
        api.async().raw().words(
                "/interface/print",
                "?type=ether",
                "=.proplist=name,type");

try {
    RawCommandResult result = request.get(30, TimeUnit.SECONDS);
    System.out.println("Rows: " + result.records().size());
} catch (TimeoutException localWaitExpired) {
    request.cancel(true); // best effort; not a router rollback
    System.err.println("Local wait timed out");
} catch (ExecutionException failed) {
    // A facade exception, including a command timeout, may be the cause.
    System.err.println("Asynchronous request failed: "
            + failed.getCause().getClass().getSimpleName());
}
```

The `get(30, ...)` timeout here is a **local Java wait timeout**, distinct
from the facade's configured `commandTimeout(Duration)`. `cancel(true)`
makes a best-effort attempt to cancel the tagged command. Never assume it
undoes a router operation. Keep the API session open until the request is
finished or cancelled; avoid closing a try-with-resources scope immediately
after submitting async work. Do not rely on the thread executing
`CompletableFuture` continuations unless explicitly controlled.

## 8. Limitations and safety

- `words(String path, String... words)` and `words(List<String>)` validate that
  the first word is an absolute command path. Empty words, NUL characters
  and caller-provided `.tag=...` are rejected; the facade adds the tag and
  sentence terminator itself.
- Words are transported as **UTF-8 text**, not arbitrary binary data. For
  binary downloads, use the supported file API.
- The raw API can send **mutating** commands. This cookbook deliberately uses
  read-only `print` operations; use a least-privilege account.
- No CLI lexer, quoting, regex, hidden client-side equality verification or
  automatic router-version-specific query correction is applied.
- The returned data is modeled as records and completion metadata; raw inbound
  `!trap` bytes are not exposed verbatim.
- `.proplist` reduces expected payload size but is neither guaranteed
  to suppress extra returned properties nor a substitute for access controls.
- Real-router behavior depends on supported menus, firmware, and packages.
  Unit and wire-loopback tests verify transport and facade behavior, not every
  RouterOS implementation.

## 9. Protocol references

- [MikroTik RouterOS API — protocol words, tags, print and boolean query stack](https://manual.mikrotik.com/docs/developer-guides/api/)
- [MikroTik WiFi — modern CAPsMAN remote-cap menu and base-mac property](https://help.mikrotik.com/docs/spaces/ROS/pages/224559120/WiFi)
- [MikroTik classic CAPsMAN — remote-cap menu](https://help.mikrotik.com/docs/spaces/ROS/pages/1409149/AP%2BController%2BCAPsMAN)
- [Facade query/filter semantics](query-filtering.md)
- [Facade getting started](getting-started.md)
- [Facade Java API examples](examples.md)

The syntax and stack explanations above follow the official RouterOS manual.
Actual success, failure, and query-filter behavior on an individual router
must be observed and recorded separately.
