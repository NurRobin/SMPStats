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

### Bilder

SMPStats bringt keine Bilderwand mit. Bilder der Bauten von 2025 hängen als Karten in normalen Rahmen in der Halle
und werden mit dem Plugin ImageFrame gesetzt: `/if create <name> <url> selection` (Bild per URL) oder
`/if create <name> <url> <breite> <höhe>`. Das dürfen nur Leute mit Team-Rechten; die Befehle gehören zu ImageFrame,
nicht zu SMPStats. Titel über oder unter dem Bild setzt das Text-Werkzeug des Bearbeitungsmodus (siehe unten). Frühere
Versionen kannten `throwback.images_folder` und `throwback/mapwall.yml`; beides wird nicht mehr gelesen und kann
gelöscht werden. Beim ersten Laden der Halle räumt das Plugin Rahmen und Titel der alten Bilderwände ab (erkennbar
am Tag `throwback_wall`).

## Admin-Befehle (`smpstats.admin`)

| Befehl | Wirkung |
|---|---|
| `/throwback admin edit` | Bearbeitungsmodus starten (auch mit `smpstats.throwback.build`), siehe unten |
| `/throwback admin edit save`, `cancel` | Entwurf speichern (nur mit allen Pflichtteilen) oder verwerfen |
| `/throwback admin edit text <text>` | legt den Text fest, den das Text-Werkzeug als Nächstes setzt (MiniMessage, höchstens 256 Zeichen, `<br>` für eine neue Zeile) |
| `/throwback admin build` | erzeugt einen schlichten Platzhalter-Gang mit 12 Bühnen, Regeltafel und Portal und speichert das Layout |
| `/throwback admin tp` | teleportiert in die Halle (zum Bauen und Einmessen) |
| `/throwback admin setstart`, `setrules`, `setstage <1-12>`, `portal1`, `portal2` | Plätze der echten Halle einmessen; `setrules` hängt die Tafel zwei Blöcke vor den Admin in Augenhöhe |
| `/throwback admin reload` | Config und Texte neu laden |
| `/throwback admin reset <spieler>` | Fortschritt löschen: wieder Pflicht-Rundgang |
| `/throwback admin skip <spieler>` | Pflicht-Rundgang als erledigt markieren |
| `/throwback admin preview <name>` | zeigt, welche Szenen ein Spieler der Saison bekäme |
| `/throwback admin info` | Zustand und Layout (Start, Regeltafel, Portal, Bühnen, Texte) |

Die Einmess-Befehle bleiben neben dem Bearbeitungsmodus bestehen und arbeiten direkt auf dem gespeicherten Layout.

Bau-Steckbrief für die echte Halle: Ankunftsplattform, Gang mindestens 3 Blöcke breit, 12 Bühnen mit einer
Standfläche von 3 Blöcken Tiefe und 4 Blöcken Breite plus 1 bis 2 Blöcken Luft zu den Seiten, nach hinten und nach oben,
damit die Hologramme (bis 6,5 Blöcke breit) nicht in die Wand ragen (6 links, 6 rechts, zum Gang offen), optional
flache Wandflächen für Bilder (ImageFrame, Größe frei), Regeltafel etwa 5×3, dekoratives Portal am Gangende. Die
Blickrichtung (`yaw`) einer Bühne zeigt auf die Rückwand der Bühne. Regel- und Datenschutz-Tafel sind im Layout
untere Mitte und Blickrichtung der Tafel (`layout.rules`, `layout.privacy`); der Knopf "Regeln gelesen" sitzt direkt
unter der Regeltafel. Hat das Layout keinen Platz für die Datenschutz-Tafel, hängt sie rechts neben der Regeltafel.
Freie Texte stehen unter `layout.texts` (Position, Blickrichtung, MiniMessage-Text) und erscheinen im Rundgang für
jeden Spieler.

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
5. `edit save` oder das Speichern-Werkzeug übernimmt den Entwurf als Layout (`config.yml`) und beendet die Sitzung.
6. Läuft `/throwback admin edit` in der Sitzung nochmal, prüft es alle Boxen neu. Das ist nötig, wenn Blöcke mit
   WorldEdit oder Befehlen geändert wurden: Nur Setzen und Abbauen des Builders selbst werden bemerkt.

### Werkzeuge (Leiste, von links)

Erkannt werden sie nur an einem Tag im Item, nie an Name oder Material.

| Werkzeug | Rechtsklick auf einen Block |
|---|---|
| Start (Bergungskompass) | Startpunkt auf der Blockoberseite, Blickrichtung des Builders. Bewusst kein normaler Kompass: Der ist in WorldEdit der Navigationsstab und teleportiert bei jedem Klick durch Wände |
| Bühne (Rüstungsständer) | Figurenplatz, Blickrichtung des Builders zeigt zur Rückwand. Klick auf den hintersten Boden-Block der Standfläche; quer dazu rastet der Punkt auf die Blockkante nächst dem Klickpunkt ein, weil 4 Blöcke keine Mitte haben. Die nächste freie Nummer (höchstens 12) |
| Regeltafel (Papier) | Die Tafel hängt genau an der angeklickten Stelle und schaut zum Builder (Blickrichtung + 180°), der Knopf "Regeln gelesen" direkt darunter. Papier statt beschreibbarem Buch, weil das Buch beim Klick seinen Editor öffnet |
| Datenschutz-Tafel (Buch) | eigener Platz der Tafel, sie schaut zum Builder. Ohne eigenen Platz hängt sie rechts neben der Regeltafel |
| Portal-Ecke 1, 2 (Enderperle, Enderauge) | die beiden Ecken des Portal-Bereichs; durchlässige Blöcke zählen selbst, sonst der Block an der angeklickten Seite |
| Text (Namensschild) | hängt den mit `/throwback admin edit text <text>` festgelegten Text wie eine Tafel an die angeklickte Stelle, zum Beispiel als Titel über einem ImageFrame-Bild. Ohne festgelegten Text kommt ein Hinweis |
| Rückgängig (roter Farbstoff) | entfernt die zuletzt gesetzte Markierung (auch ohne Block, in die Luft klicken) |
| Speichern (grüner Farbstoff) | wie `edit save` |

Die Blickrichtung wird auf 90° gerundet. Ein Linksklick mit einem Werkzeug entfernt dessen Markierung, auf deren Box
oder Tafel der Builder schaut (Blickstrahl bis 8 Blöcke, bis zum angeklickten Block). Findet der Strahl nichts, gilt
die nächstgelegene Markierung innerhalb von 2 Blöcken um den angeklickten Block; der Block selbst bleibt heil. Wird
eine Bühne entfernt, rücken die folgenden nach (die Nummer ist die Reihenfolge). Singletons (Start, Regel-,
Datenschutz-Tafel, Portal-Ecken) ersetzt ein neues Setzen, Texte sind beliebig viele.

Gesperrt sind Droppen, Verschieben, Ziehen, Hotbar-Tasten, Tausch in die Nebenhand, Benutzen an Entities, Abbauen mit
einem Werkzeug in der Hand, Öffnen von Containern, und beim Tod fallen keine Werkzeuge. Da die Leiste alle 9 Hotbar-Plätze
belegt, wird in der Sitzung nicht mit Blöcken gebaut; dafür den Modus verlassen (`quit` behält den Entwurf) oder
WorldEdit nutzen.

### Boxen und Vorschau

Jede Markierung zeigt ihren Platzbedarf als Box aus dünnen Linien (Block-Displays, nur für den Builder sichtbar, nicht
persistent; Tour-Spieler sehen nichts). Grün ist frei, rot heißt: feste Blöcke ragen hinein, Hellblau ist die
Standfläche einer Bühne. Was noch fehlt, steht als Checkliste in der Sidebar des Builders (dessen Scoreboard
wird beim Beenden zurückgegeben).

| Element | Box |
|---|---|
| Start | Säule 1×1×2 |
| Regel-/Datenschutz-Tafel | Fläche der echten Tafel (Breite und Höhe nach Zeilen der Texte, Maße in `PanelMetrics`); bei der Regeltafel zusätzlich Platz für den Knopf darunter |
| Portal | Quader zwischen den Ecken |
| Text | Fläche des Textes, Höhe nach seinen Zeilen |

Ob etwas rot ist, wird beim Setzen und Ändern sowie bei Setzen/Abbauen des Builders im Bereich berechnet und
gespeichert. Als fest gilt jedes Material mit `Material#isSolid`, Laternen und Zäune zählen also mit. Rot ist nur
eine Warnung: Beim Speichern erscheint sie als Hinweis.

Regel- und Datenschutz-Tafel und Texte erscheinen sofort als echte Tafel (derselbe Renderer und dieselben Texte wie in
der Tour, nur für den Builder, nicht gespeichert, am Ende entfernt).

### Pflichtteile

Gespeichert wird nur mit: 1× Start, 1× Regeltafel, Datenschutz-Tafel (zählt auch ohne eigenen Platz, dann hängt sie
neben der Regeltafel), beide Portal-Ecken und mindestens `throwback.edit.min_stages` (Standard 8) bis höchstens 12
Bühnen. Texte sind optional. Fehlt etwas, zeigt die Fehlermeldung die Checkliste (zum Beispiel "Bühnen 6/8 ✗").
Dieselbe Checkliste steht während der Sitzung in der Sidebar.

## Last und BlueMap

- Die Halle-Welt ist nur geladen, solange jemand drin ist (Entladen nach `unload_delay_seconds`). Mobs, Wetter,
  Tageszeit und Schaden sind aus.
- Pro Spieler entstehen rund 100 Entities, verteilt auf 14 Ticks. Der Gang pollt nur, wenn jemand in der Halle
  ist (alle 10 Ticks). Der Spawn-Eingang prüft alle 10 Ticks nur die Spieler seiner Welt.
- **BlueMap:** Die Hallen-Welt gehört nicht auf die BlueMap. In der BlueMap-Konfiguration keine Map für
  `throwback_hall` anlegen (BlueMap lädt Welten nur, wenn dafür eine Map-Datei existiert). Die Halle wird
  nach dem Entladen und Neuladen nicht von SMPStats gerendert.
