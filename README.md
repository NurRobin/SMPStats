# SMPStats – Minecraft Analytics Engine

<p align="center">
  <strong>The first fully dynamic, query-driven analytics system for Minecraft servers</strong>
</p>

SMPStats evolves from a player statistics plugin into a comprehensive **Minecraft Analytics Engine** — inspired by Google Analytics but purpose-built for Minecraft. Store raw event data efficiently and gain on-demand aggregated insights: heatmaps, temporal trends, social dynamics, skill patterns, resource consumption, zone activity, and automated event detection.

---

## 🎯 Vision

> Transform your Minecraft server into a data-driven experience. Understand player behavior, optimize gameplay, and discover hidden patterns — all without sacrificing performance.

**Core Capabilities:**
- **Server-wide analytics** with per-player drill-down
- **Trend analysis** over arbitrary time ranges
- **Dynamic heatmaps** generated at query time with configurable grid sizes
- **Social graph clustering** — discover who plays with whom
- **Rolling-window event detection** — capture meaningful gameplay moments automatically
- **Efficient data storage** with SQLite (PostgreSQL/Elasticsearch planned)
- **REST API** for external dashboards and visualization tools

---

## ✅ Milestone 1 — Foundations (Completed)

SMPStats has achieved its first major milestone: a complete event-driven statistics tracking system with baseline listeners, local storage, and an early "moments" logic.

### Current Features

#### 📊 Tracked Metrics
| Category | Metrics |
|----------|---------|
| **Activity** | Playtime, joins/quits, first/last join timestamps |
| **Combat** | Player kills, mob kills, damage dealt/taken, death count & causes |
| **Exploration** | Distance traveled (per dimension), biomes visited |
| **Building** | Blocks placed, blocks broken |
| **Economy** | Items crafted, items consumed |
| **Skills** | Mining, Combat, Exploration, Builder, Farmer scores (configurable weights) |

#### 🔔 Moments Engine
Automatically detect and record significant gameplay events:
- **Triggers:** `block_break`, `death`, `death_fall`, `first_death`, `damage_low_hp`, `death_explosion`, `item_gain`, `boss_kill`
- **Merge Windows:** Combine rapid events (e.g., a "diamond run" merges consecutive diamond ore breaks within 30 seconds)
- **Configurable:** Define custom moments via YAML with filters (materials, entity types, damage causes)

#### 🗺️ Heatmaps & Hotspots
- **Mining Heatmap:** Track block break locations at chunk resolution
- **Death Heatmap:** Visualize dangerous areas
- **Hotspot Regions:** Define named areas (e.g., "Spawn") and track aggregate activity

#### 🤝 Social Statistics
- **Proximity Tracking:** Measure time players spend near each other
- **Shared Activity:** Track kills performed while teammates are nearby
- **Pair Leaderboards:** Discover the strongest player partnerships

#### 📈 Timeline & Trends
- **Daily Snapshots:** Per-player cumulative stats captured daily
- **Range Deltas:** Compare activity over 7-day, 30-day, or custom periods
- **Leaderboards:** Rank players by activity within time windows

#### 🖥️ Server Health
- **Entity Counting:** Loaded chunks, entities, hoppers, redstone blocks
- **Cost Index:** Weighted score (0-100) indicating server load
- **Per-World Breakdown:** Identify which world is causing lag

#### 📖 Story Generator
- Weekly JSON summaries with top players and recent moments
- Optional webhook integration for Discord bots or external dashboards

#### 💀 Death Replay
- Capture death context: cause, position, health, fall distance
- Record nearby players and mobs
- Optionally store inventory contents

#### 🌐 HTTP API
Full REST API with API key authentication:

| Endpoint | Description |
|----------|-------------|
| `GET /stats/{uuid}` | Player statistics |
| `GET /stats/all` | All player statistics |
| `GET /online` | Online player names |
| `GET /moments/recent` | Recent moments |
| `GET /moments/query` | Filtered moment search |
| `GET /moments/stream` | SSE feed for real-time moments |
| `GET /heatmap/{type}` | Heatmap bins (MINING, DEATH) |
| `GET /heatmap/hotspots/{type}` | Hotspot counters |
| `GET /timeline/{uuid}` | Player timeline |
| `GET /timeline/range/{uuid}` | Delta stats for a period |
| `GET /timeline/leaderboard` | Period-based leaderboard |
| `GET /social/top` | Top player pairs by time together |
| `GET /death/replay` | Death replay entries |
| `GET /health` | Server health snapshot |

See [docs/API.md](docs/API.md) for full documentation.

### Storage Model (SQLite)
- **`stats`** — Core player statistics with JSON biomes array
- **`moments`** — Event records with type, payload, and location
- **`heatmap`** — Chunk-based activity bins
- **`social_pairs`** — Player proximity counters
- **`timeline`** — Daily stat snapshots
- **`death_replay`** — Death context records

Schema auto-migrates on startup; downgrades are blocked for safety.

### Known Limitations
- Heatmaps are chunk-resolution only (no sub-chunk grids yet)
- No exponential decay on heatmap data
- Social tracking limited to pairs (no group clustering)
- No external database backends (SQLite only)
- Event detection is trigger-based, not rolling-window

---

## 🚀 Quick Start

### Installation
```bash
# Build the plugin
mvn clean package

# Copy to your server
cp target/SMPStats.jar /path/to/plugins/

# Start your Paper 1.21.1+ server
# config.yml and stats.db are auto-created
```

### Configuration
```yaml
api:
  enabled: true
  bind_address: "127.0.0.1"
  port: 8765
  api_key: "YouShouldChangeThisKey"

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
  definitions:
    diamond_run:
      type: block_break
      title: "Diamond Run"
      detail: "Diamonds found: {count}"
      merge_seconds: 30
      materials: [DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE]

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

### Commands
| Command | Description |
|---------|-------------|
| `/stats` | Show your stats |
| `/stats <player>` | Show another player's stats |
| `/stats json` | Export your stats as JSON |
| `/stats dump` | Dump all stats to console (JSON) |
| `/sstats` | Plugin info (version, API status) |
| `/sstats reload` | Reload configuration |
| `/sstats user <name>` | View/edit player stats |

### Permissions
| Permission | Default | Description |
|------------|---------|-------------|
| `smpstats.use` | true | Use `/stats` and `/sstats` |
| `smpstats.reload` | op | Reload configuration |
| `smpstats.edit` | op | Reset/modify player stats |

---

## 🗺️ Roadmap: The Analytics Engine

See [Roadmap.md](Roadmap.md) for detailed milestone breakdowns.

### Milestone 2 — Analytics Layer
Dynamic aggregation engine with exponential decay, configurable grid sizes, and multi-layer heatmap queries.

### Milestone 3 — Social Dynamics
Advanced player-proximity tracking, automatic group detection via clustering, and interaction matrices.

### Milestone 4 — Event Engine
Full JSON-based event definitions with rolling windows, multi-step conditions, and timeout buffers.

### Milestone 5 — Storage & Performance
Efficient raw-event storage, compression strategies, sharded tables, and optional external backends.

### Milestone 6 — Visualization Platform
External web dashboard with world heatmaps, live trend monitors, player timelines, and social graph visualization.

---

## 💡 Use Case Examples

### 🧱 Dynamic Heatmap Query
```
GET /heatmap?type=block_break&block=diamond_ore&from=3d&grid=16&decay=0.95h
```
- Filter by block type and time range
- Aggregate to custom grid sizes (16×16 blocks)
- Apply exponential decay (recent activity weighted higher)
- Render on-demand for dashboards

### 🔁 Rolling-Window Event Detection
```json
{
  "id": "diamond_vein",
  "match": { "type": "block_break", "block": "diamond_ore" },
  "window_reset_seconds": 30,
  "finalize_on_timeout": true,
  "min_events": 2
}
```
> When a player breaks diamond ore, a 30-second timer starts. Each new diamond break resets the timer. When the timer expires, the streak finalizes as an event: "Robin mined 12 diamonds near x=114 z=-62".

### 🤝 Social Proximity Analysis
> Players within 10 blocks for 120+ seconds are recorded as "interaction partners." Over time, clusters emerge revealing social groups. Visualize group hangout zones on heatmaps.

### ⚒️ Resource Trend Dashboard
> "What are players farming right now?"
> Query mining activity over the last 1h, 6h, 24h, or 7d. Identify shifts in resource focus and popular farming locations.

---

## 🔧 Development

### Requirements
- Java 21
- Maven 3.8+
- Paper API 1.21.x

### Project Structure
```
src/main/java/de/nurrobin/smpstats/
├── SMPStats.java          # Plugin entry point
├── Settings.java          # Configuration wrapper
├── StatsRecord.java       # Player stats model
├── StatsService.java      # Stats business logic
├── api/                   # HTTP API server
├── commands/              # Command handlers
├── database/              # SQLite storage
├── heatmap/               # Heatmap service
├── health/                # Server health tracking
├── listeners/             # Bukkit event listeners
├── moments/               # Moments engine
├── skills/                # Skill profile calculation
├── social/                # Social proximity tracking
├── story/                 # Weekly summary generator
└── timeline/              # Timeline snapshots
```

### Building
```bash
mvn clean package
# Output: target/SMPStats.jar
```

### Testing
```bash
mvn test
# Uses MockBukkit for integration testing
```

---

## 🔒 Security & Verification

All releases include cryptographic verification:

| Artifact | Verification |
|----------|--------------|
| **SHA256 Checksums** | `sha256sum -c SMPStats-vX.Y.Z.jar.sha256` |
| **GPG Signatures** | `gpg --verify SMPStats-vX.Y.Z.jar.asc SMPStats-vX.Y.Z.jar` |
| **Build Provenance** | `gh attestation verify SMPStats-vX.Y.Z.jar --repo NurRobin/SMPStats` |
| **SBOM** | Software Bill of Materials (`*.sbom.json`) |

---

## 📦 Releases

**Automated Release System:**
1. Set version: `./scripts/set-version.sh X.Y.Z`
2. Commit & push to `main`
3. Auto-release creates draft with all artifacts
4. Publish draft — done! 🚀

- **Stable releases:** `vX.Y.Z`
- **Pre-releases:** `vX.Y.Z-beta.N`

See [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md) for details.

---

## 📜 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 🤝 Contributing

Contributions are welcome! Please read the issue guidelines and submit PRs against the `main` branch. See [Roadmap.md](Roadmap.md) for planned features and areas where help is needed.
