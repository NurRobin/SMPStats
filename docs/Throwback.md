# Throwback-Halle

Rückblick auf die Saison 2025 beim ersten Join: Spieler gehen durch einen Gang, links und rechts stehen kleine
Szenen mit ihren eigenen Werten, am Ende liegen die Regeln, danach öffnet sich das Portal zum Spawn. Neue
Spieler sehen an denselben Plätzen Regeln und Event-Infos. Die Halle bleibt danach über `/throwback` und ein
Portal am Spawn besuchbar.

## Ablauf

1. Beim Join wird geprüft, ob der Pflicht-Rundgang offen ist (`throwback_progress.completed_at IS NULL`).
   Wenn ja, landet der Spieler in der Halle (eigene Void-Welt, Adventure-Modus).
2. Die Szenen werden nur für diesen Spieler aufgestellt. Alle Entities entstehen unsichtbar und werden nur dem
   Besitzer gezeigt. Sie sind nicht persistent.
3. An der Regeltafel klickt der Spieler auf "Regeln gelesen" (Fallback: `/throwback confirm`, nur in Reichweite
   der Tafel). Gespeichert werden Zeitstempel und Regelversion.
4. Das Portal am Gangende bleibt bis dahin zu: Wer hineinläuft, wird zurückgeschoben und bekommt einen Hinweis.
5. Danach teleportiert das Portal zum Spawn (oder `throwback.exit`), der Rundgang gilt als abgeschlossen.
6. Quit oder Weltwechsel räumen alle Entities des Spielers ab. Wer mitten im Pflicht-Rundgang geht, startet beim
   nächsten Join wieder am Hallenstart und muss die Regeln erneut bestätigen.
7. Später: `/throwback` oder der Eingang am Spawn (`throwback.spawn_portal`) führen ohne Bestätigung in die Halle,
   `/throwback leave` oder das Portal bringen zurück an den Ausgangsort.

Wer mit der Teleport-Funktion eines anderen Plugins (`/spawn`, `/home` …) aus dem Pflicht-Rundgang springen will,
wird gestoppt. Ausnahme: Permission `smpstats.throwback.bypass` (in `smpstats.admin` enthalten).

## Wer sieht was

| Spieler | Inhalt |
|---|---|
| in der Saison, mindestens `min_playtime_minutes` | 12 Trophäen-Szenen (3 feste: Spielzeit, abgebaute Blöcke, gelaufene Strecke; 9 beste nach Platz ÷ N), Statue, Regeln |
| in der Saison, darunter | Rundgang für Neue mit neutralem "Willkommen zurück" |
| nicht in der Saison | Event-Infos und Regeln |

Werte von 0 werden nie gezeigt. "Platz X von N" erscheint nur bis `rank_badge_top` (Standard 15). Fehlen Trophäen,
stehen an den freien Bühnen Infotafeln.

Auf Servern im Offline-Modus (Testserver) passen die UUIDs nicht zur Online-Saison. Mit
`match_by_name_offline: true` wird dort ersatzweise über den Namen gesucht. Auf dem Live-Server (Online-Modus)
wirkt die Einstellung nie.

## Daten

Die Saison muss importiert sein (`plugins/SMPStats/seasons/<saison>/`, siehe Saison-Import). Die Trophäenwerte
werden beim ersten Login (asynchron) aus der Datenbank in den Speicher geladen. Danach kostet ein Join keinen
Datenbankzugriff auf dem Main-Thread. Fortschritt wird im Speicher gehalten und im Hintergrund geschrieben.

## Konfiguration

Siehe Abschnitt `throwback` in der `config.yml`. Texte, Regeln, Vergleiche ("× Kölner Dom") und Infotafeln stehen in
`plugins/SMPStats/throwback/texts.yml` (MiniMessage, wird beim ersten Start angelegt). Bei neuem Regeltext
`rules_version` hochzählen.

PNGs für die Bilderwand "Bauten 2025" liegen in `throwback.images_folder` (Standard `throwback/images`) als
`<wandname>.png`. Ein Bild mit 512×384 Pixeln passt auf eine Wand von 4×3 Kacheln, andere Größen werden skaliert.
Fehlt das Bild, bleibt die Wand leer. Die Karten-IDs stehen in `throwback/mapwall.yml`, damit ein Neustart keine
neuen Karten anlegt.

## Admin-Befehle (`smpstats.admin`)

| Befehl | Wirkung |
|---|---|
| `/throwback admin build` | erzeugt einen schlichten Platzhalter-Gang mit 12 Bühnen, 3 Bilderwänden, Regeltafel und Portal und speichert das Layout |
| `/throwback admin tp` | teleportiert in die Halle (zum Bauen und Einmessen) |
| `/throwback admin setstart`, `setrules`, `setstage <1-12>`, `portal1`, `portal2`, `addwall <name> <breite> <höhe> [titel]` | Plätze der echten Halle einmessen |
| `/throwback admin walls` | Bilderwände neu aufbauen |
| `/throwback admin reload` | Config und Texte neu laden |
| `/throwback admin reset <spieler>` | Fortschritt löschen: wieder Pflicht-Rundgang |
| `/throwback admin info` | Zustand und Layout |

Bau-Steckbrief für die echte Halle: Ankunftsplattform, Gang mindestens 3 Blöcke breit, 12 Bühnen mit einer
Standfläche von 3 Blöcken Tiefe und 4 Blöcken Breite plus 1 bis 2 Blöcken Luft zu den Seiten, nach hinten und nach oben,
damit die Hologramme (bis 6,5 Blöcke breit) nicht in die Wand ragen (6 links, 6 rechts, zum Gang offen, Einmessen der Bodenmitte), flache Wandflächen für etwa 3 Bilder à 4×3,
Regeltafel etwa 5×3, dekoratives Portal am Gangende. Die Blickrichtung (`yaw`) einer Bühne zeigt auf die
Rückwand der Bühne.

## Last und BlueMap

- Die Halle-Welt ist nur geladen, solange jemand drin ist (Entladen nach `unload_delay_seconds`). Mobs, Wetter,
  Tageszeit und Schaden sind aus.
- Pro Spieler entstehen rund 100 Entities, verteilt auf 14 Ticks. Der Gang pollt nur, wenn jemand in der Halle
  ist (alle 10 Ticks). Der Spawn-Eingang prüft alle 10 Ticks nur die Spieler seiner Welt.
- **BlueMap:** Die Hallen-Welt gehört nicht auf die BlueMap. In der BlueMap-Konfiguration keine Map für
  `throwback_hall` anlegen (BlueMap lädt Welten nur, wenn dafür eine Map-Datei existiert). Die Halle wird
  nach dem Entladen und Neuladen nicht von SMPStats gerendert.
