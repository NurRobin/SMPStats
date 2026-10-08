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
| `/throwback admin edit` | Bearbeitungsmodus starten (auch mit `smpstats.throwback.build`), siehe unten |
| `/throwback admin edit save`, `cancel` | Entwurf speichern (nur mit allen Pflichtteilen) oder verwerfen |
| `/throwback admin edit wallsize <breite> <höhe> [titel]` | Größe und Titel der Bilderwände, die danach gesetzt werden (Standard 4×3, Titel "Bauten 2025: Bau N") |
| `/throwback admin build` | erzeugt einen schlichten Platzhalter-Gang mit 12 Bühnen, 3 Bilderwänden, Regeltafel und Portal und speichert das Layout |
| `/throwback admin tp` | teleportiert in die Halle (zum Bauen und Einmessen) |
| `/throwback admin setstart`, `setrules`, `setstage <1-12>`, `portal1`, `portal2`, `addwall <name> <breite> <höhe> [titel]` | Plätze der echten Halle einmessen |
| `/throwback admin walls` | Bilderwände neu aufbauen |
| `/throwback admin reload` | Config und Texte neu laden |
| `/throwback admin reset <spieler>` | Fortschritt löschen: wieder Pflicht-Rundgang |
| `/throwback admin info` | Zustand und Layout |

Die Einmess-Befehle bleiben neben dem Bearbeitungsmodus bestehen und arbeiten direkt auf dem gespeicherten Layout.

Bau-Steckbrief für die echte Halle: Ankunftsplattform, Gang mindestens 3 Blöcke breit, 12 Bühnen mit einer
Standfläche von 3 Blöcken Tiefe und 4 Blöcken Breite plus 1 bis 2 Blöcken Luft zu den Seiten, nach hinten und nach oben,
damit die Hologramme (bis 6,5 Blöcke breit) nicht in die Wand ragen (6 links, 6 rechts, zum Gang offen, Einmessen der Bodenmitte), flache Wandflächen für etwa 3 Bilder à 4×3,
Regeltafel etwa 5×3, dekoratives Portal am Gangende. Die Blickrichtung (`yaw`) einer Bühne zeigt auf die
Rückwand der Bühne. Die Datenschutz-Tafel bekommt einen eigenen Platz (`layout.privacy`, Bodenpunkt unter der Tafel,
Blickrichtung zum Leser, Tafelunterkante 1,5 Blöcke darüber). Fehlt er (alte Layouts), hängt sie wie bisher 2 Blöcke
neben der Regeltafel.

## Bearbeitungsmodus

Builder setzen die Plätze der Halle mit Werkzeugen statt mit Koordinaten-Befehlen. Erlaubt ist das mit
`smpstats.throwback.build` (nur der Bearbeitungsmodus, keine sonstigen Admin-Befehle) oder `smpstats.admin`, das das
Recht einschließt. Ein Builder ohne `smpstats.admin` braucht also nur dieses eine Recht.

### Ablauf

1. `/throwback admin edit`. Wer nicht in der Hallen-Welt steht, wird zum Start der Halle teleportiert.
2. Das Inventar wird in `plugins/SMPStats/throwback/edit/<uuid>.yml` gesichert und durch die Werkzeugleiste ersetzt.
   Es kommt zurück bei Speichern, Abbrechen, Quit, Kick, Tod, Verlassen der Hallen-Welt und beim Abschalten des
   Plugins. Blieb nach einem Absturz eine Sicherung liegen, stellt der nächste Join sie wieder her und entfernt die
   Werkzeuge, ohne etwas zu verdoppeln. Ist die Sicherung nicht lesbar, startet der Modus gar nicht.
3. Die Sitzung beginnt mit den Markierungen des gespeicherten Layouts, geändert wird also schrittweise.
4. Der Entwurf liegt als `<uuid>.draft.yml` daneben und überlebt Quit und Neustart: Der nächste `edit` macht dort
   weiter. `edit cancel` verwirft ihn, Speichern löscht ihn.
5. `edit save` oder das Speichern-Werkzeug übernimmt den Entwurf als Layout (`config.yml`), baut die Bilderwände neu
   auf (entfernte Wände verlieren ihre Rahmen) und beendet die Sitzung.
6. Läuft `/throwback admin edit` in der Sitzung nochmal, prüft es alle Boxen neu. Das ist nötig, wenn Blöcke mit
   WorldEdit oder Befehlen geändert wurden: Nur Setzen und Abbauen des Builders selbst werden bemerkt.

### Werkzeuge (Leiste, von links)

Erkannt werden sie nur an einem Tag im Item, nie an Name oder Material.

| Werkzeug | Rechtsklick auf einen Block |
|---|---|
| Start | Startpunkt auf der Blockoberseite, Blickrichtung des Builders |
| Bühne | Figurenplatz, Blickrichtung des Builders zeigt zur Rückwand. Klick auf den hintersten Boden-Block der Standfläche; quer dazu rastet der Punkt auf die Blockkante nächst dem Klickpunkt ein, weil 4 Blöcke keine Mitte haben. Die nächste freie Nummer (höchstens 12) |
| Regeltafel | Regelstation (Knopf), die Tafel schaut zum Builder (Blickrichtung + 180°); die Tafel hängt wie in der Tour 2 Blöcke daneben |
| Datenschutz-Tafel | eigener Platz der Tafel, sie schaut zum Builder |
| Portal-Ecke 1, 2 | die beiden Ecken des Portal-Bereichs, je der Block über dem angeklickten |
| Bilderwand | unten links vor der Wand; die Rahmen schauen gegen die Blickrichtung. Name `bauten-<n>`, Größe über `edit wallsize` |
| Rückgängig | entfernt die zuletzt gesetzte Markierung (auch ohne Block, in die Luft klicken) |
| Speichern | wie `edit save` |

Die Blickrichtung wird auf 90° gerundet. Linksklick mit einem Werkzeug entfernt dessen nächstgelegene Markierung
innerhalb von 2 Blöcken um den angeklickten Block; der Block selbst bleibt heil. Wird eine Bühne entfernt, rücken die
folgenden nach (die Nummer ist die Reihenfolge). Singletons (Start, Regel-, Datenschutz-Tafel, Portal-Ecken) ersetzt
ein neues Setzen.

Gesperrt sind Droppen, Verschieben, Ziehen, Hotbar-Tasten, Tausch in die Nebenhand, Benutzen an Entities, Abbauen mit
einem Werkzeug in der Hand, Öffnen von Containern, und beim Tod fallen keine Werkzeuge. Da die Leiste alle 9 Hotbar-Plätze
belegt, wird in der Sitzung nicht mit Blöcken gebaut; dafür den Modus verlassen (`quit` behält den Entwurf) oder
WorldEdit nutzen.

### Boxen und Vorschau

Jede Markierung zeigt ihren Platzbedarf als Partikel-Box (nur 12 Kanten, Punktabstand 0,5, alle 10 Ticks, nur im Umkreis
von 32 Blöcken, höchstens 1500 Punkte pro Durchlauf, nächste zuerst). Nur der Builder bekommt sie
(`Player#spawnParticle`; Tour-Spieler sehen nichts). Grün ist frei, rot heißt: feste Blöcke ragen hinein.

| Element | Box |
|---|---|
| Bühne | Freiraum: Standfläche 3 tief × 4 breit plus 2 Blöcke zu den Seiten und nach hinten, 5 hoch; dazu die Standfläche als zweite Box in Aqua |
| Start | Säule 1×1×2 |
| Regel-/Datenschutz-Tafel | Fläche der echten Tafel (4 Blöcke breit, Höhe nach Zeilen der Texte, Maße in `PanelMetrics`) |
| Portal | Quader zwischen den Ecken |
| Bilderwand | Rahmenfläche breite×höhe vor der Wand |

Ob etwas rot ist, wird beim Setzen und Ändern sowie bei Setzen/Abbauen des Builders im Bereich berechnet und
gespeichert; der Partikel-Tick liest nur diesen Wert. Als fest gilt jedes Material mit `Material#isSolid`, Laternen
und Zäune zählen also mit. Rot ist nur eine Warnung: Beim Speichern erscheint sie als Hinweis.

Regel- und Datenschutz-Tafel erscheinen sofort als echte Tafel (derselbe Renderer und dieselben Texte wie in der Tour,
nur für den Builder, nicht gespeichert, am Ende entfernt).

### Pflichtteile

Gespeichert wird nur mit: 1× Start, 1× Regeltafel, 1× Datenschutz-Tafel, beide Portal-Ecken und mindestens
`throwback.edit.min_stages` (Standard 8) bis höchstens 12 Bühnen. Bilderwände sind optional. Fehlt etwas, zeigt die
Fehlermeldung die Checkliste (zum Beispiel "Bühnen 6/8 ✗"). Dieselbe Checkliste steht während der Sitzung in der
Actionbar.

## Last und BlueMap

- Die Halle-Welt ist nur geladen, solange jemand drin ist (Entladen nach `unload_delay_seconds`). Mobs, Wetter,
  Tageszeit und Schaden sind aus.
- Pro Spieler entstehen rund 100 Entities, verteilt auf 14 Ticks. Der Gang pollt nur, wenn jemand in der Halle
  ist (alle 10 Ticks). Der Spawn-Eingang prüft alle 10 Ticks nur die Spieler seiner Welt.
- **BlueMap:** Die Hallen-Welt gehört nicht auf die BlueMap. In der BlueMap-Konfiguration keine Map für
  `throwback_hall` anlegen (BlueMap lädt Welten nur, wenn dafür eine Map-Datei existiert). Die Halle wird
  nach dem Entladen und Neuladen nicht von SMPStats gerendert.
