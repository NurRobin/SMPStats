# SMPStats Roadmap

> **From Simple Stats to Full Analytics Engine**

---

## 🎯 The Journey: How We Got Here

### Phase 1: Player Stats
**"I wanted players to see their achievements"**

It started with a simple idea: let players use `/stats` to see how far they've traveled, how many blocks they've mined, which biomes they've explored. Basic tracking, basic commands.

**Result:** Core stats tracking system with in-game commands.

---

### Phase 2: External Integration
**"What if these stats were on my website?"**

Then I wanted to show player stats on my server website. Create profiles that link Minecraft accounts to web accounts. Display leaderboards, show who's most active.

**Result:** HTTP REST API with 11 endpoints for external tools.

---

### Phase 3: Spatial Understanding
**"Where do players actually hang out?"**

Next question: Where are players spending their time? Which areas are popular? Where are people mining? Where do PvP fights happen? Who plays together?

**Result:** Heatmaps (mining, deaths, movement, damage) and social proximity tracking.

---

### Phase 4: Server Health
**"Why is my server lagging?"**

Then came the practical admin needs: The server is lagging. What's causing it? Is it entities? Hoppers? Who's responsible?

**Result:** Server health monitoring with culprit detection (chunks, entities, hoppers, redstone).

---

### Phase 5: The Analytics Engine
**"This is becoming something bigger"**

At this point, it became clear: This isn't just a stats plugin anymore. This is a **comprehensive analytics platform** for Minecraft servers. Like Google Analytics, but for game servers.

**Where we are now:** A query-driven, modular analytics engine with real-time insights, temporal trends, social dynamics, and automated event detection.

**Where we're going:** Even deeper insights, more flexibility, better performance, and eventually a fully modular platform.

---

## 🎭 Who This Serves

### 👤 Players
**What they want:** 
- See their own progress and achievements
- Compare with friends
- Track improvement over time

**What they get:**
- Rich `/stats` command with detailed breakdowns
- Skill profiles (Mining, Combat, Exploration, Building, Farming)
- Timeline comparisons ("I mined 2x more this week!")
- Leaderboard rankings
- *(Future)* Web profiles with graphs and badges

**Key features:** Stats tracking, timelines, leaderboards, skill profiles

---

### 🛠️ Server Owners & Admins
**What they want:**
- Understand what players are doing
- Find and fix performance issues
- Plan server layout and events
- Identify problems before they escalate

**What they get:**
- **Heatmaps** showing where players travel, mine, die, and fight
- **Health monitoring** to identify lag sources and culprits
- **Social tracking** to understand community dynamics
- **Death analysis** to find dangerous zones
- **Resource monitoring** to track economy balance
- **Trend analysis** to plan events during peak activity

**Key features:** Heatmaps, health monitoring, social dynamics, moments detection

---

### 💻 Developers
**What they want:**
- Integrate Minecraft data into custom tools
- Build Discord bots, websites, dashboards
- Access real-time event streams
- Query historical data flexibly

**What they get:**
- **Full REST API** with 11 endpoints
- **Real-time SSE streams** for live events
- **Flexible queries** with filters (time, player, world, type, grid size, decay)
- **JSON exports** for all data types
- **Webhook support** for automated notifications

**Key features:** HTTP API, SSE streams, webhooks, JSON exports

---

## 💭 Design Principles

These principles guide every feature and decision:

### 1. Lightweight but Expressive
- Powerful analytics without sacrificing server performance
- Async processing for expensive operations
- Efficient storage (SQLite with future sharding options)
- **Goal:** <1% TPS impact on typical servers

### 2. Storage Efficiency
- Track everything relevant without bloating to 50GB
- Smart compression and encoding strategies
- Exponential decay for time-sensitive data
- Configurable retention policies
- **Goal:** <500MB for 100 players over 3 months

### 3. Unique Insights
- Go beyond basic counts to discover patterns
- Social dynamics (who plays with whom)
- Temporal trends (streaks, bursts, decay)
- Behavioral analysis (farming patterns, exploration habits)
- **Goal:** Answer questions other plugins can't

### 4. Configurable Everything
- Events and triggers defined in configuration
- No code changes needed for customization
- Sensible defaults for immediate use
- Power-user options for advanced setups
- **Goal:** Works out-of-box, scales to custom needs

---

## ✅ Milestone 1 — Foundations (COMPLETED)

> **Status:** Complete as of v0.10.0  
> **Goal:** A complete event-driven stat tracking system with baseline listeners, local storage, and early "moments" logic.

### What We Built

| Category | Features | Status |
|----------|----------|--------|
| **Core Tracking** | Playtime, kills, deaths, blocks, distance, biomes, crafting, damage, consumption | ✅ Done |
| **Skills** | Mining, Combat, Exploration, Builder, Farmer profiles with configurable weights | ✅ Done |
| **Moments** | 8 trigger types, merge windows, YAML configuration | ✅ Done |
| **Heatmaps** | Mining/Death bins at chunk resolution, hotspot regions | ✅ Done |
| **Social** | Proximity tracking, pair counters, shared kills | ✅ Done |
| **Timeline** | Daily snapshots, range deltas, leaderboards | ✅ Done |
| **Health** | Chunk/entity/hopper/redstone counting, cost index | ✅ Done |
| **Death Replay** | Cause, position, nearby entities, inventory | ✅ Done |
| **Story** | Weekly summaries, webhook integration | ✅ Done |
| **API** | 11 endpoints with API key auth | ✅ Done |
| **Storage** | SQLite with schema migrations, WAL mode | ✅ Done |

### Real-World Impact

**For Players:**
- See comprehensive stats with `/stats`
- Track skill progression over time
- Compare with friends on leaderboards

**For Admins:**
- Identify lag sources via health monitoring
- Understand player distribution via heatmaps
- Discover social groups via proximity tracking

**For Developers:**
- Full API access to all tracked data
- Real-time event streams via SSE
- JSON exports for custom dashboards

---

## 🔄 Milestone 2 — Analytics Layer (IN PROGRESS)

> **Status:** Partially complete (v0.11.0)  
> **Goal:** Dynamic aggregation engine with exponential decay, configurable grid sizes, and multi-layer heatmap queries.

### Why This Matters

**Problems we're solving:**

1. **"Where are players mining diamonds RIGHT NOW?"**
   - Old data is still counted equally → misleading
   - Solution: Exponential decay weights recent activity higher

2. **"Should I build my shop here or there?"**
   - Need to see high-traffic areas at different zoom levels
   - Solution: Configurable grid sizes (8×8 for detail, 64×64 for overview)

3. **"Where do PvP fights actually happen?"**
   - Deaths tell part of the story, but not where damage occurs
   - Solution: Separate damage heatmap layer

4. **"Show me only the last 6 hours of activity"**
   - Need temporal filtering without losing historical data
   - Solution: Time-range queries with on-demand aggregation

### Features

| Feature | Priority | Size | Status | Use Case |
|---------|----------|------|--------|----------|
| **Exponential Decay Model** | High | M | ✅ Done | Show "hot right now" activity patterns |
| **Configurable Grid Sizes** | High | M | ✅ Done | Zoom from overview (64×64) to detail (8×8) |
| **Multi-Layer Heatmaps** | High | L | ✅ Done | Separate mining, deaths, movement, damage layers |
| **Dynamic Query Engine** | High | L | ✅ Done | On-demand aggregation with filters |
| **Movement Heatmaps** | Medium | M | ✅ Done | Visualize player travel and popular zones |
| **Damage Heatmaps** | Medium | M | ✅ Done | Identify PvP hotspots and mob danger zones |
| **Resource-Specific Heatmaps** | Medium | M | ✅ Done | Track diamond, iron, gold, ancient debris separately |
| **OpenAPI-Published API** | High | M | ⚠️ Partial | Machine-readable docs exposed for external tooling |
| **Time-Range Filters** | Medium | M | ✅ Done | Query "last 6h", "today", "this week" |
| **Biome-Filtered Views** | Low | S | ⏳ Todo | Filter any heatmap by biome type |
| **In-Game GUI** | High | L | ✅ Done | Rich chest-menu interface with visual insights |
| **Live Activity Dashboard** | Medium | M | ⚠️ Partial | "Who's farming what right now" real-time view |
| **Player Comparison Views** | Medium | M | ⏳ Todo | Side-by-side stat comparisons in GUI |

### Technical Deep Dive

#### Exponential Decay Formula
```
weight = exp(-Δt / τ)

where:
  Δt = time since event occurred (seconds)
  τ = decay constant (configurable half-life)
```

**Example with τ = 3600 (1 hour half-life):**

| Time Ago | Weight | Meaning |
|----------|--------|---------|
| 0 min | 1.00 | Current activity (full weight) |
| 30 min | 0.61 | Recent activity (high weight) |
| 1 hour | 0.37 | Half-life reached |
| 2 hours | 0.14 | Older activity (low weight) |
| 6 hours | 0.0025 | Historical data (minimal weight) |

**Why this matters:** You can query "hot right now" mining spots without losing historical data. The same dataset powers both recent and long-term analysis.

#### Grid Aggregation
```
Store: Raw events with precise X/Z coordinates
Query: Aggregate to requested grid size at query time
Cache: Hot queries for performance

Supported grid sizes:
  8×8   - Fine detail (individual builds)
  16×16 - Chunk resolution (default)
  32×32 - Medium zones (districts)
  64×64 - Large areas (regions)
```

**Why this matters:** Flexible zoom levels without storing multiple copies. Start broad, drill down to details.

### What's Next for M2

**Remaining work:**
- Time-range query syntax (`from=6h`, `to=now`, `range=3d`)
- Biome filtering for all heatmap types
- Complete live activity dashboard (real-time streaming)
- In-GUI player comparison views

**Target completion:** v0.12.0 (next minor release)

---

## 🤝 Milestone 3 — Social Dynamics (PLANNED)

> **Status:** Planning  
> **Goal:** Advanced player-proximity tracking, automatic group detection, and social graph analysis.

### Why This Matters

**Problems we're solving:**

1. **"Who are the core friend groups on my server?"**
   - Manual observation is incomplete
   - Solution: Automatic group detection via clustering algorithm

2. **"Which players are always online together?"**
   - Need to identify consistent partnerships
   - Solution: Time-weighted interaction matrices

3. **"Where do groups hang out?"**
   - Understanding social spaces helps with builds
   - Solution: Group-specific heatmaps

4. **"Are new players integrating into the community?"**
   - Identify isolated players who need help
   - Solution: Social graph analysis

### Features

| Feature | Priority | Size | Status | Use Case |
|---------|----------|------|--------|----------|
| **Enhanced Proximity Tracking** | High | M | Todo | 10-block radius, 120+ second threshold |
| **Automatic Group Detection** | High | L | Todo | Clustering algorithm for 3+ player groups |
| **Group Formation Tracking** | High | M | Todo | Track how groups form, grow, dissolve |
| **Interaction Matrices** | Medium | M | Todo | N×N player interaction strength grid |
| **Social Graph Export** | Medium | M | Todo | JSON/GraphML for visualization tools |
| **"Who Plays With Whom" API** | High | M | Todo | Query player relationships and groups |
| **Active Together Detection** | Medium | M | Todo | Identify players consistently online together |
| **Time-Filtered Social Data** | Medium | S | Todo | "Last 6h", "past week", "this month" views |
| **Group Hangout Zones** | Medium | M | Todo | Heatmaps of where groups spend time |
| **Party Session Detection** | Low | L | Todo | Automatically detect coordinated play sessions |

### Technical Approach

#### Proximity Detection
```
Every N seconds:
  1. Sample all online player positions
  2. Build distance matrix (all pairs)
  3. Record pairs within threshold (e.g., 10 blocks)
  4. Accumulate time → "interaction score"

Threshold example:
  - Distance: ≤10 blocks
  - Duration: ≥120 seconds cumulative
  - Result: Strong interaction recorded
```

#### Group Clustering
```
Algorithm:
  1. Build graph: Players = nodes, interaction scores = edge weights
  2. Apply clustering (e.g., Louvain algorithm, Union-Find)
  3. Identify connected components (groups)
  4. Filter: Groups must have 3+ members
  5. Track: Group stability over time

Output:
  - Group ID
  - Members (UUIDs)
  - Formation timestamp
  - Interaction strength scores
  - Dissolution timestamp (if applicable)
```

#### Use Case Example
```
API Query: GET /social/groups?min_size=3&active_within=7d

Response:
{
  "groups": [
    {
      "id": "g1",
      "members": ["Alice", "Bob", "Charlie"],
      "formed": "2024-11-01T12:00:00Z",
      "interaction_score": 847.3,
      "shared_playtime_hours": 42.5,
      "common_locations": [
        {"x": 1500, "z": -3000, "time_spent_minutes": 380}
      ]
    }
  ]
}

Admin action: "These 3 players always play together → create team challenge quest"
```

### Real-World Impact

**For Admins:**
- Plan events for detected groups
- Identify isolated players who need welcoming
- Understand social hierarchy and influence
- Design team-based challenges

**For Developers:**
- Build social network visualizations
- Create "find a group" matchmaking systems
- Analyze community health metrics

---

## ⚡ Milestone 4 — Event Engine (PLANNED)

> **Status:** Planning  
> **Goal:** Full JSON-based event definitions with rolling windows, multi-step conditions, and timeout buffers.

### Why This Matters

**Problems we're solving:**

1. **"I want to track diamond veins, not individual ore breaks"**
   - Current system fires per-block
   - Solution: Rolling windows that reset on each matching event

2. **"How do I define custom events without code changes?"**
   - Currently requires YAML editing and plugin reload
   - Solution: JSON-based event definitions with hot-reload

3. **"I want multi-step achievements (e.g., kill dragon → craft elytra)"**
   - Current system only handles single triggers
   - Solution: Multi-step condition chains

4. **"I want notifications when events trigger"**
   - Current system only logs events
   - Solution: Configurable in-game messages, titles, sounds

### Features

| Feature | Priority | Size | Status | Use Case |
|---------|----------|------|--------|----------|
| **JSON Event Definitions** | High | L | Todo | Richer schema than YAML, better validation |
| **Rolling Window Events** | High | L | Todo | Timer resets on each matching event |
| **Timeout-Based Finalization** | High | M | Todo | Complete event when window expires |
| **Multi-Step Conditions** | Medium | L | Todo | Sequence of conditions before trigger |
| **Event Categories** | Medium | S | Todo | Loot, Combat, Exploration, Accident tags |
| **Event Notifications** | Medium | M | Todo | In-game messages, sounds, titles |
| **Event API Exposure** | Medium | M | Todo | WebSocket/SSE for live event stream |
| **Complex Triggers** | Low | L | Todo | Compound AND/OR/NOT conditions |

### Technical Approach

#### Rolling Window Logic
```
Example: Diamond Vein Detection

1. Player breaks diamond ore
   → Start 30-second timer
   → Event counter = 1

2. Player breaks another diamond (within 30s)
   → Reset timer to 30 seconds
   → Event counter = 2

3. Player breaks another diamond (within 30s)
   → Reset timer to 30 seconds
   → Event counter = 3

4. 30 seconds pass with no more diamonds
   → Finalize event
   → Record: "Robin mined 12 diamonds near (114, -62)"
   → Trigger notifications, webhooks, etc.
```

#### JSON Event Definition Example
```json
{
  "id": "diamond_vein",
  "version": "1.0",
  "description": "Tracks diamond mining streaks",
  "category": "mining",
  
  "match": {
    "type": "block_break",
    "materials": ["DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"]
  },
  
  "window": {
    "type": "rolling",
    "reset_seconds": 30,
    "finalize_on_timeout": true
  },
  
  "conditions": {
    "min_events": 2,
    "max_distance_between_events": 50
  },
  
  "notification": {
    "enabled": true,
    "title": "&b{player} struck diamonds!",
    "subtitle": "&7Found {count} diamonds",
    "sound": "ENTITY_PLAYER_LEVELUP",
    "broadcast_radius": 50
  },
  
  "webhook": {
    "enabled": true,
    "url": "${DISCORD_WEBHOOK}",
    "template": "discord_embed"
  }
}
```

#### Multi-Step Event Example
```json
{
  "id": "dragon_slayer_fully_equipped",
  "description": "Kill dragon and craft full elytra setup",
  "category": "achievement",
  
  "steps": [
    {
      "id": "kill_dragon",
      "match": {
        "type": "entity_kill",
        "entity": "ENDER_DRAGON"
      }
    },
    {
      "id": "craft_elytra",
      "match": {
        "type": "item_craft",
        "item": "ELYTRA"
      },
      "timeout_after_previous_step": 3600
    },
    {
      "id": "collect_fireworks",
      "match": {
        "type": "item_gain",
        "item": "FIREWORK_ROCKET",
        "min_amount": 64
      },
      "timeout_after_previous_step": 3600
    }
  ],
  
  "completion": {
    "notification": {
      "title": "&6&lDRAGON SLAYER!",
      "subtitle": "&e{player} mastered the End",
      "sound": "UI_TOAST_CHALLENGE_COMPLETE",
      "broadcast": true
    }
  }
}
```

### Real-World Impact

**For Admins:**
- Define custom events without coding
- Create server-specific achievements
- Track unique gameplay patterns

**For Players:**
- More meaningful event notifications
- Recognition for complex achievements
- Better sense of progression

---

## 🚀 Milestone 5 — Storage & Performance (PLANNED)

> **Status:** Planning  
> **Goal:** Efficient raw-event storage, compression, sharding, and optional external database backends.

### Why This Matters

**Problems we're solving:**

1. **"My stats.db file is 5GB after 6 months"**
   - Current system stores aggregated data only
   - Solution: Efficient raw event storage with compression

2. **"I want to query every diamond mined in the last year"**
   - Need full historical data, not just summaries
   - Solution: Raw event log with columnar storage

3. **"My server has 500+ players, SQLite is struggling"**
   - Need better concurrency and query performance
   - Solution: Optional PostgreSQL/Elasticsearch backends

4. **"Can I delete old data to save space?"**
   - Need automatic retention policies
   - Solution: Configurable data archival and cleanup

### Features

| Feature | Priority | Size | Status | Use Case |
|---------|----------|------|--------|----------|
| **Raw Event Storage** | High | L | Todo | Store every event for replay/analysis |
| **Binary Encoding** | Medium | L | Todo | Compact event serialization |
| **Columnar Storage** | Medium | L | Todo | Optimized for analytical queries |
| **Time-Based Sharding** | Medium | L | Todo | Per-month or per-week tables |
| **World-Based Sharding** | Medium | M | Todo | Separate tables per dimension |
| **Data Retention Policies** | Medium | M | Todo | Auto-archive/delete old data |
| **PostgreSQL Backend** | Low | XL | Todo | For large servers (500+ players) |
| **Elasticsearch Backend** | Low | XL | Todo | For high-load analytical queries |
| **Query Optimization** | Medium | L | Todo | Index tuning, query plans |
| **Async Write Pipeline** | High | M | Todo | Non-blocking event storage |

### Technical Approach

#### Raw Event Storage Schema
```sql
CREATE TABLE events (
  id INTEGER PRIMARY KEY,
  timestamp INTEGER NOT NULL,
  player_uuid TEXT NOT NULL,
  event_type TEXT NOT NULL,
  world TEXT NOT NULL,
  x INTEGER NOT NULL,
  y INTEGER NOT NULL,
  z INTEGER NOT NULL,
  payload BLOB NOT NULL
);

-- Indexes for common queries
CREATE INDEX idx_events_timestamp ON events(timestamp);
CREATE INDEX idx_events_player ON events(player_uuid, timestamp);
CREATE INDEX idx_events_type ON events(event_type, timestamp);
CREATE INDEX idx_events_location ON events(world, x, z, timestamp);
```

#### Time-Based Sharding
```
Instead of one giant table:
  events (5,000,000 rows)

Use monthly shards:
  events_2024_11 (150,000 rows)
  events_2024_12 (180,000 rows)
  events_2025_01 (200,000 rows)

Benefits:
  - Faster queries (smaller tables)
  - Easy archival (drop old tables)
  - Better index performance
```

#### Binary Encoding for Compression
```
Current (JSON payload): ~200 bytes/event
Binary (varint + dict encoding): ~40 bytes/event

Compression ratio: 5:1

For 1M events:
  JSON: 200 MB
  Binary: 40 MB

Savings: 160 MB per million events
```

#### Retention Policy Example
```yaml
storage:
  retention:
    raw_events:
      keep_days: 90
      archive_to: "/backups/smpstats/"
    
    aggregated_stats:
      keep_forever: true
    
    heatmap_bins:
      keep_days: 180
      decay_after_days: 30
```

### Real-World Impact

**For Small Servers:**
- SQLite handles everything efficiently
- No external dependencies needed

**For Medium Servers:**
- Time-based sharding keeps SQLite fast
- Optional archival for cost savings

**For Large Servers:**
- PostgreSQL handles concurrent writes
- Elasticsearch powers complex analytical queries

---

## 🎨 Milestone 6 — Visualization Platform (PLANNED)

> **Status:** Planning  
> **Goal:** External web dashboard with world heatmaps, live monitors, and social graph visualization.

### Why This Matters

**Problems we're solving:**

1. **"I want to see heatmaps overlaid on my world map"**
   - Need visual representation of spatial data
   - Solution: Leaflet/MapLibre integration with tile rendering

2. **"I want real-time activity monitors for admins"**
   - Need live dashboard that auto-updates
   - Solution: WebSocket/SSE connections with React components

3. **"I want to visualize who plays with whom"**
   - Social data is hard to understand as text
   - Solution: Interactive network graph visualization

4. **"I don't want to code my own dashboard"**
   - Many admins aren't developers
   - Solution: Built-in web dashboard with professional UI

### Features

| Feature | Priority | Size | Status | Use Case |
|---------|----------|------|--------|----------|
| **Web Dashboard** | Medium | XL | ✅ Done | Built-in dashboard with public/admin sections |
| **World Heatmap Tiles** | Medium | XL | Todo | Leaflet/MapLibre tile overlay system |
| **Dynmap Integration** | Medium | L | Todo | Overlay stats on existing Dynmap |
| **Live Trend Monitors** | Medium | L | Partial | Auto-refresh activity graphs and counters |
| **Player Timeline View** | Medium | M | ✅ Done | Visual timeline of player activity |
| **Social Graph Visualization** | Medium | L | Todo | Interactive D3.js/Cytoscape network diagram |
| **Resource Dashboard** | Low | M | Todo | Mining trends, farming patterns over time |
| **Leaderboard Pages** | Low | S | ✅ Done | Public leaderboards in web interface |
| **Custom Report Builder** | Low | XL | Todo | Drag-and-drop analytics builder |

### Technical Approach

#### Web Dashboard Architecture
```
Current (v0.11.0):
  ✅ Embedded Javalin HTTP server
  ✅ Public stats pages
  ✅ Admin monitoring pages
  ✅ API key authentication
  ⏳ Live updates via SSE
  ⏳ Interactive visualizations

Future:
  React/Vue frontend (SPA)
  Real-time WebSocket updates
  Responsive mobile design
  Theme customization
```

#### World Heatmap Tiles
```
Approach:
  1. Render heatmap data to PNG tiles (256×256px)
  2. Serve tiles via HTTP: /tiles/{z}/{x}/{y}.png
  3. Frontend uses Leaflet to display/zoom tiles
  4. Overlay on Dynmap or standalone map

Example tile URL:
  http://localhost:8765/tiles/heatmap/mining/3/4/2.png
  
  where:
    3 = zoom level
    4 = tile X coordinate
    2 = tile Y coordinate
```

#### Social Graph Visualization
```html
<div id="social-graph"></div>

<script>
  // Fetch social graph data
  const data = await fetch('/api/social/graph')
    .then(r => r.json());
  
  // Render with D3.js force-directed graph
  const nodes = data.players.map(p => ({
    id: p.uuid,
    name: p.name,
    group: p.primary_group
  }));
  
  const links = data.interactions.map(i => ({
    source: i.player1,
    target: i.player2,
    weight: i.interaction_score
  }));
  
  // D3 force simulation
  const simulation = d3.forceSimulation(nodes)
    .force("link", d3.forceLink(links))
    .force("charge", d3.forceManyBody())
    .force("center", d3.forceCenter());
</script>
```

### Real-World Impact

**For Admins:**
- Visual understanding of server activity
- No coding required for dashboard access
- Professional presentation for server websites

**For Players:**
- Engaging way to view their stats
- Competitive leaderboards and rankings
- Visual progression tracking

---

## 🔷 Milestone 7 — Platform Modularization (FUTURE)

> **Status:** Future planning (post-v2.0)  
> **Goal:** Split SMPStats into Core, API, and Web plugins for flexible deployment scenarios.

### Why This Matters

**The Problem:**

Currently, SMPStats is a single JAR with everything bundled:
- Core event tracking
- HTTP API server
- Web dashboard

This works great for most users, but has limitations:
- Can't scale API/Web independently
- Can't deploy API/Web on separate hosts
- Developers who only need API still get Web bundle
- Networks with multiple game servers duplicate API/Web instances

**The Solution:**

Three separate plugins that work together:

```
┌──────────────────────────────────────────────────────────┐
│ SMPStats-Core.jar (Always Required)                     │
│ → Event tracking, SQLite storage, stats logic            │
│ → Runs in plugins/ folder                                │
│ → Source of truth for all data                           │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ SMPStats-API.jar (Optional)                              │
│ → REST endpoints, SSE streams, webhooks                  │
│ → Can run in plugins/ OR as standalone service           │
│ → Connects to Core via bridge protocol                   │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ SMPStats-Web.jar (Optional)                              │
│ → Built-in dashboard, public profiles                    │
│ → Can run in plugins/ OR as standalone service           │
│ → Connects to API for data                               │
└──────────────────────────────────────────────────────────┘
```

### Deployment Scenarios

#### Scenario 1: Simple SMP (Most Common)
```
plugins/
├── SMPStats-Core.jar
├── SMPStats-API.jar
└── SMPStats-Web.jar

Result:
  - Everything runs on game server
  - Access dashboard at http://server:3000
  - No external services needed
  - Perfect for 10-50 player servers
```

#### Scenario 2: Developer with Custom Frontend
```
Game Server:
  plugins/
  ├── SMPStats-Core.jar
  └── SMPStats-API.jar

Separate Dev Machine:
  - Custom React/Vue dashboard
  - Connects to API at http://game-server:8765
  
Result:
  - Developer builds custom UI
  - Full API access
  - No unnecessary Web bundle
```

#### Scenario 3: Multi-Server Network
```
Game Server 1:
  plugins/SMPStats-Core.jar

Game Server 2:
  plugins/SMPStats-Core.jar

Game Server 3:
  plugins/SMPStats-Core.jar

Central Web Host:
  java -jar SMPStats-API.jar \
    --aggregate \
    --cores server1:25566,server2:25566,server3:25566
  
  java -jar SMPStats-Web.jar \
    --api http://localhost:8765

Result:
  - One dashboard shows stats from all 3 servers
  - Players see combined stats across network
  - Web traffic offloaded from game servers
```

### Features

| Feature | Priority | Size | Status | Description |
|---------|----------|------|--------|-------------|
| **Bridge Protocol Design** | High | L | Todo | RPC protocol for Core ↔ API communication |
| **Core Bridge Server** | High | M | Todo | Core exposes TCP/Unix socket for API to connect |
| **API Standalone Mode** | High | M | Todo | API runs independently, connects to Core bridge |
| **Web Standalone Mode** | Medium | M | Todo | Web runs independently, connects to API |
| **Multi-Core Aggregation** | Medium | XL | Todo | API aggregates data from multiple Core instances |
| **Service Discovery** | Low | L | Todo | Auto-detect available Core instances |
| **Load Balancing** | Low | L | Todo | Distribute API requests across multiple instances |

### Technical Approach

#### Bridge Protocol
```yaml
# SMPStats-Core/config.yml
bridge:
  enabled: true
  bind: "127.0.0.1"  # or "0.0.0.0" for external
  port: 25566
  auth_token: "secure-random-token-here"
  allowed_clients:
    - "192.168.1.100"  # API server IP
    - "192.168.1.101"  # Another API server

# SMPStats-API/config.yml
mode: standalone  # or "plugin"

core:
  host: "minecraft.example.com"
  port: 25566
  auth_token: "secure-random-token-here"
  
# SMPStats-Web/config.yml
mode: standalone  # or "plugin"

api:
  url: "http://api.example.com:8765"
  api_key: "your-api-key"
```

#### RPC Protocol Example
```protobuf
// Bridge protocol definition
service CoreBridge {
  // Basic queries
  rpc GetPlayerStats(UUID) returns (StatsRecord);
  rpc GetAllPlayers() returns (stream PlayerSummary);
  
  // Heatmap queries
  rpc GetHeatmap(HeatmapRequest) returns (HeatmapData);
  rpc GetHotspots(HotspotRequest) returns (HotspotList);
  
  // Real-time streams
  rpc SubscribeEvents(EventFilter) returns (stream Event);
  rpc SubscribeMoments(MomentFilter) returns (stream Moment);
  
  // Health monitoring
  rpc GetHealth() returns (HealthSnapshot);
}

// Example request
message HeatmapRequest {
  HeatmapType type = 1;        // MINING, DEATH, etc.
  optional string world = 2;
  optional int64 from_ts = 3;  // Unix timestamp
  optional int64 to_ts = 4;
  optional int32 grid_size = 5;
  optional double decay = 6;
}
```

### Migration Path

**Phase 1 (v2.0):** Single JAR still works
- Introduce bridge protocol internally
- Core/API/Web communicate via bridge
- Still packaged as one plugin

**Phase 2 (v2.1):** Optional separation
- Release Core/API/Web as separate JARs
- Single JAR still available as "Full" bundle
- Both deployment modes supported

**Phase 3 (v3.0):** Full separation
- Deprecate single JAR bundle
- Focus development on modular architecture
- Advanced features (multi-server) require separation

### Real-World Impact

**For Small Servers:**
- No change! Install all 3 JARs like before
- Slightly larger download size, but same functionality

**For Developers:**
- Only install what you need (Core + API)
- Build custom frontends without Web bundle bloat

**For Networks:**
- Massive performance improvement
- Central analytics for all servers
- Scale independently (1 Web, 3 API, 10 Core)

**For Enterprise:**
- Kubernetes/Docker deployment
- Load balancing and failover
- Horizontal scaling

---

## 📊 Current Progress Overview

### Completed ✅

| Milestone | Version | Date | Features |
|-----------|---------|------|----------|
| M1 - Foundations | v0.10.0 | 2024-11 | Core stats, API, heatmaps, social, timeline, health, death replay, story |

### In Progress 🔄

| Milestone | Current Version | Target | ETA |
|-----------|----------------|--------|-----|
| M2 - Analytics Layer | v0.11.0 | v0.12.0 | Q1 2025 |

**M2 Remaining work:**
- [ ] Time-range filters for queries
- [ ] Biome-filtered heatmap views
- [ ] Live activity dashboard
- [ ] Player comparison views in GUI

### Planned 📋

| Milestone | Target Version | ETA | Priority |
|-----------|---------------|-----|----------|
| M3 - Social Dynamics | v1.0.0 | Q2 2025 | High |
| M4 - Event Engine | v1.1.0 | Q3 2025 | Medium |
| M5 - Storage & Performance | v1.2.0 | Q4 2025 | Medium |
| M6 - Visualization Platform | v1.5.0 | 2026 | Low |
| M7 - Modularization | v2.0.0 | 2026+ | Future |

---

## 🛠️ Contributing

We welcome contributions! Here's where help is needed:

### Priority Areas (M2 Focus)

**Backend:**
- Time-range query implementation
- Biome filtering for heatmaps
- Query optimization and caching
- Performance testing with large datasets

**Frontend:**
- Live activity dashboard components
- Player comparison UI in chest menu
- Admin analytics visualizations
- Mobile-responsive web views

**Documentation:**
- More use case examples
- Video tutorials
- Configuration best practices
- Performance tuning guides

### How to Contribute

1. **Pick a Feature:** Choose from milestone tables above
2. **Discuss First:** Open an issue to discuss approach
3. **Follow Conventions:** See main README for guidelines
4. **Test Thoroughly:** Use MockBukkit for integration tests
5. **Submit PR:** Reference milestone and feature in PR description

### Getting Started

```bash
# Clone repository
git clone https://github.com/NurRobin/SMPStats.git
cd SMPStats

# Build
mvn clean package

# Run tests
mvn test

# Output: target/SMPStats.jar
```

---

## 📅 Version History

| Version | Date | Milestone | Highlights |
|---------|------|-----------|------------|
| v0.11.0 | 2024-11 | M2 Partial | Decay model, grid sizes, multi-layer heatmaps, in-game GUI |
| v0.10.0 | 2024-11 | M1 Complete | Core stats, moments, heatmaps, social, timeline, health, API |
| v0.9.0 | 2024-10 | M1 | Death replay, story generator |
| v0.8.0 | 2024-10 | M1 | Server health monitoring |
| v0.7.0 | 2024-09 | M1 | Timeline snapshots and leaderboards |
| v0.6.0 | 2024-09 | M1 | Social proximity tracking |
| v0.5.0 | 2024-08 | M1 | Heatmaps and hotspots |
| v0.4.0 | 2024-08 | M1 | Moments engine |
| v0.3.0 | 2024-07 | M1 | HTTP API |
| v0.2.0 | 2024-07 | M1 | Skill profiles |
| v0.1.0 | 2024-06 | M1 | Core stats tracking |

---

## 🎯 What's Next?

### Short-term (Next Release: v0.12.0)

**Focus:** Complete Milestone 2 (Analytics Layer)

**Remaining features:**
1. Time-range filters (`from=6h`, `to=now`, `range=3d`)
2. Biome-filtered heatmaps (e.g., "Show Deep Dark mining only")
3. Live activity dashboard ("Who's farming what RIGHT NOW?")
4. Player comparison views ("Compare me vs. my friend")

**Why these matter:**
- Admins can answer "What happened in the last 6 hours?"
- Biome filters reveal dimension-specific patterns
- Live dashboard shows real-time server activity
- Comparisons drive player engagement

**Target:** Q1 2025

---

### Medium-term (v1.0.0)

**Focus:** Milestone 3 (Social Dynamics)

**Key features:**
1. Automatic group detection (clustering algorithm)
2. "Who plays with whom" API endpoint
3. Group hangout zone heatmaps
4. Social graph JSON export

**Why this matters:**
- Automatically discover friend groups
- Plan team events based on real data
- Identify isolated players who need community
- Visualize social network structure

**Target:** Q2 2025

---

### Long-term (v2.0+)

**Focus:** Platform maturity and enterprise features

**Goals:**
1. Complete M4 (Event Engine) - JSON event definitions
2. Complete M5 (Storage) - PostgreSQL/Elasticsearch backends
3. Complete M6 (Visualization) - World map tile overlays
4. Begin M7 (Modularization) - Separate Core/API/Web

**Vision:** SMPStats becomes the de-facto analytics platform for serious Minecraft servers and networks.

---

## 🌟 Success Metrics

### Milestone 2 (Analytics Layer)
- [ ] Query response time <100ms for 1M events
- [ ] Memory usage <512MB with full dataset
- [ ] Support 10+ simultaneous queries
- [ ] 10+ grid sizes working

### Milestone 3 (Social Dynamics)
- [ ] Detect groups with >90% accuracy
- [ ] Process 1000+ players efficiently
- [ ] Social queries <50ms
- [ ] Export graphs >10,000 nodes

### Milestone 5 (Storage)
- [ ] Handle 10M+ raw events
- [ ] Database size <1GB for 100 players/3 months
- [ ] Write throughput >1000 events/sec
- [ ] Query latency <10ms (indexed)

### Milestone 7 (Modularization)
- [ ] Zero downtime migration path
- [ ] Backward compatible for 1 major version
- [ ] Aggregate 10+ Core instances
- [ ] <5% performance overhead vs. monolithic

---

## 💬 Questions & Feedback

Have questions about the roadmap? Want to suggest a feature?

- **Discussions:** [GitHub Discussions](https://github.com/NurRobin/SMPStats/discussions)
- **Feature Requests:** [GitHub Issues](https://github.com/NurRobin/SMPStats/issues)
- **Bug Reports:** [GitHub Issues](https://github.com/NurRobin/SMPStats/issues)

---

<p align="center">
  <strong>The journey from simple stats to full analytics engine continues.</strong><br>
  <em>Every milestone brings new insights. Every feature solves real problems.</em>
</p>

<p align="center">
  Join us in building the future of Minecraft server analytics.
</p>
