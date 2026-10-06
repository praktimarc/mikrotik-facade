# mikrotik-facade

High-level Java facade for the classic MikroTik RouterOS API.

## Status

The project is in initial implementation. The architecture and implementation plan are tracked under `docs/superpowers/`.

## Baseline

- Java 17
- Maven
- Low-level client: `io.github.praktimarc:mikrotik:3.0.8-praktimarc.4`
- SLF4J API for logging
- JUnit 5 for tests

## Scope

The facade provides a typed, CLI-oriented API with raw access, shared synchronous/asynchronous execution, Flow-based streaming, RouterOS capability/source/schema compatibility handling, and no dependency on ISPSup/GWT DTOs.

RouterOS REST is not part of v1.

## Dependency note

The current low-level baseline `3.0.8-praktimarc.4` is distributed through GitHub Releases and is not yet available from a remote Maven repository. A clean public Maven/CI build therefore requires an approved Maven distribution source for that artifact. The project does not use `systemPath` or a checked-in dependency JAR as a workaround.
