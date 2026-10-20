# Public API documentation coverage

This is a **coverage inventory**, not a claim that every public method has
a standalone executable example. It tracks the public facade entry points and
documented API modules at the current Java 17 `0.1.0-SNAPSHOT` baseline.

- [Basic examples](examples.md): connection, DHCP, WiFi, basic firewall,
  interfaces, SNMP, files, ping, raw and guarded DHCP deletion.
- [Advanced cookbook](advanced-cookbook.md): ID-based writes, Address Lists,
  SNMP changes, DHCP data fields, PCQ, IP pools, monitoring, futures and errors.
- [Typed live log streaming](log-streaming.md): RouterLogEntry, `follow`,
  `followOnly`, cancellation and device acceptance tests.
- [Raw-Streaming guide](raw-streaming.md): exact protocol words delivered as
  bounded `Flow.Publisher` records with tag cancellation.
- [Raw-Words Cookbook](raw-protocol-words.md): native binary protocol queries
  without pseudo-CLI parsing.
- [Getting started](getting-started.md): session configuration, TLS, lifecycle,
  asynchronous execution and file transfers.
- [Compatibility catalog](routeros-compatibility.md): RouterOS version,
  package, driver and CAPsMAN source behavior.

## How the inventory works

`docs/api-coverage.tsv` lists the supported public facade API classes with
their declared method names and one primary documentation destination.
A JUnit test compares this list against the actual public methods exposed by
those classes and checks that every linked guide exists. The test also detects
new public `*Api` classes that were not added to the inventory.

This detects **method additions and removals**, but it is not a semantic
review. A method with a new overload of the same name or a changed behavior
can still require a manual documentation update. Public domain-model
accessors remain available in the attached Java-Javadoc and are described
by module in the advanced cookbook; their every getter is **not**
individually enumerated in this inventory.

## Review rule

Before merging an API change:

1. Check newly introduced or modified public methods against the inventory.
2. Add or update a copyable example at the linked guide, particularly for
   writes, errors and non-obvious return types.
3. Verify the example uses the real current method signatures and does not
   imply nonexistent transactions, idempotent writes or supported filters.
4. Update `docs/api-coverage.tsv` and run `mvn -U clean verify`; CI fails
   when the inventoried API method names drift.
5. Keep destructive examples explicitly gated; never include credentials
   or customer configuration in repository documentation.

The table is maintained in a machine-readable form so documentation cannot
silently lag behind an added facade operation. It is deliberately
restricted to **public facade entry-point classes**; domain getters and
protocol commands have separate guides and Java-Javadoc.
