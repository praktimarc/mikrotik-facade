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

Finalisierte v1-Builder-Konfiguration:

```java
MikrotikRtrApi.builder()
    .host(String)
    .credentials(String username, String password)
    .transport(ApiTransport)
    .port(int)
    .connectTimeout(Duration)
    .commandTimeout(Duration)
    .bootstrapRetries(int)
    .callbackExecutor(Executor)
```

Defaultwerte:

```text
transport         = plain
connectTimeout    = 60 s
commandTimeout    = 60 s
bootstrapRetries  = 0
callbackExecutor  = facade-owned default when omitted
```

Portwerte werden auf `1..65535` validiert. Timeouts müssen positiv, exakt in ganzen Millisekunden darstellbar und auf den vom Low-Level-API erwarteten positiven `int`-Millisekundenbereich begrenzt sein. Validierung erzeugt keinerlei Netzwerkzugriff.

Host und Benutzername dürfen nicht leer sein. Ein leerer Passwort-String bleibt zulässig; Passwortpolitik gehört zu RouterOS und wird von der Facade nicht erfunden. Builder- und Konfigurations-Diagnostics dürfen den Passwortwert niemals ausgeben.

Die validierte Konfiguration ist ein interner immutable Snapshot für den späteren Bootstrap. Caller-provided Executors bleiben caller-owned.

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

Task 11 finalisiert den ersten vollständigen typisierten Slice:

```java
Optional<DhcpLease> byMac =
        mtApi.dhcpServer().findLeaseByMac(mac);

Optional<DhcpLease> byAddress =
        mtApi.dhcpServer().findLeaseByAddress(address);

CompletableFuture<Optional<DhcpLease>> asyncByMac =
        mtApi.async().dhcpServer().findLeaseByMac(mac);

CompletableFuture<Optional<DhcpLease>> asyncByAddress =
        mtApi.async().dhcpServer().findLeaseByAddress(address);
```

Beide Bäume verwenden dieselbe `CommandEngine`, denselben Session-Lifecycle und dieselbe Mapping-Operation. 0 Treffer sind ein normales leeres `Optional`; mehr als ein Treffer bei diesen expected-single Lookups ist inkonsistente RouterOS-Datenlage und führt zu `MikrotikDataException`.

Task 12 finalisiert den Firewall-Baum als allgemeine RouterOS-Primitiven:

```java
mtApi.firewall().filter()
mtApi.firewall().mangle()
mtApi.firewall().addressList()

mtApi.async().firewall().filter()
mtApi.async().firewall().mangle()
mtApi.async().firewall().addressList()
```

Filter, Mangle und Address List unterstützen generische Equality-Queries über `RouterOsProperties`. Leere Resultsets sind normale leere Listen. Filter- und Mangle-Regeln werden als `FirewallRule` mit belegten stabilen Legacy-Feldern plus vollständigem `raw()` geliefert; Address-List-Einträge verwenden ein eigenes `AddressListEntry`.

Flexible `add(...)`-Operationen verwenden `RouterOsProperties` und geben terminales `ret` als `Optional<String>` zurück. Stabile Mutationen werden nur dort als Convenience angeboten, wo die Parity-Matrix sie rechtfertigt: `setDisabled(id, boolean)` für Filter/Mangle und `remove(id)` für Mangle.

Der alte Workaround, vor einem Mangle-`set` eine neue RouterOS-Verbindung zu öffnen, wird nicht übernommen. Alle Reads und Writes laufen über dieselbe gesunde Session und dieselbe Command Engine.

Bei `/ip/firewall/address-list` heißt die Gruppierungs-Property `list`. Der alte ISPSup-Query mit `address-list` wird nicht reproduziert. Die festen Bedeutungen von `active-clients` und `disable access system temporarily` bleiben Consumer-Policy.

Task 13 finalisiert den WiFi-Registration-Slice:

```java
mtApi.wifi().registrationTable()
mtApi.async().wifi().registrationTable()
```

`WifiRegistration` ist eine immutable `RouterOsEntity` mit vollständigem `raw()` und expliziter Source-Provenienz. Typisiert werden nur belegte stabile Felder. `signal` und `rx-signal` werden source-spezifisch zu einem dBm-Wert normalisiert; unbekannte Felder sowie nicht sinnvoll zu einem Scalar vereinfachbare Rate-/Counter-Darstellungen bleiben verlustfrei erhalten.

Die Source-Resolution trennt weiterhin Capability, Source und Schema. Legacy CAPsMAN wird über `/caps-man/manager/print` bewertet, neues WiFi-CAPsMAN über `/interface/wifi/capsman/print`. Ein lokaler RouterOS-7.13+-WiFi-Stack mit `wifi-qcom`/`wifi-qcom-ac` kann den modernen Registration-Pfad zusätzlich relevant machen, muss `/interface/wifi/registration-table/print` aber erfolgreich verifizieren. Package-Namen sind Hinweise und niemals alleinige Source-Autorität. Sind beide Manager relevant, wird `COMPOSITE` verwendet; Records werden nicht nach MAC oder `.id` dedupliziert. Ein erfolgreich leeres Resultset bleibt ein gültiges Resultset.

`wifiwave2` bleibt als historische Package-/Menüinformation im Compatibility-Katalog. Ohne verifizierte Fixture wird daraus keine zusätzliche `/interface/wifiwave2/...`-Facade-Source konstruiert.

Der alte ISPSup-Handler filterte CAP-Interfaces über einen lexikalischen Range-Query und öffnete für jede Registration eine neue Session zur DHCP-Anreicherung. Beides bleibt Consumer-Komposition und wird nicht in die Facade übernommen.

Task 14 finalisiert den Interface-/Monitoring-Slice:

```java
mtApi.interfaces().list();
mtApi.interfaces().addresses();
mtApi.interfaces().monitor("ether1");

mtApi.async().interfaces().list();
mtApi.async().interfaces().addresses();
```

`InterfaceInfo` modelliert generische `/interface/print`-Rows, `InterfaceAddress` modelliert `/ip/address/print`. Beide unterstützen Equality-Queries über `RouterOsProperties` und behalten unbekannte Felder vollständig in `raw()`. Hardware-/Treiber-Felder wie L2MTU, Max-L2MTU oder einzelne Statuswerte sind optional; nur fachlich zwingende Identifikationswerte werden erzwungen.

`interfaces().monitor(interfaceName)` ist ein cold `Flow.Publisher<InterfaceMonitorEntry>` über `/interface/monitor-traffic`. Eine Subscription startet genau eine laufende RouterOS-Operation. Die Queue-Kapazität beträgt 64 Samples; Overflow verwendet unverändert `MikrotikBackpressureException` und best-effort Remote-Cancel. Monitor-Counter wie RX/TX Pakete, Bits, Fast-Path, Drops, Errors und Queue-Drops sind optional und werden nur bei tatsächlich vorhandenen numerischen Werten typisiert.

Ein sessionweiter `StreamRegistry` registriert die von Interfaces-Monitoring exponierten Flow-Subscriptions. `MikrotikRtrApi.close()` ruft zuerst `streamRegistry.cancelActive()`, danach `commandEngine.cancelActive()`. Die Registry besitzt zusätzlich einen irreversiblen Closing-Marker: versucht ein verzögertes `onSubscribe` nach begonnenem Close noch eine Subscription zu registrieren, wird sie sofort gecancelt und startet keinen neuen RouterOS-Command.

Die ISPSup-Regel `comment=cmts-internal` plus „letztes IPv4-Oktett minus eins“ bleibt Consumer-Policy. Die Facade liefert lediglich die generischen Address-Records.

Task 15 finalisiert Queue, SNMP und System/Ping:

```java
mtApi.queue().type().list();
mtApi.queue().type().find(properties);

mtApi.snmp().communities();
mtApi.snmp().findCommunityByName(name);
mtApi.snmp().setCommunityProperties(id, properties);
mtApi.snmp().setWriteAccess(id, enabled);

mtApi.system().ping(request);
```

Die Async-Varianten spiegeln alle endlichen Operationen. Queue-Type-Properties sind kind-abhängig. `QueueType` verlangt nur den Namen; PCQ-spezifische Werte bleiben optional. Rate-/Limit-/Burst-Quantitäten werden bewusst verlustfrei als Strings exponiert, weil RouterOS sowohl nackte numerische API-Werte als auch unit-dekorierte Darstellungen wie `50KiB` kennt. Formatstabile Masken, `default` und `pcq-burst-time` werden typisiert.

`SnmpApi` modelliert `/snmp/community`, nicht einen fiktiven SNMPv3-User-Baum. Die alte ISPSup-Methode ignorierte ihr User-Argument und änderte die Community `admin`; diese Policy wird nicht in die Facade übernommen. Community-Lookup filtert den vollständigen typisierten Read lokal und legt den Community-Namen nicht als RouterOS-Query in Diagnosekontext. Generische Property-Writes laufen über `/snmp/community/set` mit exakter `.id`; `setWriteAccess` ist die stabile Convenience. Auth-/Encryption-Passwörter besitzen keine typisierten Getter. Die zentrale Redaction behandelt zusätzlich `name` ausschließlich unter `/snmp/community/...` als sensitiv.

`SystemApi.ping(PingRequest)` ist stets endlich: `count` ist obligatorisch und positiv. `PingResult` trennt einzelne Reply-/Statusrecords von der kumulativen Statistik und behält die Summary ebenfalls als `raw()`. Die letzte verwertbare Summary gewinnt, sofern die Completion-Properties keine Summary liefern. Timeout/Unreachable und 100% Paketverlust sind normale Ping-Ergebnisse. Multicast kann mehrere Antworten pro Request und dadurch negative RouterOS-`packet-loss`-Werte erzeugen; deshalb wird weder `received <= sent` noch ein künstlicher Loss-Bereich 0..100 erzwungen.

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

Der öffentliche v1-Einstieg ist gespiegelt:

```java
mtApi.raw()
mtApi.async().raw()
```

Es werden auch im Raw-Bereich keine `...Async()`-Methodensuffixe eingeführt. Beide Bäume teilen dieselbe Session, dieselbe `CommandEngine` und dieselben RouterOS-Operationsobjekte.

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

Finalisierte v1-Struktur:

```java
RouterOsCommand.builder(path)
    .argument(name, value)
    .query(name, value)
    .property(name)
    .build();

command.path()
command.arguments()
command.queries()
command.properties()
command.serialize()
```

Der Command ist ein immutable Snapshot. Argumente und Equality-Queries behalten ihre Einfügereihenfolge. Die Serialisierung verwendet ausschließlich die öffentliche Low-Level-String-API und quotiert Werte so, dass insbesondere `/` und `,` in Werten nicht als Parser-Syntax interpretiert werden. Ein Command-Pfad darf keine eingeschmuggelten Argumente oder Queries enthalten. `toString()` verwendet ausschließlich die zentrale redigierte Diagnostic-Darstellung.

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

Finalisierte v1-Zugriffe:

```java
Optional<String> find(String key)
String require(String key) throws MikrotikDataException

OptionalLong getLong(String key) throws MikrotikDataException
long requireLong(String key) throws MikrotikDataException

Optional<Boolean> getBoolean(String key) throws MikrotikDataException
boolean requireBoolean(String key) throws MikrotikDataException

Optional<Duration> getDuration(String key) throws MikrotikDataException
Duration requireDuration(String key) throws MikrotikDataException

Map<String, String> asMap()
```

Fehlende optionale Properties bleiben leer; fehlende erforderliche oder vorhandene, aber nicht konvertierbare Werte erzeugen `MikrotikDataException`. Konvertierungsfehler dürfen den vollständigen Raw-Wert nicht in die Exception-Meldung übernehmen. `asMap()` liefert eine immutable, einfügereihenfolgetreue Sicht auf die exakt empfangenen Properties.

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

Finalisierte v1-Oberfläche:

```java
List<RouterOsRecord> records =
        mtApi.raw().execute("/ip/route/print");

RawCommandResult result =
        mtApi.raw()
             .command("/ip/firewall/address-list/add")
             .argument("list", "blocked")
             .argument("address", "192.0.2.10")
             .execute();

CompletableFuture<List<RouterOsRecord>> asyncRecords =
        mtApi.async().raw().execute("/ip/route/print");

CompletableFuture<RawCommandResult> asyncResult =
        mtApi.async().raw()
             .command("/ip/firewall/address-list/add")
             .argument("list", "blocked")
             .argument("address", "192.0.2.10")
             .execute();
```

Die Convenience-`execute(path)`-Variante mappt direkt als eigene `RouterOsOperation<List<RouterOsRecord>>` in der Engine. Sie verwendet absichtlich kein nachgeschaltetes `thenApply(...)`, damit `CompletableFuture.cancel(...)` weiterhin bis zum internen `OperationContext` und damit zum Low-Level-Tag propagiert.

`RawCommandBuilder` unterstützt dieselben Argument-, Equality-Query- und Property-Selection-Bausteine wie `RouterOsCommand`. `RawCommandResult` ist immutable und bewahrt die vollständige `completion()` einschließlich `ret` und zukünftiger unbekannter Properties.

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

Finalisierte v1-Zugriffe:

```java
List<RouterOsRecord> records()
RouterOsRecord completion()
```

Beide Teile sind immutable Snapshots. `completion()` bleibt auch bei leerem `!done` ein vorhandener leerer `RouterOsRecord`; dadurch müssen Consumer nicht zwischen `null` und einer legitimen leeren Completion unterscheiden.

## 12. Interne Operations

Fachliche Logik wird in kleinen internen Operations gekapselt.

Konzeptionell:

```java
interface RouterOsOperation<T> {
    String name();
    RouterOsCommand command();
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

`CommandEngine.executeSync(...)` und `CommandEngine.executeAsync(...)` erzeugen denselben `OperationContext` und starten denselben listenerbasierten Dispatch. Auch Sync wartet deshalb auf denselben internen Completion-Pfad statt die synchrone Low-Level-API aufzurufen.

Async-Mapping läuft nach dem Low-Level-Callback über den internen Dispatch-Executor. Die öffentliche Future-Completion wird anschließend auf den Callback-Executor übergeben. Dadurch führt der Low-Level-Processor weder Operation-Mapping noch User-Future-Callbacks aus. Ein synchron wartender Caller hängt nicht vom Callback-Executor ab.

Für kontrolliertes Session-Close führt die Engine zusätzlich eine interne Concurrent-Registry der aktuell endlichen `OperationContext`-Instanzen. `cancelActive()` fordert deren best-effort Cancellation an. Die Registry erzeugt weder RouterOS-Tags noch Response-Routing und ist kein zweiter Dispatcher; Einträge werden bei jeder logischen Terminal-Completion automatisch entfernt.

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

Erfolgt Cancellation noch im Zustand `CREATED`, bevor Dispatch begonnen hat, wird die Operation direkt lokal `CANCELLED` und es wird kein RouterOS-Command gesendet. Scheitert ein bereits gestarteter Dispatch nach gewonnenem Cancel-Race, ohne jemals einen Tag zu liefern, wird `CANCELLING` ebenfalls sauber zu `CANCELLED` finalisiert.

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

Timeout und User-Cancellation teilen lediglich den best-effort Remote-Cancel-Mechanismus. Timeout terminiert logisch mit `FAILED` und `MikrotikTimeoutException`; User-Cancellation terminiert mit `CANCELLED`. Ein Remote-Cancel-Fehler ersetzt das bereits gewonnene logische Terminalergebnis nicht.

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

Wird ein synchroner Wait unterbrochen, fordert die Facade best-effort Cancellation an, stellt das Interrupt-Flag des Caller-Threads wieder her und meldet einen `MikrotikCommandException` mit sicherem Operation-/Command-Kontext.

Dadurch hängt ein synchron wartender Aufruf nicht davon ab, ob ein User-Executor blockiert ist.

Vorgesehene Executor-Rollen:

```text
callbackExecutor
timeoutScheduler
blockingExecutor
```

Facade-eigene Executors werden beim `close()` beendet.

Für endliche v1-Commands existieren konkret:

```text
dispatchExecutor      → interne Dispatch-/Mapping-Arbeit
timeoutScheduler      → Command-Timeouts
callbackExecutor      → öffentliche Async-Completion/User-Callbacks
```

Wenn kein Callback-Executor geliefert wurde, erzeugt die Facade einen eigenen. Ein vom Nutzer gelieferter Callback-Executor bleibt dagegen caller-owned und wird niemals von der Facade geschlossen.

## 18. Flow.Publisher

Streaming-Publisher sind cold.

Die interne v1-Struktur besteht aus:

```text
RouterOsPublisher<T>
RouterOsSubscription<T>
SerialDelivery
```

Die Konstruktion eines `RouterOsPublisher` führt keinerlei RouterOS-I/O aus. Erst `subscribe(...)` erzeugt eine neue `RouterOsSubscription`; jede Subscription startet exakt eine eigene listenerbasierte RouterOS-Operation. Mehrere Subscriber teilen deshalb weder RouterOS-Tag noch Queue, Demand oder Terminalzustand.

Pro Subscription existiert eine begrenzte Queue mit fester Kapazität.

`request(n)` steuert ausschließlich die lokale Auslieferung und kann den Router selbst nicht zuverlässig drosseln. Demand-Arithmetik saturiert bei `Long.MAX_VALUE`. Ein `request(0)` oder negativer Wert terminiert die Subscription nach den Flow-Regeln mit `IllegalArgumentException`.

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

Der Low-Level-`ResultListener.receive(...)` führt keinen User-Code und kein typisiertes Mapping aus. Er erzeugt lediglich einen immutable `RouterOsRecord`-Snapshot, aktualisiert die bounded Queue und stößt den internen Dispatch-Handoff an. Mapping und sämtliche Flow-Callbacks laufen erst danach auf der seriellen Delivery-Lane.

`SerialDelivery` garantiert FIFO-Auslieferung und verhindert parallele Callbacks derselben Subscription auch dann, wenn der zugrunde liegende Callback-Executor mehrere Threads besitzt. Ein langsamer oder blockierender Subscriber darf dadurch den RouterOS-Reader-/Processor-Thread nicht blockieren.

`!done` darf eintreffen, während noch Records gepuffert sind. `onComplete` wird erst ausgeliefert, nachdem alle bereits gepufferten Records entsprechend vorhandenem Demand ausgeliefert wurden. Bis zur tatsächlichen Terminal-Auslieferung kann ein lokaler Mapping-Fehler eines gepufferten Records einen vorgemerkten erfolgreichen Abschluss noch in `onError` überführen; ein erst danach eintreffendes Low-Level-Terminalsignal darf dagegen kein zweites Terminalereignis erzeugen.

`Flow.Subscription.cancel()` verwendet denselben `OperationContext` wie endliche Commands. Damit wird auch Cancellation vor Rückgabe des Low-Level-Tags vorgemerkt und nach Tag-Zuweisung best-effort genau einmal an `ApiConnection.cancel(tag)` weitergegeben. Nach Flow-Cancellation werden keine weiteren `onNext`, `onComplete` oder `onError` ausgeliefert.

Langlaufende Streams besitzen standardmäßig keinen generischen Overall-Command-Timeout.

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

Vor jedem neuen öffentlichen technischen Command wird der Session-Zustand geprüft:

```text
OPEN      → Operation darf starten
BROKEN    → MikrotikConnectionException mit erhaltener fataler Low-Level-Ursache
CLOSING   → IllegalStateException
CLOSED    → IllegalStateException
```

Beim Async-Baum wird ein bereits bekannter `BROKEN`-Fehler über den konfigurierten Callback-Executor in das öffentliche Future completed. `CLOSING/CLOSED` bleibt bewusst ein synchroner Lifecycle-Programmierfehler.

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

Die Facade entfernt ihren Listener vor dem Low-Level-`close()`. Ein trotzdem verspäteter oder bereits konkurrierender `connectionLost()`-Callback darf einen begonnenen kontrollierten Close nicht mehr in `BROKEN` umwandeln. Der Zustandsübergang wird atomar entschieden.

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

`MikrotikRtrApi.close()` ist idempotent und darf beim eigentlichen Low-Level-Close eine `MikrotikConnectionException` melden. Unabhängig davon endet ein begonnener kontrollierter Close im Zustand `CLOSED`.

Ablauf:

```text
OPEN/BROKEN → CLOSING
reject new operations
cancel active streams
CommandEngine.cancelActive()
best-effort /cancel for finite operations with known tags
complete public operations terminal
close ApiConnection
drain already queued internal dispatch completion
shutdown facade-owned timeout/dispatch/default-callback executors
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

Die produktive Verbindungserzeugung delegiert ausschließlich an die öffentliche Low-Level-API `ApiConnection.connect(SocketFactory, host, port, timeout)`. Für kontrollierte Unit-Tests existiert intern ein schmaler, nicht öffentlicher Connection-Factory-Seam; er ist kein Bestandteil der Facade-API.

`MikrotikRtrApiBuilder.connect()` ist der öffentliche Bootstrap-Einstieg. Der konfigurierte Command-Timeout wird direkt nach Listener-Registrierung auf die Low-Level-Connection gesetzt. Der optionale caller-provided Callback-Executor wird als Session-Konfiguration übernommen und ab dem öffentlichen Async-Baum tatsächlich für Future-Completion/User-Callbacks verwendet. Wenn keiner geliefert wurde, erzeugt die Session einen facade-owned Default-Callback-Executor. Der validierte `commandTimeout` wird unverändert in die Runtime-`CommandEngine` übernommen.

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

Finalisierte v1-Transport-API:

```java
ApiTransport.plain()
ApiTransport.tlsUnverified()
ApiTransport.tlsVerified()
ApiTransport.tlsVerified(SSLContext)
ApiTransport.custom(SocketFactory, defaultPort)
```

`plain()` verwendet standardmäßig Port 8728. Beide TLS-Modi verwenden standardmäßig Port 8729. Ein expliziter Builder-Port überschreibt den Transport-Default.

Verified TLS verwendet standardmäßig den JVM-Truststore und aktiviert zusätzlich Hostname-/Endpoint-Verification. Da die Low-Level-Bibliothek ein vom `SocketFactory` erzeugtes Socket selbst verbindet, kapselt die Facade die verwendete `SSLSocketFactory` und setzt die Endpoint-Identification explizit auf `HTTPS`. Auch ein eigener `SSLContext` behält diese Hostname-Prüfung bei.

`tlsUnverified()` deaktiviert bewusst Zertifikats- und Hostname-Prüfung und ist deshalb sowohl im Typ als auch in Diagnostics ausdrücklich als unsicher erkennbar.

Ein vollständig eigener `SocketFactory` bleibt über `custom(...)` als Escape Hatch möglich. In diesem Fall interpretiert oder verändert die Facade dessen Sicherheitssemantik nicht; der Caller gibt deshalb auch den Default-Port dieses Transports an.

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

Retry-fähig sind ausschließlich technische Connect-/Login-Probleme.

Ein eindeutiger Authentication-Reject wird nicht sinnlos mehrfach wiederholt. Ein während `login(...)` gelieferter öffentlicher `ApiCommandException` wird als Authentication-Reject behandelt und zu `MikrotikAuthenticationException` gemappt.

Nach erfolgreichem Login werden Environment-Kommandos nicht automatisch wiederholt. Ein Fehler in `/system/resource` oder `/system/package` beendet deshalb den Bootstrap dieses Aufrufs statt bereits ausgeführte Bootstrap-Kommandos zu replayen.

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

Finalisierte v1-Struktur:

```java
RouterOsEnvironment
  .systemInfo()
  .packages()
  .packageInformationAvailable()

RouterOsSystemInfo
  .version()
  .architectureName()
  .boardName()
  .platform()
  .raw()

RouterOsPackage
  .name()
  .version()
  .raw()
```

`RouterOsSystemInfo.version()` ist verpflichtend. Architektur, Board und Plattform bleiben optional. Package-Name ist für vorhandene Package-Zeilen verpflichtend, Package-Version optional.

Rohdaten bleiben vollständig erhalten. Alle Environment-Modelle und enthaltenen Listen sind immutable Session-Snapshots.

Öffentlich wird ein read-only Zugriff angeboten:

```java
mtApi.environment();
```

Das Environment ist ein Session-Snapshot und wird in v1 nicht automatisch aktualisiert.

## 27. /system/resource und /system/package

`/system/resource` ist für eine normale typisierte Session verpflichtend.

`/system/package` ist eine wichtige zusätzliche Informationsquelle, darf aber bei eindeutig nicht unterstützten Systemen optional fehlen.

Ein erfolgreich abgefragter, aber leerer Package-Snapshot ist fachlich verschieden von nicht verfügbarer Package-Information:

```text
packages() = Optional.of(emptyList())
→ Package-Abfrage war verfügbar und lieferte keine Zeilen

packages() = Optional.empty()
→ Package-Information ist auf diesem System ausdrücklich nicht verfügbar
```

Ein nicht verfügbarer Package-Snapshot bedeutet:

```text
weniger Vorwissen für Capability Resolution
```

nicht automatisch:

```text
Session kann nicht verwendet werden
```

Für v1 wird „Package-Command ausdrücklich nicht verfügbar“ konservativ nur dann akzeptiert, wenn der öffentliche Low-Level-`ApiCommandException` eine echte RouterOS-Category `0` enthält und die RouterOS-Meldung command-bezogen ist. Andere Command-Fehler bleiben fatal.

`/system/resource` muss genau einen verwertbaren Datensatz mit nicht-leerer `version` liefern. Technische Fehler wie Connection-Loss während des Bootstrap bleiben Bootstrap-Fehler.

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

Finalisierte v1-Struktur:

```java
CapabilityState
  SUPPORTED
  UNSUPPORTED
  UNKNOWN

CapabilityRegistry
  .state(capability)
  .recordDefinitive(capability, state)
  .resolve(capability, readOnlyPrintCommand, probe)
  .snapshot()
```

`CapabilityRegistry` ist session-scoped. Nur `SUPPORTED` und `UNSUPPORTED` werden dauerhaft für diese Session gecacht. `UNKNOWN` sowie technische Probe-Fehler verändern den Cache nicht und dürfen bei einem späteren Aufruf erneut geprüft werden. Widersprüchliche definitive Erkenntnisse werden als interner Zustandsfehler abgelehnt statt still überschrieben zu werden.

Aktive v1-Probes akzeptieren ausschließlich RouterOS-`/print`-Commands. Package-, Version-, Board- und andere bereits bekannte Environment-Informationen werden nicht durch synthetische Write- oder Seiteneffekt-Probes ersetzt.

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

Der DHCP-Lease-Slice verwendet dagegen eine eindeutige `SINGLE` Source: `/ip/dhcp-server/lease/print`. Unterschiede wie `agent-circuit-id` gegenüber `active-agent-circuit-id` sind Schema-Varianten innerhalb derselben Source und keine Source-Fallback-Entscheidung.

Für das generische Compatibility-Framework bedeutet die Präsenz eines Source-Resultsets in der Resolver-Eingabe: diese Quelle wurde erfolgreich abgefragt. Eine vorhandene leere Liste ist deshalb semantisch verschieden von einem fehlenden Resultset.

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

Finalisierte v1-Plansemantik:

```text
SINGLE
→ genau eine im Plan festgelegte Quelle

PREFERRED_FALLBACK
→ Preferred verwenden, sobald dessen Resultset erfolgreich vorhanden ist
→ auch eine leere Preferred-Liste ist ein gültiges Ergebnis
→ nur wenn das Preferred-Resultset fehlt, darf Fallback verwendet werden

CONDITIONAL
→ eine explizit vorab aufgelöste Compatibility-Bedingung wählt genau eine Quelle

COMPOSITE
→ alle im Plan genannten erfolgreich vorhandenen Quellen bleiben relevant
```

`first non-empty result wins` ist dadurch nicht nur dokumentarisch, sondern auch im generischen Resolver ausgeschlossen.

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

Finalisierte interne Repräsentation:

```java
ResolvedRecord
  .record()
  .source()
```

`FeatureSourceResolver` bewahrt Source-Reihenfolge und jeden einzelnen Record unverändert. Selbst identische `.id`-, MAC-, Name- oder andere Werte aus zwei COMPOSITE-Quellen werden nicht generisch zusammengeführt oder dedupliziert.

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

Der initiale Katalog wird mit Task 10 unter `docs/routeros-compatibility.md` angelegt. Er enthält bereits die bekannten Themen WiFi/CAPsMAN, Package-Hinweise, Signalvarianten, DHCP Client-/Circuit-ID, hardwareabhängige Interface-Counter und RouterOS-versionabhängige File-Funktionen. Spätere Typed-Module konkretisieren die jeweiligen Zeilen mit echten Fixtures und verifizierten Property-Varianten.

Compatibility-Diagnostics enthalten ausschließlich validierte stabile Feature-Identifier, Strategy-Namen und Source-Identifier. RouterOS-Recordwerte, Command-Argumente, Queries, Credentials oder freie secret-bearing Begründungstexte werden dort nicht ausgegeben.

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

Für `DhcpLease` sind in v1 ausschließlich die aus dem Legacy-Parser/Fixture-Inventar belegten Felder typisiert:

```text
address
mac-address
client-id
address-lists
server
dhcp-option
status
expires-after
last-seen
active-address
active-mac-address
active-client-id
active-server
host-name
agent-circuit-id / active-agent-circuit-id
agent-remote-id / active-agent-remote-id
radius
dynamic
blocked
disabled
comment
```

`active-agent-*` hat Vorrang vor dem Legacy-`agent-*`-Feld, wenn beide vorhanden sind. Andere Property-Namen, die zufällig `circuit`, `agent` oder `remote` enthalten, werden nicht heuristisch interpretiert. Unbekannte Varianten bleiben ausschließlich in `raw()`.

`.id` wird in diesem Slice absichtlich nicht als typed `DhcpLease`-Property eingeführt, weil der bisherige DTO-Vertrag es nicht als fachliches Lease-Feld verwendete. Es bleibt vollständig über `raw()` verfügbar.

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

`MikrotikFacadeException` erweitert `Exception` und ist damit eine checked Exception. Die technischen Facade-Unterklassen bleiben ebenfalls checked; nur Programmier- und Lifecycle-Fehler wie `IllegalArgumentException` und `IllegalStateException` sind davon getrennt unchecked.

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

Diese Cause-Identität hat eine technische Sicherheitsgrenze: Die Facade kann eigene Exception-Messages, strukturierte Felder, Logs und Diagnostics vollständig redigieren, aber sie kann den Text eines fremden Low-Level-`Throwable` nicht verändern und gleichzeitig exakt dasselbe Throwable-Objekt als `cause` erhalten. Consumer dürfen deshalb nicht ungefiltert rekursiv fremde Cause-Messages als sichere Facade-Diagnostics behandeln.

## 37. RouterOS Command Error Context

`MikrotikCommandException` stellt sicheren strukturierten Kontext bereit:

```java
Optional<String> operation()
Optional<String> commandPath()
OptionalInt category()
Optional<String> routerOsMessage()
```

Der Command-Pfad enthält keine Argumente. Auch wenn ein Raw-Command bereits Argumente oder Queries direkt im gelieferten String enthält, wird der öffentliche `commandPath()` auf den reinen Pfad gekürzt.

`routerOsMessage()` enthält ausschließlich eine bereits sanitizierte RouterOS-Meldung. Zur Sanitization werden sowohl zentral bekannte sensitive Schlüssel als auch die tatsächlich zum Command gehörenden sensitiven Werte berücksichtigt, damit von RouterOS reflektierte Secrets nicht wieder in öffentliche Exception-Felder gelangen.

`ApiCommandException.hasCategory()` muss berücksichtigt werden, da Kategorie `0` ein realer Wert sein kann.

Der RouterOS-Transport-Tag bleibt intern.

## 38. Secret Redaction

Command-Argumente werden nie ungefiltert in Exceptions oder Logs ausgegeben.

Die zentrale Policy liegt in `SecretRedactor`. `CommandDiagnosticRenderer` verwendet dieselbe Policy für strukturierte Command-Diagnostics, Raw-Commands und RouterOS-Fehlermeldungen.

Sensitive Keys werden normalisiert und zentral behandelt. Mindestens betroffen:

```text
password / passphrase
PSK / pre-shared key
private key material
authentication responses
authentication/privacy/encryption passwords or keys
SNMP community / secrets
tokens / credentials / API keys
weitere Credentials
```

Bekannte sensitive Werte werden zusätzlich aus freiem Diagnose-Text entfernt, auch wenn RouterOS nur den Wert reflektiert und dort keinen Schlüssel mehr nennt. Erkennbare Inline-Zuweisungen wie `password=...`, `community:...` oder `private-key=...` werden ebenfalls redigiert.

Arbiträre Raw-Commands sind kein Redaction-Escape-Hatch. `RouterOsCommand.toString()` darf kein vollständiger unredacted Command Dump sein.

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


Task 16 finalisiert diese Architektur wie folgt:

```java
mtApi.files().list();
mtApi.files().find(properties);
mtApi.files().findByName(name);
mtApi.files().download(remoteFile, localPath);

mtApi.async().files().download(remoteFile, localPath);
```

`RouterFile` modelliert `/file/print`-Metadaten und behält unbekannte Felder in `raw()`. Exakte Namenssuche liefert `Optional.empty()` bei Abwesenheit und behandelt Mehrdeutigkeit als Datenfehler.

Der Binary-Pfad verwendet ausschließlich `ApiConnection.downloadFile(...)`. Die Facade implementiert weder Chunking noch `.part`-Handling erneut. `IOException` wird zu `MikrotikFileException`; Low-Level Connection-, Command- und Data-Fehler behalten ihre jeweilige Facade-Kategorie. Nur ein definitiver Category-0-Fehler, der einen fehlenden Binary-Read-Befehl bzw. dessen Chunk-Parameter beschreibt, wird zu `MikrotikUnsupportedFeatureException`. Es gibt keinen geratenen RouterOS-Version-Cutoff.

Für Async-Downloads besitzt jede Facade-Session genau einen bounded `FileDownloadExecutor` mit zwei Worker-Threads und 16 Queue-Slots. Das Blocking-I/O läuft ausschließlich dort; Completion läuft über den bestehenden `callbackExecutor`. User-Cancellation kann einen lokalen Worker interrupten und queued work entfernen, garantiert aber keinen Remote-`/cancel` des aktuell internen Low-Level-Chunks.

Beim kontrollierten `close()` wird der File-Executor zuerst für neue Arbeit geschlossen und queued work terminiert. Danach werden Streams und finite Commands gecancelt und die Connection geschlossen. Erst danach wird der File-Executor vollständig heruntergefahren, solange der Callback-Executor noch lebt. Ein aktiver Low-Level-Read wird dadurch durch Connection-Close beendet.

Das alte ISPSup-`/tool/fetch`-SFTP-Uploadverhalten bleibt bewusst außerhalb des Files-Moduls: Zielhost, Zielpfad und Upload-Policy sind Consumer-Konfiguration und können bei weiterhin bestehendem Bedarf über `raw()` zusammengesetzt werden.

## 42a. Task-17 parity closure

Task 17 adds the last typed primitives required by the supplied legacy-handler inventory.

DHCP gains:

```java
mtApi.dhcpServer().pools();
mtApi.dhcpServer().countLeases(properties);
mtApi.dhcpServer().removeLease(id);
```

`RouterOsCommand` and the structured raw builder support valueless command flags. This is used for RouterOS `count-only`; terminal `ret` is consumed from `CommandResult.completion()` and never by textual line position. `DhcpPool` preserves one RouterOS pool row and exposes exact range fragments without calculating addresses or inventing ISPSup server naming.

`DhcpLease.id()` is an additive view over the retained raw `.id`. Lease reset remains consumer composition: expected-single lookup by address followed by removal only when a row exists. Empty lookup never emits a remove with an empty id, and ambiguous lookup stays a data error.

WiFi gains:

```java
mtApi.wifi().remoteCaps();
mtApi.wifi().findRemoteCapByBaseMac(mac);
```

Remote-CAP authority is driven only by enabled CAPsMAN managers. Legacy `/caps-man/remote-cap` and modern `/interface/wifi/capsman/remote-cap` can be simultaneously relevant and then use `COMPOSITE`. No empty-result fallback exists. `WifiRemoteCap` retains source provenance and normalizes the verified `board-name` / `board` alias while preserving every original field.

Parity closure explicitly distinguishes three terminal states:

```text
IMPLEMENTED
ACCEPTED_RAW_FALLBACK
INTENTIONALLY_OBSOLETE
```

The SFTP upload path remains `ACCEPTED_RAW_FALLBACK`: structured `raw().command("/tool/fetch")` can express `upload=yes` and `src-path`, while destination host/path/credentials remain consumer configuration. The non-functional WiFi-config stub and mutable current-router accessors are `INTENTIONALLY_OBSOLETE`.

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
