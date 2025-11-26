# SMPStats Roadmap

> Transforming from a player statistics plugin into a comprehensive **Minecraft Analytics Engine**

---

## Design Principles

- **Lightweight but Expressive:** Powerful analytics without sacrificing server performance or configuration complexity
- **Storage Efficiency:** Track everything relevant without ballooning to 50GB — smart compression and decay
- **Unique Insights:** Go beyond common stats to discover social dynamics, streaks, and behavioral patterns
- **Configurable Everything:** Events, triggers, and analytics should be definable without code changes

---

## Milestone History

### ✅ Milestone 1 — Foundations (Completed)

> A complete event-driven stat tracking system with baseline listeners, local storage, and early "moments" logic.

| Feature | Status | Notes |
|---------|--------|-------|
| Core Stats Tracking | ✅ Done | Playtime, kills, deaths, blocks, distance, biomes, crafting, damage, consumption |
| Skill Profiles | ✅ Done | Mining, Combat, Exploration, Builder, Farmer with configurable weights |
| Moments Engine | ✅ Done | 8 trigger types, merge windows, YAML configuration |
| Heatmaps | ✅ Done | Mining/Death bins at chunk resolution, hotspot regions |
| Social Stats | ✅ Done | Proximity tracking, pair counters, shared kills |
| Timeline | ✅ Done | Daily snapshots, range deltas, leaderboards |
| Server Health | ✅ Done | Chunk/entity/hopper/redstone counting, cost index |
| Death Replay | ✅ Done | Cause, position, nearby entities, inventory |
| Story Generator | ✅ Done | Weekly summaries, webhook integration |
| HTTP API | ✅ Done | 14 endpoints with API key auth |
| SQLite Storage | ✅ Done | Schema migrations, WAL mode |

---

## Future Milestones

### 🔄 Milestone 2 — Analytics Layer

> Dynamic aggregation engine with exponential decay, configurable grid sizes, and multi-layer heatmap queries.

| Feature | Priority | Size | Status | Description |
|---------|----------|------|--------|-------------|
| Exponential Decay Model | High | M | Todo | `weight = exp(-Δt / τ)` for time-weighted aggregation |
| Configurable Grid Sizes | High | M | Todo | Chunk (16×16), 8×8, 32×32, 64×64 cell options |
| Multi-Layer Heatmaps | High | L | Todo | Movement, mining, deaths, damage as separate queryable layers |
| Dynamic Query Engine | High | L | Todo | On-demand aggregation at query time |
| Movement Heatmaps | Medium | M | Todo | Player presence and travel patterns |
| Damage Heatmaps | Medium | M | Todo | PvP hotspots, mob damage zones |
| Resource-Specific Heatmaps | Medium | M | Todo | Diamond, iron, gold mining patterns |
| Biome-Filtered Views | Low | S | Todo | Filter any heatmap by biome type |
| Time-Range Filters | Medium | M | Todo | `from=3d`, `from=1w`, arbitrary ranges |
| In-Game GUI (Chest Menu) | High | L | Todo | Rich chest-menu style interface with visual insights |
| Live Activity Dashboard | Medium | M | Todo | "Who's farming what right now" real-time view |
| Player Comparison Views | Medium | M | Todo | Side-by-side stat comparisons in GUI |

**Technical Details:**
```
Decay Formula: weight = exp(-Δt / τ)
  - Δt = time since event (seconds)
  - τ = decay constant (configurable, e.g., 3600 for 1-hour half-life)
  
Grid Aggregation:
  - Store raw events with precise coordinates
  - Aggregate at query time to requested grid size
  - Cache hot queries for performance
```

---

### 🤝 Milestone 3 — Social Dynamics

> Advanced player-proximity tracking, automatic group detection, and social graph analysis. Understand how groups form on the server and which players are active together.

| Feature | Priority | Size | Status | Description |
|---------|----------|------|--------|-------------|
| Enhanced Proximity Tracking | High | M | Todo | 10-block radius, 120+ second threshold |
| Automatic Group Detection | High | L | Todo | Clustering algorithm for 3+ player groups |
| Group Formation Tracking | High | M | Todo | Track how groups form, grow, and dissolve over time |
| Interaction Matrices | Medium | M | Todo | N×N player interaction strength grid |
| Social Graph Export | Medium | M | Todo | JSON/GraphML for visualization tools |
| "Who Plays With Whom" API | High | M | Todo | Query player relationships and groups |
| Active Together Detection | Medium | M | Todo | Identify which players are consistently online together |
| Time-Filtered Social Data | Medium | S | Todo | "Last 6h", "past week", "this month" |
| Group Hangout Zones | Medium | M | Todo | Heatmaps of where groups spend time |
| Party Session Detection | Low | L | Todo | Automatically detect coordinated play sessions |

**Algorithm Concept:**
```
Proximity Detection:
  1. Sample player positions every N seconds
  2. Build distance matrix for all online players
  3. Record pairs within threshold distance
  4. Accumulate time → "interaction score"

Group Clustering:
  - Union-Find for connected components
  - Minimum spanning tree for group boundaries
  - Threshold: shared time > X hours over period Y
```

---

### ⚡ Milestone 4 — Event Engine

> Full JSON-based event definitions with rolling windows, multi-step conditions, and timeout buffers.

| Feature | Priority | Size | Status | Description |
|---------|----------|------|--------|-------------|
| JSON Event Definitions | High | L | Todo | Replace YAML with richer JSON schema |
| Rolling Window Events | High | L | Todo | Timer resets on each matching event |
| Timeout-Based Finalization | High | M | Todo | Complete event when window expires |
| Multi-Step Conditions | Medium | L | Todo | Sequence of conditions before trigger |
| Event Categories | Medium | S | Todo | Loot, Combat, Exploration, Accident |
| Event Notifications | Medium | M | Todo | In-game messages, sounds, titles |
| Event API Exposure | Medium | M | Todo | WebSocket/SSE for live event stream |
| Complex Triggers | Low | L | Todo | Compound AND/OR/NOT conditions |

**Example: Diamond Vein Event**
```json
{
  "id": "diamond_vein",
  "description": "Tracks diamond mining streaks",
  "match": {
    "type": "block_break",
    "block": "diamond_ore",
    "include_deepslate": true
  },
  "window_reset_seconds": 30,
  "finalize_on_timeout": true,
  "min_events": 2,
  "notification": {
    "title": "{player} found a vein!",
    "message": "Mined {count} diamonds near ({x}, {z})",
    "sound": "ENTITY_PLAYER_LEVELUP"
  }
}
```

**Rolling Window Logic:**
```
1. Player breaks diamond ore → start 30s timer
2. Player breaks another diamond → reset timer to 30s
3. Player breaks another diamond → reset timer to 30s
4. 30 seconds pass with no diamonds → finalize event
5. Record: "Robin mined 12 diamonds near x=114 z=-62"
```

---

### 🚀 Milestone 5 — Storage & Performance

> Efficient raw-event storage, compression, sharding, and optional external database backends.

| Feature | Priority | Size | Status | Description |
|---------|----------|------|--------|-------------|
| Raw Event Storage | High | L | Todo | Store every event for replay/analysis |
| Binary Encoding | Medium | L | Todo | Compact event serialization |
| Columnar Storage | Medium | L | Todo | Optimized for analytical queries |
| Time-Based Sharding | Medium | L | Todo | Per-month or per-week tables |
| World-Based Sharding | Medium | M | Todo | Separate tables per world |
| Data Retention Policies | Medium | M | Todo | Auto-archive old data |
| PostgreSQL Backend | Low | XL | Todo | Optional external database |
| Elasticsearch Backend | Low | XL | Todo | For high-load servers |
| Query Optimization | Medium | L | Todo | Index tuning, query plans |
| Async Write Pipeline | High | M | Todo | Non-blocking event storage |

**Storage Strategy:**
```
Raw Events Table:
  id, timestamp, player_uuid, event_type, world, x, y, z, payload_blob

Sharding:
  events_2024_01, events_2024_02, ...
  OR events_world_overworld, events_world_nether, ...

Compression:
  - Varint encoding for coordinates
  - Dictionary encoding for common strings
  - Delta encoding for timestamps
```

---

### 🎨 Milestone 6 — Visualization Platform (Optional External Project)

> Web dashboard with world heatmaps, live monitors, and social graph visualization.

| Feature | Priority | Size | Status | Description |
|---------|----------|------|--------|-------------|
| Web Dashboard | Medium | XL | Todo | Standalone web application |
| World Heatmap Tiles | Medium | XL | Todo | Leaflet/MapLibre integration |
| Dynmap Integration | Medium | L | Todo | Overlay on existing Dynmap |
| Live Trend Monitors | Medium | L | Todo | Real-time activity graphs |
| Player Timeline View | Medium | M | Todo | Individual player activity history |
| Social Graph Visualization | Medium | L | Todo | Interactive network diagram |
| Resource Dashboard | Low | M | Todo | Mining trends, farming patterns |
| Leaderboard Pages | Low | S | Todo | Web-based ranking displays |
| Custom Report Builder | Low | XL | Todo | Drag-and-drop analytics |

**Tech Stack Suggestions:**
```
Frontend: React/Vue + Leaflet/MapLibre
Backend: Express/FastAPI (optional, can query plugin API directly)
Real-time: WebSocket connection to plugin SSE
Maps: Pre-rendered tiles or dynamic canvas rendering
```

---

## Technical Explanations

### Exponential Decay

Decay makes recent events more significant than old ones. The formula:

```
weight = exp(-Δt / τ)
```

Where:
- `Δt` = time since event occurred (in seconds)
- `τ` = decay constant (half-life parameter)

**Example with τ = 3600 (1 hour half-life):**
| Time Ago | Weight |
|----------|--------|
| 0 min | 1.00 |
| 30 min | 0.61 |
| 1 hour | 0.37 |
| 2 hours | 0.14 |
| 6 hours | 0.0025 |

This enables "hot right now" queries without losing historical data.

### Rolling Windows

Unlike simple triggers that fire once per event, rolling windows aggregate related events:

1. First matching event starts the window
2. Each subsequent match resets the expiration timer
3. Window closes when timer expires with no new matches
4. Final event captures aggregate data (count, positions, duration)

**Use Cases:**
- Mining streaks (diamond, iron veins)
- Combat sessions (PvP fights)
- Building bursts (rapid block placement)
- Farming runs (crop harvesting)

### Filter System

Queries support multiple filter dimensions:

| Filter | Example | Description |
|--------|---------|-------------|
| `type` | `block_break` | Event type |
| `block` | `diamond_ore` | Block material |
| `player` | `<uuid>` | Specific player |
| `world` | `world_nether` | World name |
| `from` | `3d` | Time range start |
| `to` | `now` | Time range end |
| `grid` | `16` | Aggregation grid size |
| `decay` | `0.95h` | Decay constant |
| `biome` | `deep_dark` | Biome filter |

---

## Contributing

We welcome contributions to any milestone! Here's how to get started:

1. **Pick a Feature:** Choose from the tables above
2. **Discuss First:** Open an issue to discuss implementation approach
3. **Follow Conventions:** See the main README for coding guidelines
4. **Test Thoroughly:** Use MockBukkit for integration tests
5. **Submit PR:** Reference the milestone and feature in your PR

**Priority Areas:**
- Milestone 2 features (Analytics Layer) are the current focus
- Performance improvements always welcome
- Documentation and examples appreciated

---

## Version History

| Version | Milestone | Date | Highlights |
|---------|-----------|------|------------|
| 1.0.0 | 1 - Foundations | TBD | Core stats, moments, heatmaps, social, timeline, health, death replay, story generator |
