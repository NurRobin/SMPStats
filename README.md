# SMPStats – Minecraft Analytics Engine

<p align="center">
  <strong>The first truly modular, query-driven analytics platform for Minecraft servers</strong>
</p>

<p align="center">
  From simple player stats to enterprise-grade server analytics — understand your server like never before.
</p>

---

## 🎯 The Vision

**It started simple:** I wanted players on my SMP to see their stats — distance traveled, biomes explored, blocks mined.

**Then I thought:** What if I could show these stats on my website? Player profiles that update automatically?

**Then I wondered:** What if I could see where players hang out? Who plays with whom? Are there social groups forming?

**Then I realized:** Admins need to understand what's happening on their servers. Where's the lag coming from? Which areas are popular? What are players actually doing?

**And so SMPStats evolved** from a simple stats tracker into a comprehensive **Minecraft Analytics Engine** — inspired by Google Analytics, but purpose-built for Minecraft. Store raw event data efficiently and gain on-demand insights: heatmaps, temporal trends, social dynamics, resource patterns, and automated event detection.

---

## 🎭 Who Is This For?

### 👤 For Players
**What you want:** See your achievements, compare with friends, track your progress

**What you get:**
- Rich in-game stats via `/stats` command
- Compare your performance over time (daily, weekly, monthly)
- See how you rank on leaderboards
- Track skill progression (Mining, Combat, Exploration, Building, Farming)
- View your own heatmaps and activity patterns

**Example:** "How far have I traveled this week? Which biomes have I explored? Am I better at mining than my friend?"

---

### 🛠️ For Server Owners & Admins
**What you want:** Understand what's happening on your server, optimize gameplay, find issues

**What you get:**
- **Heatmaps:** "Where do players actually hang out?" — Visualize movement, mining, deaths, and damage
- **Social Dynamics:** "Who plays with whom?" — Automatic group detection and friendship tracking
- **Lag Detection:** "Who's causing the server to lag?" — Identify hopper-heavy builds, entity farms, redstone contraptions
- **Death Analysis:** "Where are the dangerous zones?" — Hotspot detection for mob spawns and PvP
- **Resource Monitoring:** "Are players finding too many diamonds?" — Track resource acquisition patterns
- **Activity Trends:** "When are players most active?" — Plan events and maintenance windows

**Example:** "Server is lagging — Health Dashboard shows Robin has 847 hoppers at spawn. Time for a conversation."

---

### 💻 For Developers
**What you want:** Integrate Minecraft data into your own tools and services

**What you get:**
- **Full REST API:** 11 endpoints covering stats, heatmaps, moments, social data, and health
- **Real-time Streams:** SSE (Server-Sent Events) for live event feeds
- **Webhooks:** Discord bot integration, automated notifications
- **JSON Exports:** Player timelines, leaderboards, social graphs
- **Flexible Queries:** Filter by time, player, world, event type, grid size, decay factor

**Example:** Build a React dashboard that shows "Who's mining diamonds right now?" with live updates via SSE.

---

## ✨ Core Capabilities

### 📊 Comprehensive Event Tracking
Track everything that matters on your server:
- **Activity:** Playtime, joins/quits, session patterns
- **Combat:** Player kills, mob kills, damage dealt/taken, death causes
- **Exploration:** Distance traveled per dimension, biomes discovered
- **Building:** Blocks placed and broken with material tracking
- **Economy:** Items crafted, items consumed
- **Skills:** Dynamic scoring for Mining, Combat, Exploration, Building, Farming

### 🗺️ Dynamic Heatmaps
Visualize player activity with configurable resolution:
- **Movement Heatmaps:** Where do players travel?
- **Mining Heatmaps:** Where are resources being extracted?
- **Death Heatmaps:** Where are the dangerous zones?
- **Damage Heatmaps:** Where do PvP fights happen?
- **Resource-Specific:** Track diamond, iron, gold, ancient debris separately
- **Exponential Decay:** Weight recent activity higher than old data
- **Configurable Grids:** From 8×8 (detail) to 64×64 (overview) blocks per cell

### 🤝 Social Dynamics
Understand how your community interacts:
- **Proximity Tracking:** Measure time players spend together (16-block radius)
- **Top Pairs:** Discover the strongest player partnerships
- **Shared Activity:** Track cooperative kills, builds, and exploration
- **Group Detection:** *(Future)* Automatic clustering to find social groups
- **Interaction Matrices:** *(Future)* N×N player relationship strength

### 🔔 Moments Engine
Automatically detect and record significant gameplay events:
- **8 Trigger Types:** Block breaks, deaths, damage, item gains, boss kills, and more
- **Merge Windows:** Combine rapid events (e.g., "mined 12 diamonds in 30 seconds")
- **Configurable:** Define custom moments via YAML with material/entity/cause filters
- **Notifications:** *(Future)* In-game messages, titles, sounds for special achievements

### 📈 Timeline & Trends
Track how things change over time:
- **Daily Snapshots:** Per-player cumulative stats captured automatically
- **Period Comparisons:** "How much more active was I this week vs. last week?"
- **Leaderboards:** Rank players by activity within time windows (7-day, 30-day, custom)
- **Trend Analysis:** *(Future)* Identify patterns like "farming bursts" or "exploration sprees"

### 🖥️ Server Health Monitoring
Keep your server running smoothly:
- **Entity Counting:** Track loaded chunks, entities, tile entities
- **Performance Scoring:** 0-100 cost index indicating server load
- **Culprit Detection:** "Who placed all these hoppers?"
- **Per-World Breakdown:** Identify which dimension is causing lag
- **Historical Tracking:** See how server health evolves over time

### 💀 Death Replay
Understand every death in detail:
- **Full Context:** Cause, position, health, fall distance, killer entity
- **Nearby Players:** Who was around when it happened?
- **Nearby Mobs:** What entities were in the area?
- **Inventory:** *(Optional)* Record what items were lost

### 📖 Story Generator
Automated weekly summaries:
- **Top Players:** Most active, most kills, most exploration
- **Recent Moments:** Highlight interesting events from the week
- **JSON Format:** Easy integration with Discord bots or websites
- **Webhook Support:** Push summaries to external services automatically

---

## 🚀 Quick Start

### Installation

**Requirements:**
- Paper 1.21.1 or higher (Spigot/Bukkit not supported)
- Java 21+

```bash
# Download the latest release
wget https://github.com/NurRobin/SMPStats/releases/latest/download/SMPStats.jar

# Copy to your server
cp SMPStats.jar /path/to/server/plugins/

# Start your server
# config.yml and stats.db are created automatically
```

### Basic Configuration

After first launch, edit `plugins/SMPStats/config.yml`:

```yaml
# Enable or disable core features
tracking:
  movement: true      # Track player movement for heatmaps
  blocks: true        # Track block placement/breaking
  kills: true         # Track kills and deaths
  biomes: true        # Track biome exploration
  crafting: true      # Track item crafting
  damage: true        # Track damage dealt and taken
  consumption: true   # Track item consumption

# Enable HTTP API for external tools
api:
  enabled: true
  bind_address: "127.0.0.1"  # Change to "0.0.0.0" for external access
  port: 8765
  api_key: "CHANGE_THIS_SECRET_KEY"

# Enable features
heatmap:
  enabled: true
  
social:
  enabled: true
  nearby_radius: 16   # Distance for proximity tracking

timeline:
  enabled: true

moments:
  enabled: true

death_replay:
  enabled: true

health:
  enabled: true

story:
  enabled: true
  webhook_url: ""     # Optional: Discord webhook for weekly summaries
```

### Essential Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/stats` | Show your own stats | `smpstats.use` |
| `/stats <player>` | Show another player's stats | `smpstats.use` |
| `/stats json` | Export your stats as JSON | `smpstats.use` |
| `/sstats` | Plugin info (version, API status) | `smpstats.use` |
| `/sstats reload` | Reload configuration | `smpstats.reload` |

### Using the API

The HTTP API provides programmatic access to all data:

```bash
# Get all player stats
curl -H "X-API-Key: YOUR_KEY" http://localhost:8765/stats/all

# Get online players
curl -H "X-API-Key: YOUR_KEY" http://localhost:8765/online

# Get recent moments
curl -H "X-API-Key: YOUR_KEY" http://localhost:8765/moments/recent?limit=10

# Get mining heatmap
curl -H "X-API-Key: YOUR_KEY" http://localhost:8765/heatmap/MINING

# Stream live events (SSE)
curl -H "X-API-Key: YOUR_KEY" http://localhost:8765/moments/stream
```

**Full API documentation:** See [docs/API.md](docs/API.md)

---

## 💡 Real-World Use Cases

### 🏗️ Planning Your Server Layout
**Problem:** "Where should I build the spawn market? Where do players naturally gather?"

**Solution:** 
1. Enable movement heatmaps
2. Let players explore naturally for a few days
3. Check heatmap via API or in-game GUI
4. Build shops and infrastructure in high-traffic areas

**Result:** Organic player flow leads to more vibrant community spaces.

---

### 🐌 Finding Lag Sources
**Problem:** "Server TPS is dropping to 15. What's causing it?"

**Solution:**
1. Check `/sstats health` for cost index
2. Identify top contributors (e.g., "Robin: 847 hoppers")
3. Use `/teleport` to inspect the build
4. Work with player to optimize or relocate

**Result:** Targeted lag fixes instead of server-wide hopper limits.

---

### ⚔️ Balancing PvP
**Problem:** "Players complain about spawn camping. Is it actually happening?"

**Solution:**
1. Enable damage heatmaps
2. Check PvP hotspots over 7-day period
3. Identify if spawn area has unusually high damage events
4. Adjust spawn protection radius or add safe zones

**Result:** Data-driven decisions instead of anecdotal complaints.

---

### 💎 Anti-Cheat Assistance
**Problem:** "Did this player find 64 diamonds in 10 minutes legitimately?"

**Solution:** *(Future - Milestone 2)*
1. Query resource-specific heatmap with time filter
2. Check mining patterns for suspicious clusters
3. Review moments for "diamond runs" with high counts
4. Compare against server average and historical data

**Result:** Flag suspicious activity for manual review.

---

### 🤝 Understanding Your Community
**Problem:** "Are there cliques forming? Who's playing together?"

**Solution:**
1. Enable social proximity tracking
2. Check top player pairs via API
3. Review who's consistently online together
4. *(Future)* Use automatic group detection

**Result:** Plan team events, identify mentorship opportunities, understand social dynamics.

---

### 📊 Showcasing Player Achievements
**Problem:** "I want player profiles on my server website"

**Solution:**
1. Enable API with secure key
2. Build website that calls `/stats/{uuid}`
3. Display stats with charts and leaderboards
4. Link Minecraft accounts to website accounts

**Result:** Players see their stats outside the game, increasing engagement.

---

## 🔌 HTTP API

SMPStats includes a full REST API with 11 endpoints. All requests require an API key via `X-API-Key` header.

### Key Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/stats/{uuid}` | GET | Get stats for specific player |
| `/stats/all` | GET | Get stats for all players |
| `/online` | GET | List currently online players |
| `/moments/recent` | GET | Recent moments with filters |
| `/moments/query` | GET | Advanced moment search |
| `/moments/stream` | GET | SSE stream of live events |
| `/heatmap/{type}` | GET | Heatmap bins (MINING, DEATH, MOVEMENT, DAMAGE) |
| `/heatmap/hotspots/{type}` | GET | Named region activity counters |
| `/timeline/{uuid}` | GET | Player timeline snapshots |
| `/timeline/range/{uuid}` | GET | Delta stats over period |
| `/timeline/leaderboard` | GET | Period-based rankings |
| `/social/top` | GET | Top player pairs by time together |
| `/death/replay` | GET | Death replay entries with context |
| `/health` | GET | Current server health snapshot |

### Example: Building a Discord Bot

```javascript
const apiKey = 'YOUR_API_KEY';
const baseUrl = 'http://localhost:8765';

// Get top 5 players by playtime
async function getTopPlayers() {
  const res = await fetch(`${baseUrl}/stats/all`, {
    headers: { 'X-API-Key': apiKey }
  });
  const players = await res.json();
  return players
    .sort((a, b) => b.playtime_seconds - a.playtime_seconds)
    .slice(0, 5);
}

// Stream live moments to Discord channel
const evtSource = new EventSource(`${baseUrl}/moments/stream`, {
  headers: { 'X-API-Key': apiKey }
});

evtSource.onmessage = (event) => {
  const moment = JSON.parse(event.data);
  if (moment.type === 'DIAMOND_RUN') {
    discordChannel.send(`💎 ${moment.player_name} found ${moment.count} diamonds!`);
  }
};
```

**Full documentation:** [docs/API.md](docs/API.md)

---

## 🗺️ Roadmap

See [Roadmap.md](Roadmap.md) for the complete development roadmap with detailed milestones, technical specifications, and future plans.

### Current Status: v0.11.0

**✅ Milestone 1 Complete — Foundations**
- Core stats tracking with SQLite storage
- HTTP API with 11 endpoints
- Heatmaps (mining, death, movement, damage)
- Social proximity tracking
- Moments engine with 8 trigger types
- Timeline snapshots and leaderboards
- Server health monitoring
- Death replay system
- Story generator with webhook support

**🔄 Milestone 2 In Progress — Analytics Layer**
- ✅ Exponential decay model
- ✅ Configurable grid sizes (8×8 to 64×64)
- ✅ Multi-layer heatmaps
- ✅ Dynamic query engine
- ⏳ Time-range filters
- ⏳ Biome-filtered views
- ⏳ Live activity dashboard

**📋 Upcoming Milestones**
- **M3 — Social Dynamics:** Group detection, interaction matrices, social graphs
- **M4 — Event Engine:** JSON event definitions, rolling windows, complex triggers
- **M5 — Storage & Performance:** Raw event storage, compression, sharding, external DBs
- **M6 — Visualization Platform:** Web dashboard, world map overlays, live monitors
- **M7 — Platform Modularization:** Split into Core/API/Web plugins for flexible deployment

---

## 🗄️ Storage Architecture

### SQLite Database Schema

**Core Tables:**
- `stats` — Player statistics with JSON biomes array
- `moments` — Event records with type, payload, location
- `heatmap` — Chunk-based activity bins with decay support
- `social_pairs` — Player proximity time counters
- `timeline` — Daily stat snapshots for trend analysis
- `death_replay` — Death context with nearby entities

**Features:**
- Auto-migration on startup (schema version tracking)
- WAL mode for better concurrency
- JSON fields for flexible data structures
- Downgrade protection to prevent data loss

**Future:**
- PostgreSQL backend for large servers
- Elasticsearch integration for advanced queries
- Time-based sharding (per-month tables)
- Binary encoding for compression

---

## 🔐 Security & Verification

All releases include cryptographic verification:

| Artifact | How to Verify |
|----------|---------------|
| **SHA256 Checksums** | `sha256sum -c SMPStats-vX.Y.Z.jar.sha256` |
| **GPG Signatures** | `gpg --verify SMPStats-vX.Y.Z.jar.asc SMPStats-vX.Y.Z.jar` |
| **Build Provenance** | `gh attestation verify SMPStats-vX.Y.Z.jar --repo NurRobin/SMPStats` |
| **SBOM** | Software Bill of Materials in `*.sbom.json` |

---

## 🤝 Contributing

Contributions are welcome! Here's how to get started:

### Priority Areas

**Milestone 2 (Analytics Layer):**
- Time-range filters for queries
- Biome-filtered heatmap views
- Live activity dashboard components
- Player comparison views

**Performance:**
- Query optimization for large datasets
- Caching strategies for hot queries
- Async processing improvements

**Documentation:**
- More use case examples
- Video tutorials
- Configuration guides

### Development Setup

```bash
# Clone the repository
git clone https://github.com/NurRobin/SMPStats.git
cd SMPStats

# Build
mvn clean package

# Run tests
mvn test

# Output: target/SMPStats.jar
```

### Guidelines

1. **Discuss First:** Open an issue before starting major work
2. **Follow Conventions:** Use existing code style and patterns
3. **Test Thoroughly:** Write MockBukkit tests for new features
4. **Document:** Update README/Roadmap for user-facing changes
5. **Submit PR:** Against `dev` branch with clear description

---

## 📋 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `smpstats.use` | true | Use `/stats` and `/sstats` commands |
| `smpstats.reload` | op | Reload plugin configuration |
| `smpstats.edit` | op | Reset or modify player stats |
| `smpstats.admin` | op | Access admin-level API endpoints |

---

## 📦 Releases

**Current Version:** v0.11.0

**Release Schedule:**
- **Stable Releases:** `vX.Y.Z` (production-ready)
- **Pre-Releases:** `vX.Y.Z-beta.N` (testing features)
- **Automated System:** Set version → commit → auto-release with artifacts

**Each release includes:**
- Compiled JAR
- SHA256 checksum
- GPG signature
- SBOM (Software Bill of Materials)
- Build provenance attestation

See [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md) for details.

---

## 🔮 Future Vision

### Where We're Heading

**Short-term (v1.0):**
- Complete Milestone 2 (Analytics Layer)
- Polish in-game GUI
- Comprehensive documentation

**Medium-term (v1.5):**
- Milestone 3 (Social Dynamics with group detection)
- Milestone 4 (Advanced event engine)

**Long-term (v2.0+):**
- **Platform Modularization:** Split into three plugins
  - `SMPStats-Core.jar` — Always required, tracks events
  - `SMPStats-API.jar` — Optional, can run standalone
  - `SMPStats-Web.jar` — Optional, built-in dashboard
  
- **Flexible Deployment:**
  - Simple: All 3 in `plugins/` folder
  - Developer: Core + API only, build custom frontend
  - Enterprise: Multi-server network with centralized analytics

- **Advanced Features:**
  - Anti-cheat pattern detection
  - Predictive analytics (player retention, churn risk)
  - Machine learning for anomaly detection
  - Integration with economy plugins

---

## 📜 License

MIT License — see [LICENSE](LICENSE) for full details.

---

## 🙏 Acknowledgments

Built with:
- **Paper API** — Modern Minecraft server platform
- **Javalin** — Lightweight HTTP server
- **SQLite** — Embedded database
- **Jackson** — JSON processing
- **MockBukkit** — Testing framework

Inspired by:
- Google Analytics (query-driven analytics)
- Prometheus (time-series data with decay)
- CoreProtect (efficient Minecraft logging)

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/NurRobin/SMPStats/issues)
- **Discussions:** [GitHub Discussions](https://github.com/NurRobin/SMPStats/discussions)
- **Documentation:** [docs/](docs/)

---

<p align="center">
  <strong>Transform your Minecraft server into a data-driven experience.</strong><br>
  Understand player behavior. Optimize gameplay. Discover hidden patterns.
</p>

<p align="center">
  <em>SMPStats — Because every block tells a story.</em>
</p>