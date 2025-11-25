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
| Moments Engine (Firsts/Clutch/Fail, Merge Window) | High | L | In Progress | Configurable definitions (block_break/death/death_fall/first_death/damage_low_hp/death_explosion/item_gain/boss_kill), persistence, REST/SSE feed |
| Heatmaps / Activity Maps | High | L | In Progress | Mining/Death bins + hotspots, API export |
| Social Stats (time nearby, shared activity) | Medium | M | In Progress | Nearby sampler + stored pair seconds, API `/social/top`; shared kills pending |
| Server Health / Cost Index | Medium | M | Todo | Chunks/Entities/Hopper/Redstone counters |
| Season/Timeline Layer (daily/weekly/monthly) | Medium | M | In Progress | Daily timeline snapshots + API |
| Live Feed (WS/SSE) for Moments | Medium | L | Done | SSE `/moments/stream` |
| Death Replay Lite | Medium | M | Done | Death snapshot (cause, pos, health, nearby, inventory), API `/death/replay` |
| Story Generator Hook | Low | M | Todo | Weekly JSON summary + optional LLM hook |

## Supporting Tasks
- Config/docs for weights/windows/regions/season.
- API endpoints for new features (filters, names for social pairs).
- Tests/validation (DB migrations, config parsing, integration).

## Next Steps
- Moments: more presets (Advancements, Netherite craft), dedupe-first via DB.
- Heatmap: hotspot filters per world/type, optional decay.
- Social: resolve UUID->name in API, shared kill counters, leaderboards.
- Timeline: add ranges (weekly/monthly) and leaderboards.***
