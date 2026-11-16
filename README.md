# Anchorstack

Chunkloader fuer Paper 26.2. Aktiviert durch eine Saeule aus drei Vanilla-Bloecken, von unten nach oben:

1. Kupferblock (jede Oxidationsstufe, gewachst oder nicht)
2. Obsidian (Stufe 1, 3x3 Chunks) oder Crying Obsidian (Stufe 2, 5x5 Chunks)
3. Aufrecht stehender Blitzableiter (ebenfalls in allen Oxidationsstufen, gewachst oder nicht)

Die Reihenfolge beim Bauen ist egal. Der Loader aktiviert sich, sobald die Saeule komplett ist, und haelt dann die Chunks um die Saeule dauerhaft geladen, auch wenn kein Spieler in der Naehe ist.

## Benutzen

- **Bauen:** Beim Aktivieren leuchten die Grenzen des geladenen Bereichs 20 Sekunden lang auf. Danach zeigt ein kleiner Funkenring an der Blitzableiter-Spitze, dass der Loader laeuft.
- **Grenzen nochmal zeigen:** Rechtsklick auf einen der drei Bloecke mit leerer Hand (oder einem Gegenstand, der kein Block ist).
- **Stufe wechseln:** Den Mittelblock abbauen und den anderen setzen. Der Abbau beendet den Loader sofort, der neue Block legt ihn mit der neuen Stufe neu an.
- **Abbauen:** Wer einen der drei Bloecke abbaut, beendet den Loader. Das gilt auch fuer Explosionen und Feuer. Kolben bewegen die Saeule nicht. Es gibt keinen Schutz vor fremdem Abbau.
- **Aufraeumen:** Beim Serverstart und alle 5 Minuten prueft das Plugin, ob alle Saeulen noch stehen, und entfernt Loader ohne Saeule. Wurde nur der Mittelblock getauscht, bekommt der Loader die neue Stufe.

## Befehle

| Befehl | Wirkung | Permission |
| --- | --- | --- |
| `/anchorstack list` | Eigene Loader | `anchorstack.use` |
| `/anchorstack remove <id>` | Eigenen Loader aufheben (ID mit mindestens 4 Zeichen) | `anchorstack.use` |
| `/anchorstack admin list` | Alle Loader mit Besitzer | `anchorstack.admin` |
| `/anchorstack admin tp <id>` | Zu einem Loader teleportieren | `anchorstack.admin` |
| `/anchorstack admin remove <id>` | Fremden Loader aufheben | `anchorstack.admin` |
| `/anchorstack admin cleanup` | Alle Loader ohne Saeule sofort entfernen | `anchorstack.admin` |
| `/anchorstack reload` | Config neu laden, Radien werden sofort uebernommen | `anchorstack.admin` |

`/as` ist ein Kurzname fuer `/anchorstack`.

## Einstellungen

Alles steht in `plugins/Anchorstack/config.yml`:

- `tiers.<stufe>.radius`: Radius in Chunks (1 = 3x3, 2 = 5x5).
- `limits.per-player`: Loader pro Spieler (Standard 7), per Permission `anchorstack.limit.<n>` ueberschreibbar.
- `load-when-empty`: `true` (Standard) haelt die Chunks auch ohne Spieler geladen, `false` nur solange jemand online ist. Setzt der Server `pause-when-empty-seconds` in der `server.properties` auf einen Wert ueber 0, pausiert er ohne Spieler komplett, egal was hier steht. Paper stellt das standardmaessig auf -1.
- `limits.max-total-chunks`: Obergrenze fuer dauerhaft geladene Chunks auf dem ganzen Server.
- `validate-interval-seconds`: Abstand der Pruefung, ob die Saeulen noch stehen.
- `particle-interval-seconds`: Takt des Funkenrings, 0 schaltet ihn ab.
- `border.duration-seconds` und `border.interval-ticks`: Dauer und Takt der Grenzanzeige.
- `messages`: alle Texte, im MiniMessage-Format.

Die Loader stehen in `plugins/Anchorstack/loaders.json`. Ist die Datei nicht lesbar, startet das Plugin ohne Loader und legt eine Kopie als `loaders.json.broken-<zeit>` ab.

## Bauen

Java 25 wird gebraucht, Gradle bringt der Wrapper mit.

    ./gradlew build

Unter Windows `gradlew.bat build`. Es entsteht je Minecraft-Version eine Jar in `build/libs/`, zum Beispiel `anchorstack-1.0.2-mc26.2.jar` und `anchorstack-1.0.2-mc26.3.jar`. Nimm die Jar, die zur Version deines Paper-Servers passt, und lege sie in den `plugins`-Ordner.

Alle Jars entstehen aus demselben Code, es gibt keine Branches pro Version. Die Liste der Versionen steht oben in `build.gradle.kts` (`mcVersions`). Fuer jede Version wird gegen die passende Paper-API kompiliert und `api-version` in der `plugin.yml` entsprechend gesetzt. Bricht eine neue Version etwas in der API, schlaegt nur der Build dieser Version fehl.

## Release

Ein Tag mit `v` am Anfang loest die GitHub-Pipeline aus. Sie baut die Jar und haengt sie ans Release:

    git tag v1.0.1
    git push origin v1.0.1
