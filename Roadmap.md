# SMPStats Roadmap

Status: `Todo` | `In Progress` | `Done`  
Priority: `High` | `Medium` | `Low`  
Size: `S` | `M` | `L` | `XL`

## Current
- ✅ Baseline stats tracking, SQLite, `/stats`, HTTP API, autosave, reload command.

## Focus Features
| Feature | Priority | Size | Status | Notes |
| --- | --- | --- | --- | --- |
| Skill Profiles (Mining/Combat/Exploration/Builder/Farmer) | High | M | Done | Configurable weights, shown in `/stats` |
| Moments Engine (Firsts/Clutch/Fail, Merge Window) | High | L | Done | Configurable definitions (block_break/death/death_fall/first_death/damage_low_hp/death_explosion/item_gain/boss_kill), persistence, REST/SSE feed |
| Heatmaps / Activity Maps | High | L | Done | Mining/Death bins + hotspots, API export |
| Social Stats (time nearby, shared activity) | Medium | M | Done | Nearby sampler + stored pair seconds + shared kills + names in API `/social/top` |
| Server Health / Cost Index | Medium | M | Done | Chunks/Entities/Hopper/Redstone counters + cost index + HTTP endpoint |
| Season/Timeline Layer (daily/weekly/monthly) | Medium | M | Done | Daily timeline snapshots + range deltas + leaderboards API |
| Live Feed (WS/SSE) for Moments | Medium | L | Done | SSE `/moments/stream` |
| Death Replay Lite | Medium | M | Done | Death snapshot (cause, pos, health, nearby, inventory), API `/death/replay` |
| Story Generator Hook | Low | M | Done | Weekly JSON summary + optional webhook hook |

## Supporting Tasks
- Config/docs for weights/windows/regions/season.
- API endpoints for new features (filters, names for social pairs).
- Tests/validation (DB migrations, config parsing, integration).

## Next Steps
- Moments: more presets (Advancements, Netherite craft), dedupe-first via DB.
- Heatmap: hotspot filters per world/type, optional decay.
- Social: shared activity leaderboards & richer proximity analytics.
- Timeline: per-period leaderboards for more fields + optional CSV export.
- Health: tune weights per hardware, add alerts for spikes.
- Story: templated/Markdown or Discord embed output, S3/web uploads, weekly social/shared-kill highlights.
- Ops: config validation (bad material/entity names), runtime toggles for story/health, cache headers on API, pagination for `/social/top`.
- UX: `/stats top <field>` leaderboards, proximity graph export, party session grouping (3+ players nearby).
