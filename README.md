# SMPStats – Paper 1.21.x Player Stats & Moments

SMPStats is a lightweight but feature-rich stats plugin for Paper **1.21.1+**.  
It automatically tracks player activity, exposes data via in-game commands and an optional HTTP API, and records “moments” (diamonds, clutches, deaths, etc.).

## ✨ Features
- Auto-tracking: playtime, joins/quits, deaths (cause), player/mob kills, blocks placed/broken, distance (per dimension), biomes, damage dealt/taken, crafting, item consumption, first/last join.
- Skill profiles (mining/combat/exploration/builder/farmer) with configurable weights and display in `/stats`.
- Moments engine: configurable triggers (block_break/death/death_fall/first_death/damage_low_hp/death_explosion/item_gain/boss_kill) with merge windows (e.g., diamond runs), SSE/REST feed.
- Heatmaps: chunk bins for mining/deaths + hotspot counters (configurable regions).
- Social stats: time spent nearby (pair counters) + shared kills near each other.
- Timeline snapshots: daily aggregates per player, range deltas + leaderboards.
- Server health index: counts chunks/entities/hoppers/redstone with cost score + HTTP endpoint.
- Story generator: weekly JSON summary (top players/social + recent moments) with optional webhook.
- Death Replay Lite: store death snapshots (cause, position, health, nearby entities, inventory contents).
- Optional HTTP API with API key.

## 💬 Commands
| Command | Description |
| --- | --- |
| `/stats` | Show your stats |
| `/stats <player>` | Show another player's stats |
| `/stats json` | Show your stats as JSON |
| `/stats dump` | Dump all stats to console (JSON) |
| `/sstats` (alias `/smpstats`, `/SStats`) | Info (version/API/flags) |
| `/sstats reload` | Reload config, restart API (perm `smpstats.reload`) |
| `/sstats user <name>` | Show stats for player |
| `/sstats user <name> reset` | Reset stats (perm `smpstats.edit`) |
| `/sstats user <name> set <stat> <value>` | Set a stat (tab-complete; perm `smpstats.edit`) |

## 🌐 HTTP API (if enabled)
Auth: `X-API-Key: <key>`
- `GET /stats/<uuid>` – player stats JSON
- `GET /stats/all` – all stats JSON
- `GET /online` – online player names
- `GET /moments/recent?limit=&since=` – moments list
- `GET /moments/query?player=&type=&since=&limit=` – filtered moments
- `GET /moments/stream?since=&limit=` – SSE feed of moments
- `GET /heatmap/<type>` – heatmap bins (e.g., MINING/DEATH)
- `GET /heatmap/hotspots/<type>` – hotspot counters
- `GET /timeline/<uuid>?limit=` – daily timeline entries
- `GET /timeline/range/<uuid>?days=` – delta stats for a period (weekly/monthly)
- `GET /timeline/leaderboard?days=&limit=` – per-period leaderboard (playtime-first)
- `GET /social/top?limit=` – top nearby pairs
- `GET /death/replay?limit=` – death replay entries
- `GET /health` – latest server health snapshot (chunks/entities/hoppers/redstone + costIndex)

## 💾 Storage
Uses local **SQLite**. Schema auto-migrates and blocks downgrades.

## 🚀 Install
1) `mvn clean package`  
2) Copy `target/SMPStats.jar` to `plugins/`  
3) Start server → `config.yml` & DB auto-created

## 🛠 Config (excerpt)
```yaml
api:
  enabled: true
  bind_address: "0.0.0.0"
  port: 8765
  api_key: "ChangeMe"
tracking:
  movement: true
  blocks: true
  kills: true
  biomes: true
  crafting: true
  damage: true
  consumption: true
moments:
  enabled: true
  flush_seconds: 10
heatmap:
  enabled: true
social:
  enabled: true
  nearby_radius: 16
timeline:
  enabled: true
death_replay:
  enabled: true
health:
  enabled: true
story:
  enabled: true
```

## 🧱 Build & Develop
- Java 21, Maven
- Structure: `src/main/java/de/nurrobin/smpstats/...`, resources in `src/main/resources/`

## 📌 Permissions
- `smpstats.use` (default: true) – use `/sstats` and `/smpstats`
- `smpstats.reload` (default: op) – reload config/API
- `smpstats.edit` (default: op) – reset/set player stats

## 📜 Roadmap
See `Roadmap.md` for feature status and next steps.
