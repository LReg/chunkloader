# Anchorstack

Chunkloader fuer Paper 26.2. Aktiviert durch eine Saeule aus drei Vanilla-Bloecken, von unten nach oben:

1. Kupferblock (jede Oxidationsstufe, gewachst oder nicht)
2. Obsidian (Stufe 1, 3x3 Chunks) oder Crying Obsidian (Stufe 2, 5x5 Chunks)
3. Aufrecht stehender Blitzableiter

Die Reihenfolge beim Bauen ist egal. Der Loader aktiviert sich, sobald die Saeule komplett ist.

## Bauen

Java 25 und Gradle werden gebraucht.

    gradle wrapper
    ./gradlew build

Die Jar liegt danach in `build/libs/` und kommt in den `plugins`-Ordner des Paper-Servers.

## Befehle

| Befehl | Wirkung | Permission |
| --- | --- | --- |
| `/anchorstack list` | Eigene Loader | `anchorstack.use` |
| `/anchorstack remove <id>` | Eigenen Loader aufheben | `anchorstack.use` |
| `/anchorstack admin list\|tp\|remove` | Alle Loader verwalten | `anchorstack.admin` |
| `/anchorstack reload` | Config neu laden | `anchorstack.admin` |

Loader pro Spieler: `limits.per-player` in der Config, oder `anchorstack.limit.<n>`.
