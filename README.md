# SMPStats – Paper 1.21.x Player Statistics Plugin

SMPStats ist ein leichtgewichtiges aber mächtiges Statistik-Plugin für Paper-Server  
(optimiert für Minecraft **1.21.1+**).  
Es verfolgt automatisch Spieleraktivitäten und stellt sie über Ingame-Commands und
optional über eine **HTTP-API** zur Verfügung.

## ✨ Features

### 🎮 Spieler-Tracking (automatisch)
- Spielzeit (Sessions, Pausen, Join/Leave)
- Tode (inkl. Todesursache)
- Kills (Player + Monster)
- Platzierte Blöcke
- Abgebaute Blöcke
- Zurückgelegte Distanz (Overworld / Nether / End getrennt)
- Besuchte Biome
- Damage dealt / damage taken
- Crafting / Konsumierte Items
- Erstes & letztes Join-Datum

### 💬 Commands
| Command | Beschreibung |
|--------|--------------|
| `/stats` | Eigene Statistiken anzeigen |
| `/stats <player>` | Statistiken eines anderen Spielers anzeigen |
| `/stats json` | Eigene Stats als JSON im Chat (für Debug) |
| `/stats dump` | Alle Stats als JSON in die Konsole schreiben |

### 🌐 HTTP API (optional)
Falls aktiviert, stellt das Plugin einen kleinen HTTP-Server bereit.

**Endpoints:**
- `GET /stats/<uuid>` – JSON-Stats eines Spielers  
- `GET /stats/all` – JSON-Liste aller Spieler  
- `GET /online` – Liste aller aktuell verbundenen Spieler

Authentifizierung über `X-API-Key: <key>`.

### 💾 Speicherung
Das Plugin nutzt lokal **SQLite**, ideal für SMPs — keine Einrichtung nötig.

---

## 🚀 Installation

1. Repo klonen  
2. Plugin bauen:
```bash
   mvn clean package
```

3. Die Datei `SMPStats.jar` aus `target/` in den `plugins/`-Ordner werfen
4. Server starten → Config & DB werden automatisch erstellt

---

## 🛠 Konfiguration

Die Datei `config.yml` wird beim ersten Start erstellt.

**Beispiel:**

```yaml
api:
  enabled: true
  port: 8765
  api_key: "CHANGEME123"

tracking:
  movement: true
  blocks: true
  kills: true
  biomes: true
```

---

## 🧩 API Beispiel

```bash
curl -H "X-API-Key: CHANGEME123" http://localhost:8765/stats/uuid
```

---

## 🧱 Build & Development

Sprache: **Java 21**
Build Tool: **Maven**
IDE: **IntelliJ IDEA Ultimate oder Community**
Server: **Paper 1.21.x**

Ordnerstruktur:

```
/src
 └── main
     ├── java
     │    └── de.nurrobin.smpstats
     │          ├── SMPStats.java
     │          ├── database/
     │          ├── listeners/
     │          ├── commands/
     │          └── api/
     └── resources
           ├── plugin.yml
           └── config.yml
```

---

## 📌 Permissions

Standardmäßig keine — jeder darf `/stats` nutzen.
Optional in Zukunft über Permission-Nodes regelbar.

---

## 🧪 Roadmap / Ideen

* GUI-basierte Stat Pages (eigenes Inventory)
* Export als Web-Dashboard
* Monthly Recap / Year Wrapped
* Comparison Stats (Spieler vergleichen)
* Scoreboard Integration
* Leaderboards (Kills, Playtime, Distance…)

---

## 📝 Lizenz

MIT — frei nutzbar, anpassbar, erweiterbar.