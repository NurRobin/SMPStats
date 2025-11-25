# SMPStats Roadmap

Status-Legende: `Todo` | `In Progress` | `Done`  
Priorität: `High` | `Medium` | `Low`  
Größe: `S` (klein) | `M` (mittel) | `L` (groß) | `XL` (sehr groß)

## Aktueller Stand
- ✅ Baseline Stats-Tracking, SQLite, /stats-Command, HTTP-API, Autosave, Reload-Command (Status: Done)

## Fokus-Features
| Feature | Priorität | Größe | Status | Notizen |
| --- | --- | --- | --- | --- |
| Skill-Profile (Mining/Combat/Exploration/Builder/Farmer) | High | M | Done | Gewichte per Config, Anzeige in /stats, Basis-Berechnung aktiv |
| Moments Engine (Firsts/Clutch/Fail, Merge-Window) | High | L | In Progress | Benutzerdefinierbare Definitions (block_break/death/death_fall/first_death/damage_low_hp/death_explosion) + Merge, Persistenz; Feed fehlt noch |
| Heatmaps / Activity Maps (Tode/Mining/Hotspots) | High | L | In Progress | Mining/Death-Bins + Flush + API; noch: Hotspots/mehr Typen |
| Social Stats (Nähe-Zeit, gemeinsame Kills) | Medium | M | Todo | Sampler jede n Sekunden, Paar-Counter, Leaderboards |
| Server Health / Cost Index | Medium | M | Todo | Chunks/Entities/Hopper/Redstone Counters, Monitoring-Fokus |
| Season/Timeline Layer (daily/weekly/monthly) | Medium | M | Todo | Season-Key, Zeiträume, Leaderboards pro Range |
| Live Feed (WS/SSE) für Moments | Medium | L | Todo | Stream von Moments, Throttle/Dedupe, Auth |
| Death Replay Lite | Medium | M | Todo | Snapshot bei Tod (Ort, Ursache, Health, Nearby, Value) |
| Story Generator Hook (Weekly Summary) | Low | M | Todo | JSON-Summary + optional LLM-Hook |

## Unterstützende Tasks
- Config-Erweiterungen & Docs (Weights, Windows, Regions, Season) – Todo
- API-Endpunkte für neue Features – Todo
- Tests/Validation (DB migrations, config parsing) – Todo

## Nächste Schritte (konkret)
- Moments: Feed/SSE + weitere Presets (MLG, Wither, Netherite, Advancements), Dedupe über DB für Firsts.
- Heatmap: benannte Hotspot-Regionen + Filter pro Welt/Typ, optional Decay.
- Social Stats: Nähe-Sampling + gemeinsame Kills, API/Leaderboards.
- Tests: Integration mit MockBukkit für Commands/API; Validierung Moments-Parser mit mehr Typen.
