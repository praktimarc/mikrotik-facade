# mikrotik-facade distribution

This package is built automatically by `mvn clean package`, `mvn clean verify`
or `mvn clean install` using Java 17 or newer. The facade itself remains an
unpublished `0.1.0-SNAPSHOT` until a separate release is approved.

Contents:

- `lib/`: Facade JAR plus its Maven-resolved runtime dependencies, including
  `mikrotik-3.0.8-praktimarc.5.jar` and `slf4j-api`. Do not add duplicate
  or older copies of these dependencies.
- `sources/`: Facade and low-level source-attachment JARs for the IDE.
- `javadoc/`: Facade and low-level HTML Javadoc JARs for the IDE.

## Eclipse projects without Maven (including Ant builds)

1. Copy the JARs under `lib/` into the application's dependency folder.
   Remove any older versions of the same libraries.
2. Add only the `lib/` JARs to Eclipse's Java Build Path / Ant runtime
   classpath and, where applicable, to `WEB-INF/lib` of the deployed WAR.
3. Once per JAR entry in Eclipse's Java Build Path, configure **Source
   attachment** from `sources/` and **Javadoc location** as **Javadoc in
   archive** from `javadoc/`; select `/` as the path within each archive.
4. Keep the same filenames and location for successive facade snapshot builds:
   Eclipse's attachments can then be reused without configuring them again.
   The upstream low-level filenames change when its published version changes.
5. Do **not** put Sources/Javadoc JARs into `WEB-INF/lib` or the runtime
   classpath. An SLF4J logging *provider* is application-managed and is not
   included in this API-only distribution.

The archive does not contain a fat/uber-JAR and it does not alter the
application's existing Ant build scripts. Maven-aware IDE consumers can use
`mikrotik-facade` as a Maven dependency with automatic source/Javadoc
attachment when the corresponding classifier artifacts are available.
For raw RouterOS word-level examples see
https://github.com/praktimarc/mikrotik-facade/blob/main/docs/raw-protocol-words.md
