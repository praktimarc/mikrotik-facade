# mikrotik-facade – Architecture and Design Specification v1

## 1. Ziel

`praktimarc/mikrotik-facade` wird eine öffentliche Java-Facade für die klassische MikroTik RouterOS API.

Sie baut auf folgendem Low-Level-Client auf:

```text
io.github.praktimarc:mikrotik:3.0.8-praktimarc.4
```

Facade-Baseline:

```text
Java 17
```

Low-Level-Baseline:

```text
mikrotik-java 3.0.8-praktimarc.4
Java 11
```

Die Facade soll die beabsichtigte Funktionalität des alten ISPSup-`mikrotikHandler` vollständig abbilden, ohne dessen ISPSup-/GWT-Abhängigkeiten und ohne bekannte Fehler oder Technical Debt künstlich zu reproduzieren.

Source-Kompatibilität zum alten Handler ist nicht erforderlich.

RouterOS 7 ist das primäre Ziel. RouterOS 6 wird selektiv dort unterstützt, wo fachlich sinnvoll und verifizierbar.

REST gehört ausdrücklich nicht zum Scope.

## 2. Öffentliche Grundstruktur

Zentraler Einstiegspunkt:

```java
MikrotikRtrApi
```

Eine Instanz repräsentiert exakt:

```text
eine authentifizierte RouterOS-Session
+
eine zugrundeliegende ApiConnection
```

`MikrotikRtrApi` implementiert `AutoCloseable`.

Beispiel:

```java
try (MikrotikRtrApi mtApi = MikrotikRtrApi.builder()
        .host(host)
        .credentials(username, password)
        .connect()) {

    ...
}
```

Die vollständige Konfiguration erfolgt über den Builder. Convenience-`connect(...)`-Methoden dürfen angeboten werden, delegieren aber vollständig an denselben Builder.

## 3. Fachliche API-Struktur

Die öffentliche API orientiert sich an RouterOS, bildet den CLI-Baum aber nicht blind 1:1 nach.

Vorgesehene Hauptmodule:

```text
mtApi.dhcpServer()
mtApi.firewall()
mtApi.wifi()
mtApi.queue()
mtApi.files()
mtApi.snmp()
mtApi.system()
mtApi.interfaces()
mtApi.raw()
```

Größere Bereiche können Unterbereiche erhalten:

```text
mtApi.firewall().filter()
mtApi.firewall().addressList()

mtApi.queue().type()

mtApi.wifi().registrationTable()
```

Fachlich verständliche Convenience-Methoden werden gegenüber tiefen CLI-Ketten bevorzugt:

```java
mtApi.dhcpServer().findLeaseByMac(mac);
```

statt einer künstlichen Abbildung wie:

```java
mtApi.ip().dhcpServer().lease().print();
```

## 4. Sync, Async und Streaming

Sync und Async gehören von Anfang an zu v1.

Synchron:

```java
Optional<DhcpLease> lease =
        mtApi.dhcpServer().findLeaseByMac(mac);
```

Asynchron über einen gespiegelten API-Baum:

```java
CompletableFuture<Optional<DhcpLease>> future =
        mtApi.async()
             .dhcpServer()
             .findLeaseByMac(mac);
```

Es werden keine `...Async()`-Methodensuffixe verwendet.

Langlaufende Monitoring-Operationen verwenden:

```java
Flow.Publisher<T>
```

Beispiel:

```java
Flow.Publisher<InterfaceMonitorEntry> stream =
        mtApi.interfaces().monitor(...);
```

Sync, Async und Streaming teilen dieselbe interne RouterOS- und Operationslogik.

## 5. Zentrale Architektur

Die Architektur lautet:

```text
Public CLI-like modules
        │
        ▼
Typed Operations
        │
        ▼
RouterOsCommand
        │
        ▼
CommandEngine
        │
        ▼
ApiConnection
```

Rückweg:

```text
ApiConnection
        │
        ▼
CommandResult / Stream Records
        │
        ▼
Schema Mapper / Resolver
        │
        ▼
Typed Entity / Public Result
```

Quer dazu:

```text
Session Lifecycle
Capability Registry
Feature Source Resolver
Compatibility Catalog
Exception Mapper
Executors
Diagnostics
```

Die Facade implementiert ausdrücklich nicht erneut:

```text
OutputStream-Serialisierung
RouterOS-Tag-Allokation
Low-Level-Response-Routing
Low-Level-Listener-Registry
Transport-Failure-Fanout
Low-Level-Connection-State-Machine
```

Diese Garantien liefert `mikrotik-java 3.0.8-praktimarc.4`.

## 6. RouterOsCommand

`RouterOsCommand` ist eine immutable interne Repräsentation eines RouterOS-Commands.

Konzeptionell enthält es:

```text
path
arguments
queries
property selection
```

Es enthält ausdrücklich nicht:

```text
RouterOS tag
Future
Subscriber
Timeout task
Executor
Session state
```

Ein RouterOS-Tag wird ausschließlich durch `ApiConnection` erzeugt und bleibt Transportdetail.

## 7. RouterOsRecord

`RouterOsRecord` ist die universelle immutable Raw-Repräsentation eines RouterOS-Datensatzes.

Alle erhaltenen RouterOS-Properties werden unverändert erhalten.

Beispiele:

```text
.id
mac-address
rx-signal
signal
active-agent-circuit-id
unknown-future-property
```

Unbekannte zukünftige RouterOS-Properties dürfen niemals verloren gehen.

Vorgesehene Zugriffe:

```java
record.find(...)
record.require(...)
record.getLong(...)
record.getBoolean(...)
record.getDuration(...)
record.asMap()
```

Die endgültigen Methodennamen werden bei der API-Spezifikation festgelegt.

## 8. Typed Entities + Raw Escape Hatch

Wo fachlich sinnvoll, liefert die Facade typisierte Entities:

```text
DhcpLease
FirewallRule
RouterFile
WifiRegistration
...
```

Jede Entity bewahrt gleichzeitig die vollständigen Rohdaten:

```java
public interface RouterOsEntity {
    RouterOsRecord raw();
}
```

Dadurch bleiben neue oder unbekannte RouterOS-Felder verfügbar, ohne dass für jede RouterOS-Erweiterung sofort eine neue Facade-Version erforderlich ist.

## 9. RouterOsProperties und Writes

Reads sind primär typisiert.

Writes verwenden ein Hybridmodell:

- häufige und stabile Aktionen erhalten Convenience-Methoden;
- flexible RouterOS-Writes erhalten ein generisches Property-Modell.

Beispiel:

```java
mtApi.firewall()
     .filter()
     .setDisabled(id, true);
```

oder:

```java
RouterOsProperties properties =
        RouterOsProperties.builder()
                .set("chain", "forward")
                .set("action", "accept")
                .set("comment", "Example")
                .build();

mtApi.firewall()
     .filter()
     .add(properties);
```

Große Request-DTO-Hierarchien sollen vermieden werden.

## 10. Raw API

`mtApi.raw()` ist der vollständige Escape Hatch für Reads und Writes.

Die zugrundeliegende `ApiConnection` wird trotzdem niemals öffentlich herausgegeben.

Convenience:

```java
List<RouterOsRecord> records =
        mtApi.raw().execute("/ip/route/print");
```

Strukturiert:

```java
RawCommandResult result =
        mtApi.raw()
             .command("/ip/firewall/address-list/add")
             .argument("list", "blocked")
             .argument("address", "192.0.2.10")
             .execute();
```

`RawCommandResult` trennt:

```text
records()
completion()
```

Dadurch bleiben auch terminale `!done`-Properties wie `ret` erhalten.

Die Raw-API verwendet dieselbe Command Engine und dieselben Lifecycle-, Timeout- und Exception-Regeln wie typisierte Module.

Sie umgeht ausschließlich:

```text
Capability-Abstraktion
fachliche Source-Resolution
Schema-Mapping
Typed Entities
```

## 11. CommandResult und !done

Endliche Commands erzeugen intern:

```text
CommandResult
 ├─ List<RouterOsRecord> records
 └─ RouterOsRecord completion
```

`!re`-Daten und terminale `!done`-Properties werden semantisch getrennt gehalten.

Beispiel:

```text
!re address=192.0.2.1
!re address=192.0.2.2
!done
```

ergibt:

```text
records = [ ... ]
completion = {}
```

Ein Add-Command:

```text
!done
=ret=*A
```

ergibt:

```text
records = []
completion = { ret = "*A" }
```

Die `.4`-Low-Level-Baseline liefert sämtliche normalen `!done`-Properties generisch an den Listener.

## 12. Interne Operations

Fachliche Logik wird in kleinen internen Operations gekapselt.

Konzeptionell:

```java
interface RouterOsOperation<T> {
    RouterOsCommand command(...);
    T map(CommandResult result) throws MikrotikFacadeException;
}
```

Operations sind keine öffentliche Execution-API.

Sie beschreiben:

```text
benötigte Capability
Command
fachliche Result-Semantik
```

Sie enthalten nicht:

```text
Threading
Transport
Tags
Executors
Future Completion
Flow Backpressure
```

Sync und Async verwenden dieselbe Operation.

## 13. Command Engine

Die Command Engine verwendet für normale Commands den listenerbasierten Low-Level-Pfad:

```java
ApiConnection.execute(command, ResultListener)
```

Auch synchrone Facade-Aufrufe verwenden intern diesen gemeinsamen Pfad.

Die synchrone Low-Level-`execute(String)`-API wird nicht als zweite Facade-Ausführungsarchitektur verwendet.

Dadurch existieren:

```text
Cancellation
Timeout
Connection loss
Exception mapping
Result collection
```

nur einmal.

## 14. OperationContext

Jede laufende Operation besitzt einen internen `OperationContext`.

Konzeptionelle Zustände:

```text
CREATED
DISPATCHING
ACTIVE
CANCELLING
COMPLETED
FAILED
CANCELLED
```

Der Zustand berücksichtigt insbesondere das Race:

```text
cancel()
```

kann bereits eintreten, bevor `ApiConnection.execute(...)` den RouterOS-Tag zurückgegeben hat.

In diesem Fall wird Cancellation vorgemerkt und `/cancel` unmittelbar nach Tag-Zuweisung best-effort gesendet.

Eine Operation darf logisch exakt einmal terminal werden.

Alle konkurrierenden terminalen Ereignisse werden atomar gegeneinander entschieden.

Beispiele:

```text
!done vs timeout
!trap vs timeout
cancel vs !done
close vs result
connection loss vs !done
```

Nur der erste gültige terminale Übergang gewinnt.

## 15. Cancellation

Normale listenerbasierte Commands:

```text
CompletableFuture.cancel(...)
        ↓
OperationContext.cancel()
        ↓
best-effort ApiConnection.cancel(tag)
```

`mayInterruptIfRunning` besitzt keine zusätzliche RouterOS-Semantik.

Cancellation ist keine Transaktions-Rollback-Garantie. Ein Write kann bereits wirksam geworden sein, bevor `/cancel` den Router erreicht.

`Flow.Subscription.cancel()` verwendet denselben OperationContext.

Nach Flow-Cancellation werden keine weiteren:

```text
onNext
onComplete
onError
```

an den Subscriber geliefert.

## 16. Timeout

Timeout wird auf Facade-Ebene verwaltet.

Normale endliche Commands besitzen einen konfigurierbaren `commandTimeout`.

Timeout:

```text
Operation → FAILED
best-effort /cancel
MikrotikTimeoutException
```

Timeout ist semantisch nicht dasselbe wie User-Cancellation.

Langlaufende Streaming-Commands besitzen standardmäßig keinen Overall-Command-Timeout.

Optionale spätere Konzepte:

```text
idleTimeout
maxDuration
```

gehören nicht zwingend zu v1.

## 17. Threading

RouterOS-Low-Level-Threads dürfen keinen User-Code ausführen.

Prinzip:

```text
RouterOS reader/processor
→ receive / route / minimal internal state update

callback executor
→ public Future completion
→ Flow callbacks
→ User callbacks
```

Interne Completion und User-Callback-Ausführung sind getrennt.

Dadurch hängt ein synchron wartender Aufruf nicht davon ab, ob ein User-Executor blockiert ist.

Vorgesehene Executor-Rollen:

```text
callbackExecutor
timeoutScheduler
blockingExecutor
```

Facade-eigene Executors werden beim `close()` beendet.

Vom Nutzer gelieferte Executors werden niemals von der Facade geschlossen.

## 18. Flow.Publisher

Streaming-Publisher sind cold.

Eine Subscription startet genau eine RouterOS-Operation.

Mehrere Subscriber erzeugen voneinander unabhängige RouterOS-Operationen.

Pro Subscription existiert eine begrenzte Queue.

`request(n)` steuert ausschließlich die lokale Auslieferung und kann den Router selbst nicht zuverlässig drosseln.

Es gilt:

```text
kein stilles Event-Dropping
```

Queue Overflow führt zu:

```text
MikrotikBackpressureException
+
best-effort remote cancel
```

Event-Reihenfolge pro Subscription ist garantiert.

Callbacks derselben Subscription werden auch bei einem Multi-Thread-Executor serialisiert.

## 19. Session State Machine

Öffentliche Runtime-Zustände:

```text
OPEN
BROKEN
CLOSING
CLOSED
```

Bootstrap-Zustände wie:

```text
CONNECTING
AUTHENTICATING
```

gehören nicht zu einer bereits existierenden `MikrotikRtrApi`.

Transitions:

```text
OPEN → BROKEN
OPEN → CLOSING → CLOSED
BROKEN → CLOSED
```

Es gibt keinen:

```text
BROKEN → OPEN
```

innerhalb derselben Session.

Kein transparenter Reconnect.

## 20. ConnectionListener

Nach erfolgreichem Low-Level-Connect wird sofort ein:

```java
ConnectionListener
```

registriert.

Ein unerwarteter fataler Low-Level-Verbindungsverlust führt zu:

```text
OPEN → BROKEN
```

Der Listener dient insbesondere dazu, Session-Verlust auch ohne aktive Commands zu erkennen.

Absichtliches `close()` erzeugt keinen `connectionLost()`-Callback.

Damit bleibt:

```text
intentional shutdown
```

sauber von:

```text
unexpected connection loss
```

getrennt.

Aktive Commands erhalten ihre Connection-Fehler bereits direkt durch `mikrotik-java`; die Facade implementiert keinen zweiten Connection-Failure-Fanout.

## 21. close()

`MikrotikRtrApi.close()` ist idempotent.

Ablauf:

```text
OPEN/BROKEN → CLOSING
reject new operations
cancel active streams
best-effort cancel normal async operations
complete public operations terminal
close ApiConnection
shutdown facade-owned executors
→ CLOSED
```

Es wird nicht unbegrenzt auf RouterOS-Cancel-Bestätigungen gewartet.

Neue Aufrufe nach kontrolliertem Close ergeben:

```text
IllegalStateException
```

Bei einer `BROKEN`-Session ergeben technische API-Aufrufe dagegen passende `MikrotikConnectionException`-Semantik und nicht fälschlich einen Lifecycle-Programmierfehler.

## 22. Builder und Bootstrap

Eine erfolgreiche `connect()`-Operation liefert ausschließlich eine vollständig initialisierte Session.

Bootstrap:

```text
Builder validation
↓
Transport / SocketFactory
↓
ApiConnection.connect
↓
ConnectionListener registration
↓
login
↓
/system/resource
↓
/system/package
↓
RouterOsEnvironment
↓
OPEN
```

Schlägt ein notwendiger Schritt fehl, wird die teilweise aufgebaute Low-Level-Verbindung geschlossen und keine `MikrotikRtrApi` zurückgegeben.

## 23. Transportmodi

Unterstützt:

```text
Classic API TCP
API-SSL unverified
API-SSL verified
```

Defaults:

```text
plain API  → 8728
API-SSL    → 8729
```

Custom Port ist möglich.

Die API soll sicherheitsrelevante Modi explizit benennen und keine schwer verständlichen Boolean-Kombinationen verwenden.

Beispielsweise konzeptionell:

```java
ApiTransport.plain()
ApiTransport.tlsUnverified()
ApiTransport.tlsVerified()
```

Verified TLS verwendet standardmäßig den JVM-Truststore.

Custom Trust kann über vorhandene Java-TLS-Abstraktionen wie `SSLContext` bzw. `SocketFactory` bereitgestellt werden.

Die Facade implementiert kein eigenes Keystore-/PKI-Framework.

## 24. Credentials

Die Facade ist kein Credential Store.

Sie:

```text
liest keine .properties-Dateien
kennt keinen Vault
persistiert keine Credentials
erneuert keine Credentials
```

Credentials werden vom Consumer für den Bootstrap geliefert.

Sensitive Daten dürfen niemals in:

```text
Logs
Exceptions
toString()
Diagnostics
Environment
```

erscheinen.

## 25. Bootstrap Retry

Default:

```text
0 retries
```

Retries können ausschließlich für den Bootstrap konfiguriert werden.

Retry-fähig sind technische Connect-/Login-Probleme.

Ein eindeutiger Authentication-Reject wird nicht sinnlos mehrfach wiederholt.

Jeder Retry erzeugt eine neue:

```text
ApiConnection
```

Eine fatal beschädigte Low-Level-Connection wird nie wiederverwendet.

Bereits gestartete normale RouterOS-Commands werden niemals automatisch wiederholt.

Insbesondere Writes dürfen nach einem unklaren Connection-Abbruch nicht automatisch replayt werden.

## 26. RouterOsEnvironment

Beim Session-Aufbau entsteht ein immutable:

```java
RouterOsEnvironment
```

aus:

```text
/system/resource
/system/package
```

Es enthält unter anderem:

```text
RouterOS version
architecture
board/hardware context
installed package snapshot
```

Die genauen typisierten Felder werden nur aufgenommen, wenn fachlich sinnvoll.

Rohdaten bleiben erhalten.

Öffentlich wird ein read-only Zugriff angeboten:

```java
mtApi.environment();
```

Das Environment ist ein Session-Snapshot und wird in v1 nicht automatisch aktualisiert.

## 27. /system/resource und /system/package

`/system/resource` ist für eine normale typisierte Session verpflichtend.

`/system/package` ist eine wichtige zusätzliche Informationsquelle, darf aber bei eindeutig nicht unterstützten Systemen optional fehlen.

Ein nicht verfügbarer Package-Snapshot bedeutet:

```text
weniger Vorwissen für Capability Resolution
```

nicht automatisch:

```text
Session kann nicht verwendet werden
```

Technische Fehler wie Connection-Loss während des Bootstrap bleiben dagegen Bootstrap-Fehler.

## 28. Capability-Modell

Capability Detection basiert ausdrücklich nicht nur auf RouterOS-Versionen.

Informationsquellen:

```text
RouterOS version
architecture / board
packages
read-only feature/path probes
known compatibility rules
```

Interne Capability-Zustände:

```text
SUPPORTED
UNSUPPORTED
UNKNOWN
```

Nur definitive:

```text
SUPPORTED
UNSUPPORTED
```

werden gecacht.

Technische Fehler werden nicht als `UNSUPPORTED` gecacht.

Capability Probes sind immer read-only.

## 29. Capability ist nicht gleich Datenquelle

Die Architektur unterscheidet:

```text
Capability
→ kann die fachliche Funktion grundsätzlich verfügbar sein?

Source Resolution
→ wo befinden sich auf diesem Router die relevanten Daten?

Schema Resolution
→ wie sind die dort gefundenen Felder zu interpretieren?
```

Ein existierender RouterOS-Pfad bedeutet nicht automatisch, dass er die autoritative Datenquelle für die aktuelle Konfiguration ist.

Ein leeres Ergebnis bedeutet nicht:

```text
unsupported
```

oder:

```text
wrong source
```

Ein leerer Registration Table kann schlicht bedeuten, dass aktuell keine Clients registriert sind.

## 30. FeatureSourceResolver

Fachbereiche mit mehreren möglichen Datenquellen verwenden interne Source Resolver.

Ein Resolver erzeugt einen `DataSourcePlan`.

Unterstützte Strategietypen:

```text
SINGLE
PREFERRED_FALLBACK
CONDITIONAL
COMPOSITE
```

`first non-empty result wins` ist ausdrücklich keine generische Strategie.

Bei `COMPOSITE` können mehrere fachlich relevante RouterOS-Quellen abgefragt werden.

Die jeweilige fachliche Operation entscheidet, ob und wie Ergebnisse zusammengeführt werden.

Es gibt keine generische MAC-/ID-basierte Deduplication ohne gesicherte fachliche Semantik.

## 31. Provenienz

Records aus kompatibilitätsabhängigen Quellen behalten intern ihre Herkunft.

Konzeptionell:

```text
ResolvedRecord
 ├─ RouterOsRecord
 └─ DataSourceVariant
```

Dadurch können Mapper und Resolver korrekt unterscheiden, aus welchem RouterOS-Stack ein Datensatz stammt.

Die Provenienz muss nicht zwingend Teil jeder öffentlichen Entity sein.

## 32. Compatibility Catalog

Das Projekt führt einen dauerhaft gepflegten RouterOS-Kompatibilitätskatalog.

Vorgesehener Ort:

```text
docs/routeros-compatibility.md
```

Pro fachlicher Funktion werden dokumentiert:

```text
Facade-Funktion
RouterOS-Version bzw. Versionsbereich
Package-/Hardware-Kontext
mögliche Datenquellen
Source-Resolution-Strategie
Property-/Schema-Varianten
Dokumentationsstatus
Fallback
Testfixture
```

Herkunftsstatus:

```text
DOCUMENTED
OBSERVED
LEGACY_OBSERVED
HEURISTIC
```

Heuristiken werden nur verwendet, wenn unvermeidbar und klar markiert.

## 33. Bekannte Compatibility-Fälle

Mindestens folgende Klassen von Varianten müssen im initialen Katalog berücksichtigt werden:

```text
Legacy CAPsMAN registration table
New WiFi / WiFi CAPsMAN registration table
Parallelbetrieb beider CAPsMAN-Varianten

wireless
wifiwave2
wifi-qcom
wifi-qcom-ac

Signal-Feldvarianten wie rx-signal / signal

CAP-/Radio-Daten mit versions-/stackabhängigen Feldern

DHCP Client-ID Varianten

DHCP Circuit-ID / Agent-Circuit-ID Varianten

DHCP Option-82 Änderungen

hardware-/treiberabhängige Interface Counters

RouterOS-versionabhängige File-Funktionen
```

Diese Liste ist kein Ersatz für die Functional-Parity-Analyse.

Vor Implementierung eines fachlichen Moduls wird dessen tatsächlicher Compatibility-Bedarf anhand von:

```text
offizieller MikroTik-Dokumentation
altem mikrotikHandler
beobachteten RouterOS-Ausgaben
Test-Fixtures
```

ermittelt.

## 34. Volatile und undokumentierte Felder

Für Felder mit instabiler oder unzureichend dokumentierter Herkunft gilt:

```text
kein aggressives Guessing
```

Schema Resolver verwenden ausschließlich explizit bekannte Varianten.

Wenn ein typisierter Wert nicht zuverlässig bestimmt werden kann:

```java
Optional.empty()
```

bzw. eine entsprechende fehlende optionale Property.

Die vollständigen Rohdaten bleiben über:

```java
entity.raw()
```

erhalten.

Ein fehlender typisierter Wert ist einem plausibel wirkenden, aber möglicherweise falschen Wert vorzuziehen.

## 35. Exceptions

Öffentliche Hierarchie:

```text
MikrotikFacadeException
 ├─ MikrotikConnectionException
 ├─ MikrotikAuthenticationException
 ├─ MikrotikCommandException
 │    ├─ MikrotikTimeoutException
 │    └─ MikrotikBackpressureException
 ├─ MikrotikUnsupportedFeatureException
 ├─ MikrotikDataException
 └─ MikrotikFileException
```

`MikrotikFacadeException` bleibt ein möglicher generischer Fallback für zukünftige unbekannte Low-Level-Fehler.

Normale fachliche Ergebnisse werden nicht über Exceptions modelliert.

Beispiel:

```text
Lease nicht gefunden
→ Optional.empty()
```

und nicht:

```text
LeaseNotFoundException
```

Programmier-/Lifecycle-Fehler bleiben unchecked:

```text
IllegalArgumentException
IllegalStateException
```

## 36. Low-Level Exception Mapping

Die Facade importiert ausschließlich öffentliche Low-Level-Typen:

```text
MikrotikApiException
ApiConnectionException
ApiCommandException
ApiDataException
ConnectionListener
```

Niemals:

```text
me.legrange.mikrotik.impl.*
```

Mapping:

```text
ApiConnectionException
→ MikrotikConnectionException

ApiCommandException
→ MikrotikCommandException

ApiDataException
→ MikrotikDataException

Facade timeout
→ MikrotikTimeoutException

Flow overflow
→ MikrotikBackpressureException
```

Authentication wird anhand des Bootstrap-Kontexts separat klassifiziert.

Die ursprüngliche Low-Level-Exception bleibt als `cause` erhalten.

## 37. RouterOS Command Error Context

`MikrotikCommandException` soll sicheren strukturierten Kontext bereitstellen:

```text
Facade operation
RouterOS command path
RouterOS error category, falls vorhanden
RouterOS message
```

`ApiCommandException.hasCategory()` muss berücksichtigt werden, da Kategorie `0` ein realer Wert sein kann.

Der RouterOS-Transport-Tag bleibt intern.

## 38. Secret Redaction

Command-Argumente werden nie ungefiltert in Exceptions oder Logs ausgegeben.

Sensitive Keys werden zentral behandelt.

Mindestens betroffen:

```text
password
PSK
private key material
authentication responses
SNMP secrets
weitere Credentials
```

`RouterOsCommand.toString()` darf kein vollständiger unredacted Command Dump sein.

## 39. Files-Modul

Metadatenoperationen verwenden die normale Command Engine.

Beispiele:

```java
mtApi.files().list();
mtApi.files().find(...);
```

Binary Download verwendet dagegen bewusst:

```java
ApiConnection.downloadFile(...)
```

weil die Low-Level-Bibliothek bereits bereitstellt:

```text
binary-safe transfer
/file/read
chunked reading
.part staging
size validation
cleanup
```

Diese Logik wird nicht in der Facade dupliziert.

## 40. FileDownloadResult

Facade-Rückgabe:

```java
FileDownloadResult
```

konzeptionell mit:

```text
remoteFile
localFile
bytesWritten
```

Das Modell bleibt in v1 bewusst klein.

Lokale Filesystem-Fehler werden als:

```text
MikrotikFileException
```

abgebildet.

RouterOS-/Connection-Fehler behalten ihre normalen Exception-Typen.

## 41. Async Binary Download

`ApiConnection.downloadFile()` ist blockierend.

Asynchrone File-Downloads laufen deshalb auf einem separaten begrenzten:

```text
blockingExecutor
```

und niemals auf:

```text
RouterOS I/O thread
callbackExecutor
```

Es werden keine unbegrenzt neuen Threads pro Download erzeugt.

## 42. Binary Download Cancellation

Der Binary-Download ist eine dokumentierte Ausnahme von der normalen Cancellation-Garantie.

Der Low-Level-Download besteht intern aus mehreren Chunk-Commands und exponiert deren Tags nicht.

Deshalb kann:

```java
future.cancel(...)
```

den öffentlichen Future lokal canceln, aber nicht garantieren, den aktuell laufenden Remote-Chunk sofort über `/cancel` zu stoppen.

`MikrotikRtrApi.close()` schließt dagegen die zugrundeliegende Connection und beendet dadurch auch den laufenden Transfer.

Es gibt in v1:

```text
kein Flow<ByteBuffer>
keinen InputStream Download
keinen Binary Upload
```

## 43. Logging

Die Facade verwendet:

```text
SLF4J API
```

ohne eigenes Logging-Backend zu erzwingen.

Semantik:

```text
ERROR – interner Fehler/Invarianzbruch
WARN  – degradierter Betrieb/auffällige Compatibility-Situation
INFO  – grober Session-Lifecycle
DEBUG – Capability-/Source-/Adapterentscheidung
TRACE – detaillierte sichere Diagnoseinformationen
```

Normale RouterOS-Commandfehler müssen nicht automatisch als Library-ERROR geloggt werden.

## 44. Compatibility Diagnostics

DEBUG-Diagnostik soll nachvollziehbar machen:

```text
welches Environment erkannt wurde
welche Quellen Kandidaten waren
welche Source-Strategie gewählt wurde
welcher Adapter verwendet wurde
welche Fallback-Regel griff
```

Dabei werden weder Credentials noch komplette sensitive Routerkonfigurationen ausgegeben.

## 45. Teststrategie

### Pure Unit Tests

Für:

```text
RouterOsRecord
RouterOsProperties
Command building
Entity Mapper
Schema Resolver
FeatureSourceResolver
Capability Cache
Exception Mapping
Validation
State transitions
```

### Compatibility Fixture Tests

Reale anonymisierte RouterOS-Antworten werden als Fixtures gespeichert.

Beispielstruktur:

```text
src/test/resources/routeros/
    ros6/
    ros7/
    capsman/
    wifi/
    dhcp/
    interfaces/
```

Fixtures enthalten soweit erforderlich:

```text
RouterOS version
board
packages
source
raw responses
expected typed result
```

### Command Engine Race Tests

Mindestens:

```text
done vs timeout
done vs cancel
trap vs timeout
connection loss vs done
close vs completion
cancel before tag
cancel after tag
multiple simultaneous commands
```

### Flow Tests

Mindestens:

```text
request(n)
request(0)
ordering
slow subscriber
queue overflow
cancel
terminal event exclusivity
```

### Session Tests

Mindestens:

```text
connect success
authentication reject
bootstrap failure
malformed resource data
package unavailable
idle connection loss
active connection loss
close
double close
operation after close
operation after broken
```

### Redaction Tests

Sensitive Werte dürfen in keinem:

```text
toString
Exception text
Diagnostic rendering
```

auftauchen.

### Low-Level Contract Tests

Die für die Facade kritischen Garantien von:

```text
mikrotik-java 3.0.8-praktimarc.4
```

werden durch einen kleinen eigenen Contract-Test-Satz abgesichert.

### Real Router Integration Tests

Separat und credential-gated.

Sie gehören nicht zu jedem normalen CI-Build.

## 46. Functional-Parity-Matrix

Vor bzw. parallel zur Modulimplementierung muss eine vollständige Matrix des alten ISPSup-`mikrotikHandler` erstellt werden.

Pro alter Funktion:

```text
alte Methode
neue Facade-Methode
RouterOS Command/Source
Input
Output
Side Effects
ROS6/Legacy Verhalten
Package-/Capability-Abhängigkeiten
Schema-Varianten
Error Semantics
altes DTO
neues Modell
Migration Note
Tests
Implementierungsstatus
```

Diese Matrix wird aus dem tatsächlichen alten Sourcecode erstellt.

Nicht dokumentierte oder vermutete Methoden werden nicht erfunden.

Bekannte Altcode-Bugs werden dokumentiert und korrigiert statt als Compatibility-Verhalten nachgebaut.

### Functional Parity ist nicht Method Parity

Eine alte anwendungsspezifische Methode muss nicht als gleichnamige oder gleich spezialisierte Facade-Methode wieder entstehen.

Stattdessen gilt:

```text
alte anwendungsspezifische Methode
        ↓
benötigte RouterOS-Grundfunktion identifizieren
        ↓
allgemeine typisierte Facade-Funktion bereitstellen
        ↓
alte Fachsemantik im Consumer rekonstruieren
```

Beispiel:

```text
ISPSup:
getFirewallStateForClientIP(ip)

benötigt:
Suche in /ip/firewall/address-list anhand einer IP und der fachlichen Liste

Facade:
firewall().addressList().find(...)

ISPSup:
interpretiert "Eintrag in active-clients vorhanden" wieder als seinen Client-Firewall-State
```

Für die Parity-Matrix gilt zusätzlich die Klassifizierung:

```text
DIRECT
→ alte Funktion entspricht direkt einer neuen Facade-Funktion

COMPOSED
→ alte Funktion wird aus einer oder mehreren allgemeineren Facade-Funktionen aufgebaut

RAW_FALLBACK
→ Funktion ist vollständig über raw() möglich, erhält aber zunächst keine eigene Typed Convenience API

OBSOLETE
→ alter Code war Bug/Workaround/Technical Debt und wird bewusst nicht übernommen
```

`RAW_FALLBACK` ist nicht automatisch ausreichend, nur weil technisch alles über `raw()` möglich wäre. Für häufige oder fachlich stabile RouterOS-Bereiche sollen vernünftige typed/generic APIs angeboten werden.

Zentrale Regel:

> Jede beabsichtigte Funktion des alten `mikrotikHandler` muss mit der neuen Facade sinnvoll implementierbar sein. Sie muss aber nicht als dieselbe öffentliche Methode in der Facade existieren.

Nach fertiger Facade und finaler Functional-Parity-Matrix wird separat ein Claude-Code-Handoff für die Migration der alten ISPSup-Methoden erstellt.

## 47. Nicht-Ziele von v1

Nicht Teil von v1:

```text
RouterOS REST API
automatisches Reconnect einer beschädigten Session
automatisches Command Replay
öffentliche Low-Level ApiConnection
öffentliche RouterOS Tags
vollständiger 1:1 CLI Object Tree
gigantische Request DTOs
Binary Upload
Binary Byte Streaming
öffentliche CapabilityRegistry
generisches Guessing undokumentierter Felder
```

## 48. Zentrale Architektur-Invarianten

Die Implementierung muss folgende Invarianten dauerhaft erfüllen:

1. Eine `MikrotikRtrApi` entspricht genau einer authentifizierten Session.
2. Normale Sync- und Async-Commands verwenden dieselbe listenerbasierte Command Engine.
3. Tags und Low-Level-Dispatch bleiben vollständig Eigentum von `mikrotik-java`.
4. User-Code läuft niemals auf dem RouterOS-I/O-/Processor-Thread.
5. Eine Operation wird logisch höchstens einmal terminal.
6. Normale Future-/Flow-Cancellation propagiert best-effort zu RouterOS.
7. Command-Timeouts führen best-effort zu `/cancel`, aber niemals zu automatischem Replay.
8. Ein fataler Connection-Loss macht die Session dauerhaft `BROKEN`.
9. Es gibt keinen transparenten `BROKEN → OPEN`-Reconnect.
10. Unbekannte RouterOS-Properties werden nie verworfen.
11. `!re`-Records und `!done`-Completion-Metadaten bleiben getrennt.
12. Capability, Datenquelle und Schema sind drei getrennte Konzepte.
13. Ein existierender Pfad beweist nicht, dass er die fachlich richtige Quelle ist.
14. Ein leeres Resultset beweist weder Unsupported noch falsche Datenquelle.
15. Version und Packages sind Hinweise, keine alleinige Wahrheit.
16. Undokumentierte volatile Werte werden nicht aggressiv geraten.
17. Sensitive Daten erscheinen niemals ungefiltert in Logs oder Exceptions.
18. Binary Download nutzt die spezialisierte Low-Level-Implementierung statt einer Facade-Neuimplementierung.
19. Ein Consumer muss niemals `me.legrange.mikrotik.impl.*` verwenden.
20. Die Functional-Parity- und RouterOS-Compatibility-Dokumentation sind Bestandteil der Implementierung und nicht nur nachträgliche Dokumentation.

## 49. Bewusst noch nicht festgelegte Implementierungsdetails

Folgende Werte werden erst im Implementation Plan bzw. anhand von Tests bestimmt:

```text
konkrete Default-Threadpool-Größen
Flow buffer default capacity
konkrete Executor-Implementierungen
exakte Package-Namen der internen Klassen
endgültige öffentliche Builder-Methodennamen
endgültige Namen einzelner Raw-/Result-Helfermethoden
vollständige Functional-Parity-Matrix
vollständige RouterOS Compatibility Matrix
```

Diese Punkte ändern die freigegebene Architektur nicht.

## 50. Implementierungsreihenfolge – noch keine Freigabe

Nach Freigabe dieser Spec folgt separat ein detaillierter Implementation Plan.

Eine sinnvolle grobe Reihenfolge wird voraussichtlich sein:

```text
Foundation / project skeleton
↓
raw data models + exceptions
↓
builder / connection / lifecycle
↓
Command Engine + OperationContext
↓
Sync/Async execution
↓
Flow streaming
↓
Capability / source / schema framework
↓
raw API
↓
fachliche Module anhand Parity Matrix
↓
binary files integration
↓
compatibility fixtures
↓
migration documentation
```

Commit und Push sind für dieses Projekt vom User fortlaufend freigegeben und benötigen keine weiteren Einzelgates. PR, Merge, Tag, Release und Deployment bleiben jeweils eigenständige Freigabeschritte.
