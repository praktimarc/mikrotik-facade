# Query and client-side filtering

## RouterOS API limitation

The classic RouterOS API query protocol does not provide a regular-expression query
operator. Do not emulate regex or prefix matching with lexical `>` / `<` comparisons;
those operators keep their real RouterOS ordering semantics and are not a reliable
replacement for pattern matching.

The facade therefore separates two concepts explicitly:

```text
RouterOsQuery     = server-side RouterOS query expression
ClientSideFilter  = local filtering after RouterOS returned records
```

## Server-side advanced queries

The pinned public low-level parser supports equality, inequality, less-than,
greater-than and boolean composition. The facade exposes these without collapsing
multiple terms using the same property name.

Example:

```java
RouterOsQuery query =
        RouterOsQuery.eq("interface", "cap-a")
                .or(RouterOsQuery.eq("interface", "cap-b"))
                .and(RouterOsQuery.gt("signal", "-80"));

RawCommandResult result =
        mtApi.raw()
             .command("/interface/wifi/registration-table/print")
             .query(query)
             .execute();
```

This becomes a RouterOS query stack equivalent to:

```text
?interface=cap-a
?interface=cap-b
?#|
?>signal=-80
?#&
```

Supported v1 server-side operations:

```text
eq
notEq
lt
gt
and
or
not
```

Existing `.query(name, value)` remains supported and keeps its old replacement
semantics for simple equality queries. An advanced `RouterOsQuery` is ANDed with
those simple equality terms.

## Deliberate exists/notExists boundary

RouterOS itself has presence-query words such as `?name` and `?-name`, but the pinned
`mikrotik-java 3.0.8-praktimarc.4` public string parser does not provide a safe public
syntax that can emit them. The facade therefore does not expose fake
`exists()/notExists()` methods.

A future implementation requires a new public low-level API that can express raw query
words directly, followed by a deliberate low-level dependency upgrade and contract test.

## Client-side regex

Use `ClientSideFilter.regex(...)` when a real regular expression is required:

```java
ClientSideFilter capInterfaces =
        ClientSideFilter.regex("interface", "^cap-[0-9]+-");

List<WifiRegistration> matches =
        mtApi.wifi().registrationTable(capInterfaces);
```

The asynchronous mirror is:

```java
CompletableFuture<List<WifiRegistration>> matches =
        mtApi.async()
             .wifi()
             .registrationTable(capInterfaces);
```

Regex matching uses Java `Pattern` and `Matcher.find()` semantics. Add `^` and `$`
when the complete property must match.

The entire registration table still has to be transferred from the selected RouterOS
source before regex filtering occurs. Prefer exact server-side RouterOS queries first
when they can safely reduce the result set.

For raw results:

```java
List<RouterOsRecord> matches =
        mtApi.raw()
             .command("/interface/print")
             .property("name")
             .property("comment")
             .execute()
             .records(ClientSideFilter.regex("comment", "(?i)uplink-[0-9]+"));
```

## Long values

Client-side filtering operates on complete `RouterOsRecord` strings. Task 19a extends
the low-level contract suite with a text property larger than 60 kB and verifies exact
round-trip length/content, specifically guarding against the historical concern that
large API words might be truncated before regex evaluation.

## Diagnostics and secrets

`RouterOsQuery.toString()`, `ClientSideFilter.toString()` and facade diagnostics do
not contain query values or regex text. Advanced-query values participate in the same
RouterOS error-message redaction as legacy equality-query values.
