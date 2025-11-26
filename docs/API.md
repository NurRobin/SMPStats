# SMPStats HTTP API

HTTP API served by the plugin’s built-in Java `HttpServer` (no TLS/HTTP2). It starts when `api.enabled` is `true` in `config.yml` and listens on `api.port` (default `8765`).

- Base URL: `http://<host>:<port>`
- Auth: every endpoint requires header `X-API-Key: <api.api_key>`; missing/invalid → `401 Unauthorized` (plain text).
- Methods: only `GET` is implemented.
- Content types: JSON responses use `application/json`; `/moments/stream` uses `text/event-stream`; error strings are plain text.
- Time fields: epoch milliseconds unless noted. Coordinates are block coordinates unless noted. Query numbers must be valid integers; otherwise the server throws and you’ll get a 500.
- HTTPS: terminate TLS in a reverse proxy if you expose the API publicly.

## Data shapes

### Player stats (`StatsRecord`)
Returned by `/stats/*`.

| Field | Type | Meaning |
| --- | --- | --- |
| `uuid` | string (UUID) | Player UUID |
| `name` | string | Last known player name |
| `firstJoin` | long | First join timestamp (ms since epoch) |
| `lastJoin` | long | Last join/quit timestamp (ms since epoch) |
| `playtimeMillis` | long | Playtime in milliseconds |
| `deaths` | long | Total deaths |
| `lastDeathCause` | string\|null | Last recorded death cause |
| `playerKills` | long | Player kills |
| `mobKills` | long | Mob kills |
| `blocksPlaced` | long | Blocks placed |
| `blocksBroken` | long | Blocks broken |
| `distanceOverworld` | double | Travel distance in the Overworld (blocks) |
| `distanceNether` | double | Travel distance in the Nether (blocks) |
| `distanceEnd` | double | Travel distance in the End (blocks) |
| `biomesVisited` | string[] | Visited biome names |
| `damageDealt` | double | Total damage dealt |
| `damageTaken` | double | Total damage taken |
| `itemsCrafted` | long | Crafted item count |
| `itemsConsumed` | long | Consumed item count |

### Moment entry (`MomentEntry`)
Returned by `/moments/*`.

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | long | Database id |
| `playerId` | string (UUID) | Player UUID |
| `type` | string | Moment definition id (from `moments.definitions.<id>`) |
| `title` | string | Rendered title with placeholders resolved |
| `detail` | string | Rendered detail text |
| `payload` | string\|null | Extra JSON payload as string (e.g. counts/fall distance) |
| `world` | string | World name |
| `x`,`y`,`z` | int | Block position |
| `startedAt` | long | Window start timestamp (ms) |
| `endedAt` | long | Window end timestamp (ms) |

### Heatmap bin (`HeatmapBin`)
Returned by `/heatmap/*`.

| Field | Type | Meaning |
| --- | --- | --- |
| `type` | string | Heatmap type (e.g. `MINING`, `DEATH`) |
| `world` | string | World name |
| `chunkX`,`chunkZ` | int | Chunk coordinates |
| `count` | long | Hits recorded in the bin |

### Timeline day entry
Returned by `/timeline/*`. Each entry is a map with keys:

`day` (ISO `YYYY-MM-DD` string), `playtime_ms`, `blocks_broken`, `blocks_placed`, `player_kills`, `mob_kills`, `deaths`, `distance_overworld`, `distance_nether`, `distance_end`, `damage_dealt`, `damage_taken`, `items_crafted`, `items_consumed`. Values are cumulative snapshots for that day, not per-day deltas.

### Social pair row
Returned by `/social/top`.

`{ "a": "<uuidA>", "b": "<uuidB>", "name_a": "<nameA>", "name_b": "<nameB>", "seconds": <long>, "shared_kills": <long>, "shared_player_kills": <long>, "shared_mob_kills": <long> }` — `seconds` is time both players were within 16 blocks of each other; shared kill counters are kills made while the other player was within the configured radius.

### Death replay entry
Returned by `/death/replay`.

`timestamp`, `uuid`, `name`, `cause`, `health`, `world`, `x`, `y`, `z`, `fallDistance`, `nearbyPlayers` (string[]), `nearbyMobs` (string[]), `inventory` (string[]; only populated if `death_replay.include_inventory_items` is true).

### Health snapshot
Returned by `/health`.

| Field | Type | Meaning |
| --- | --- | --- |
| `timestamp` | long | Capture time (ms since epoch) |
| `chunks` | int | Loaded chunks (all worlds) |
| `entities` | int | Loaded entities (all worlds) |
| `hoppers` | int | Loaded hoppers (all worlds) |
| `redstone` | int | Count of redstone-ish block entities (droppers/dispensers/observers/pistons/etc., excluding hoppers) |
| `costIndex` | double | Weighted cost score (0-100) based on counts and config weights |
| `worlds` | object | Per-world breakdown `{ "<world>": { chunks, entities, hoppers, redstone } }` |

### Timeline range delta
Returned by `/timeline/range/*`. Same numeric keys as timeline entries but represent deltas over the requested range, plus `from`/`to` day strings.

## Endpoints

### GET `/stats/{uuid}`
- Returns the latest stats for the given player UUID.
- Errors: `400` if UUID missing/invalid, `404` if no record.

### GET `/stats/all`
- Returns a list of all player stats (live sessions override stored rows, sorted by name).

### GET `/online`
- Returns a JSON array of currently online player names (sorted case-insensitively).

### GET `/moments/recent?limit=&since=`
- Purpose: recent moments snapshot.
- Query: `limit` (int, default 50), `since` (ms epoch; if >0 returns moments with `startedAt >= since` ascending, else the latest moments descending).
- Response: list of `MomentEntry`.

### GET `/moments/query?player=&type=&since=&limit=`
- Purpose: filterable moments.
- Query: `player` (UUID string; ignored if invalid), `type` (moment id; the handler uppercases it before filtering), `since` (ms epoch), `limit` (int, default 100).
- Response: list of `MomentEntry` ordered by `startedAt` desc.
- Note: because the filter uppercases `type` and the DB stores the raw definition id, only all-uppercase ids will match.

### GET `/moments/stream?since=&limit=`
- Purpose: server-sent event snapshot of moments.
- Query: `limit` (int, default 50), `since` (ms epoch; if >0 returns entries from that point, otherwise the latest).
- Response: `text/event-stream` containing one `data: <MomentEntry-json>\n\n` block per moment. It sends the snapshot once; it is not a long-lived live stream.

### GET `/heatmap/{type}`
- Purpose: aggregated heatmap data (by chunk).
- Path: `type` (uppercased internally). Built-in emitters use `MINING`, `DEATH`, `POSITION`.
- Query:
  - `since` (long, default: 7 days ago): Start timestamp (ms).
  - `until` (long, default: now): End timestamp (ms).
  - `decay` (double, default: from settings): Half-life in hours for time-decay weighting. Set to 0 to disable decay.
  - `world` (string, default: "world"): World name to filter by.
- Response: list of `HeatmapBin` records (chunkX, chunkZ, count/weight) ordered by weight descending.
- Errors: `400 Invalid heatmap type` if an illegal value triggers an `IllegalArgumentException`.

### GET `/heatmap/hotspots/{type}`
- Purpose: aggregated counts for configured hotspots.
- Path: `type` (uppercased internally).
- Response: JSON object `{ "<hotspotName>": <count>, ... }`.

### GET `/timeline/{uuid}?limit=`
- Purpose: daily snapshots for a player.
- Query: `limit` (int, default 30).
- Response: list of timeline day maps ordered by `day` desc. If the timeline feature is disabled, an empty list is returned.
- Note: values are cumulative totals captured at autosave times, not per-day diffs.

### GET `/timeline/range/{uuid}?days=`
- Purpose: aggregated deltas over a rolling range (weekly/monthly).
- Query: `days` (int, default 7).
- Response: map of deltas between the latest snapshot and the baseline before the window plus `from`/`to` day strings.

### GET `/timeline/leaderboard?days=&limit=`
- Purpose: leaderboard across players for a range.
- Query: `days` (int, default 7), `limit` (int, default 20).
- Response: list of rows ordered by `playtime_ms` delta (includes `uuid` + `name` and deltas for the tracked fields).

### GET `/social/top?limit=`
- Purpose: pairs of players who spent the most time near each other.
- Query: `limit` (int, default 50).
- Response: list of social pair rows ordered by `seconds` desc. If social tracking is disabled, the list will stay empty.

### GET `/death/replay?limit=`
- Purpose: recent captured death snapshots.
- Query: `limit` (int, default 20).
- Response: list of `DeathReplayEntry` ordered by timestamp desc.
- Errors: returns `500 Error` on storage failures (logged server-side).

### GET `/health`
- Purpose: latest server health snapshot.
- Response: `HealthSnapshot` with global + per-world counts and cost index. If no sample exists yet, returns `404 No samples yet`.

## Quick usage example

```bash
curl -H "X-API-Key: $API_KEY" http://localhost:8765/stats/all
curl -H "X-API-Key: $API_KEY" "http://localhost:8765/moments/recent?limit=20"
curl -H "X-API-Key: $API_KEY" "http://localhost:8765/heatmap/MINING"
```
