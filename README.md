# RÚIAN importer

Java (Spring Boot) aplikace, která stáhne zazipovaný XML soubor ve výměnném formátu RÚIAN (VFR),
zparsuje z něj obce (`vf:Obec`) a části obcí (`vf:CastObce`), uloží je do SQL databáze (PostgreSQL)
a zpřístupní je přes REST API. Import se spouští při startu, jednou měsíčně a na vyžádání přes API.

Data lze načíst z několika zdrojů, které jsou nakonfigurované v `importer.sources`:

| id | zdroj |
|----|-------|
| `cuzk` | `https://vdp.cuzk.cz/vymenny_format/soucasna/{lastDayOfPreviousMonth}_OB_573060_UKSH.xml.zip` – aktuální data, výchozí |
| `smartform` | `https://www.smartform.cz/download/kopidlno.xml.zip` – soubor ze zadání |

`{lastDayOfPreviousMonth}` se při každém importu nahradí posledním dnem předchozího měsíce ve formátu `yyyyMMdd`
(v září 2026 → `20260831`), pod tímto datem ČÚZK data publikuje. Díky tomu měsíční import stahuje vždy nová data.

Zdroj se vybírá parametrem `zdroj` (`POST /api/import?zdroj=smartform`), na stránce má každý zdroj své tlačítko.
Bez parametru se použije `importer.default-source`, ten se importuje i při startu a podle rozvrhu.
Další zdroj stačí přidat do konfigurace, tlačítko se objeví samo.

## Jak to funguje

1. `XmlDownloader` stáhne soubor přes `java.net.http.HttpClient` a XML čte proudově přímo ze zipu
   (nic se neukládá na disk). Nezazipované XML se rozpozná podle obsahu a čte se přímo.
2. `RuianXmlParser` parsuje XML pomocí **StAX** (namespace-aware) a čte jen přímé potomky
   `vf:Obec` (`obi:Kod`, `obi:Nazev`) a `vf:CastObce` (`coi:Kod`, `coi:Nazev`, `coi:Obec/obi:Kod`).
   Ostatní data (adresy, parcely, stavební objekty, …) se přeskakují.
3. `GmlGeometryReader` převede geometrii těchto dvou prvků z GML do WKT – definiční bod obce
   i části obce a hranici obce (tu obsahují jen soubory „SH“ z ČÚZK). Hranice tvořená kruhovými
   oblouky se přeskočí, protože ji WKT neumí vyjádřit.
4. `RuianRepository` data uloží v jedné transakci do PostGIS (`ST_GeomFromText`) v souřadnicovém
   systému S-JTSK (EPSG:5514), jak je dodává RÚIAN. Existující záznamy (podle kódu) se aktualizují,
   import lze tedy spouštět opakovaně.
5. `RuianImportService` zajišťuje, že najednou běží nejvýš jeden import, a pamatuje si výsledek posledního.

Import se spouští:

| kdy | jak |
|---|---|
| při startu aplikace | `ImportRunner`, lze vypnout `importer.run-on-startup=false`; selhání aplikaci nezastaví |
| jednou měsíčně | `ScheduledImport` (`@Scheduled`), výchozí cron `0 0 3 2 * *` = 2. den v měsíci ve 3:00 (Europe/Prague) |
| ručně | `POST /api/import` |

## Mapa

Na <http://localhost:8080/> je jednoduchá stránka (Leaflet + OpenStreetMap), která z API vykreslí
hranici obce, její definiční bod a body částí obce. Pro každý nakonfigurovaný zdroj má tlačítko
(ČÚZK, soubor ze zadání) a umí také nahrát a naimportovat vlastní soubor (zip i nezazipované XML).

## REST API

| metoda a cesta | popis |
|---|---|
| `GET /api/obce` | všechny obce |
| `GET /api/obce/{kod}` | jedna obec (404, pokud neexistuje) |
| `GET /api/obce/{kod}/casti-obci` | části dané obce |
| `GET /api/casti-obci` | všechny části obcí |
| `GET /api/casti-obci/{kod}` | jedna část obce |
| `GET /api/obce/{kod}/definicni-bod` | definiční bod obce jako GeoJSON (WGS 84) |
| `GET /api/obce/{kod}/hranice` | hranice obce jako GeoJSON (WGS 84) |
| `GET /api/obce/{kod}/casti-obci/geojson` | definiční body všech částí obce jako GeoJSON FeatureCollection |
| `GET /api/casti-obci/{kod}/definicni-bod` | definiční bod části obce jako GeoJSON (WGS 84) |
| `GET /api/import/zdroje` | nakonfigurované zdroje (id, název, výsledné URL), výchozí první |
| `POST /api/import?zdroj={id}` | spustí import z daného zdroje, bez parametru z výchozího (409 pokud už import běží, 404 pro neznámý zdroj, 502 pokud selže zdroj) |
| `POST /api/import/soubor` | import nahraného souboru (multipart, pole `soubor`, zip i nezazipované XML) |
| `GET /api/import/status` | zda import právě běží a výsledek posledního importu |

Chyby se vrací jako problem details (RFC 9457).

```bash
curl localhost:8080/api/obce/573060/casti-obci
# [{"kod":31801,"nazev":"Drahoraz","kodObce":573060}, ...]

curl localhost:8080/api/obce/573060/definicni-bod
# {"type":"Point","coordinates":[15.2712,50.3312]}

curl -X POST localhost:8080/api/import
# {"source":"https://...","trigger":"MANUAL","startedAt":"...","finishedAt":"...","status":"SUCCESS","obce":1,"castiObci":5,"error":null}
```

## Databáze

Schéma je v [`db/schema.sql`](db/schema.sql), aplikace ho nevytváří:

| tabulka     | sloupce                                  |
|-------------|------------------------------------------|
| `obec`      | `kod` (PK), `nazev`, `definicni_bod` (Point, 5514), `hranice` (MultiPolygon, 5514) |
| `cast_obce` | `kod` (PK), `nazev`, `kod_obce` (FK → obec), `definicni_bod` (Point, 5514) |

Geometrie se ukládají v S-JTSK (EPSG:5514) tak, jak je dodává RÚIAN, a API je přepočítává
do WGS 84 (EPSG:4326) pomocí `ST_Transform`. Databáze proto musí mít rozšíření PostGIS.

## Spuštění přes Docker

```bash
docker compose up --build -d
docker compose logs -f importer
```

Spustí PostgreSQL s PostGIS (schéma se vytvoří automaticky při první inicializaci) a aplikaci na portu 8080.

Výchozí zdroj, cron nebo porty lze nastavit proměnnými prostředí, např. soubor ze zadání:

```bash
IMPORTER_DEFAULT_SOURCE=smartform \
APP_PORT=18080 DB_PORT=55432 docker compose up --build -d
```

Ručně stažený soubor lze naimportovat bez restartu – tlačítkem na stránce, nebo přímo:

```bash
curl -F soubor=@20210331_OB_573060_UZSZ.xml http://localhost:8080/api/import/soubor
```

## Lokální spuštění

Potřeba Java 21 (Gradle ji případně stáhne sám) a běžící PostgreSQL s PostGIS se schématem z `db/schema.sql`.

Gradle 8.14 samotný musí běžet na JDK 17–24 (novější JDK, např. 26, selže s
`Unsupported class file major version`). V takovém případě nastavte `JAVA_HOME` na JDK 21, např.
`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`.

```bash
docker compose up -d db          # nebo vlastní databáze
./gradlew bootRun                # import z importer.url při startu
./gradlew bootRun --args="file://$PWD/20210331_OB_573060_UZSZ.xml"
```

Konfigurace (`application.yml`, lze přepsat proměnnými prostředí):

| vlastnost                  | proměnná prostředí            | výchozí hodnota                                   |
|----------------------------|-------------------------------|---------------------------------------------------|
| `importer.default-source`  | `IMPORTER_DEFAULT_SOURCE`     | `cuzk`                                            |
| `importer.sources.<id>.url` | `IMPORTER_SOURCES_<ID>_URL`  | viz tabulka zdrojů výše                           |
| `importer.run-on-startup`  | `IMPORTER_RUN_ON_STARTUP`     | `true`                                            |
| `importer.schedule.cron`   | `IMPORTER_SCHEDULE_CRON`      | `0 0 3 2 * *` (`-` import vypne)                  |
| `importer.schedule.zone`   | `IMPORTER_SCHEDULE_ZONE`      | `Europe/Prague`                                   |
| `spring.datasource.url`    | `SPRING_DATASOURCE_URL`       | `jdbc:postgresql://localhost:5432/ruian`          |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `ruian`                                           |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `ruian`                                           |
| `server.port`              | `SERVER_PORT`                 | `8080`                                            |

Podporovány jsou i `file:` URL (např. `file:///tmp/kopidlno.xml.zip`), zazipované i nezazipované XML.

## Testy

```bash
./gradlew test
```

Integrační testy si samy spustí PostGIS v Dockeru (Testcontainers), takže je potřeba běžící Docker.

- `RuianXmlParserTest` – parsování XML včetně převodu geometrie do WKT.
- `RuianImportServiceTest` – celý import (zip / XML → PostGIS se stejným schématem), opakovaný import,
  stav importu, geometrie přepočtená do WGS 84 a GeoJSON FeatureCollection.
- `ScheduledImportTest` – naplánování importu podle cronu, cron spouští import jednou měsíčně.
- `ImportUrlResolverTest` – dosazení data do URL (přelom roku, únor, přestupný rok).
- `ApiControllerTest` – REST API včetně chybových stavů.
