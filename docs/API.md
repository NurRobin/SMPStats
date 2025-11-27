# SMPStats HTTP API

HTTP API served by the plugin’s built-in Java `HttpServer` (no TLS/HTTP2). It starts when `api.enabled` is `true` in `config.yml` and listens on `api.port` (default `8765`).

- Base URL: `http://<host>:<port>`
- Auth: every endpoint requires header `X-API-Key: <api.api_key>`; missing/invalid → `401 Unauthorized` (plain text).
- Methods: only `GET` is implemented.
- Content types: JSON responses use `application/json`; `/moments/stream` uses `text/event-stream`; error strings are plain text.
- OpenAPI: `GET /openapi.json` returns the machine-readable OpenAPI 3.1 document (no auth required).
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
| `x`,`z` | int | Grid indices (chunk-aligned when `grid=16`, otherwise scaled to the requested grid size) |
| `gridSize` | int | Bin size in blocks (8, 16, 32, 64) |
| `count` | number | Weighted hits recorded in the bin (decay applied when configured) |

### Heatmap hotspots map
Returned by `/heatmap/hotspots/*`.

`{ "<hotspotName>": <weightedCount>, ... }`

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
| `tps` | double | Server TPS (first bucket returned by Paper) |
| `memoryUsed` | long | Used memory (bytes) |
| `memoryMax` | long | Max JVM memory (bytes) |
| `chunks` | int | Loaded chunks (all worlds) |
| `entities` | int | Loaded entities (all worlds) |
| `hoppers` | int | Loaded hoppers (all worlds) |
| `redstone` | int | Count of redstone-ish block entities (droppers/dispensers/observers/pistons/etc., excluding hoppers) |
| `costIndex` | double | Weighted cost score (0-100) based on counts and config weights |
| `worlds` | object | Per-world breakdown `{ "<world>": { chunks, entities, hoppers, redstone } }` |
| `hotChunks` | array | Top chunk samples: `{ world, x, z, entityCount, tileEntityCount, topOwner }` |

### Timeline range delta
Returned by `/timeline/range/*`. Same numeric keys as timeline entries but represent deltas over the requested range, plus `from`/`to` day strings.

## Time Range Filters

Many endpoints accept a `from` (and optionally `to`) parameter for human-readable time range filtering. This provides a more intuitive alternative to epoch timestamps.

### Supported Formats

| Format | Example | Description |
| --- | --- | --- |
| **Duration** | `6h`, `30m`, `3d`, `2w` | Relative duration from now |
| **Named Period** | `today`, `yesterday` | Specific day |
| **Named Period** | `this_week`, `last_week` | Week boundaries (Monday-based) |
| **Named Period** | `this_month`, `last_month` | Month boundaries |
| **Epoch (ms)** | `1700000000000` | Raw timestamp (backwards compatibility) |

### Duration Units

| Unit | Meaning | Example |
| --- | --- | --- |
| `s` | Seconds | `30s` = last 30 seconds |
| `m` | Minutes | `15m` = last 15 minutes |
| `h` | Hours | `6h` = last 6 hours |
| `d` | Days | `3d` = last 3 days |
| `w` | Weeks | `2w` = last 2 weeks |

### Examples

```bash
# Get heatmap data for the last 6 hours
curl -H "X-API-Key: $API_KEY" "http://localhost:8765/heatmap/MINING?from=6h"

# Get moments from today only
curl -H "X-API-Key: $API_KEY" "http://localhost:8765/moments/recent?from=today"

# Get this week's leaderboard
curl -H "X-API-Key: $API_KEY" "http://localhost:8765/timeline/leaderboard?from=this_week"

# Get mining heatmap for a specific time window
curl -H "X-API-Key: $API_KEY" "http://localhost:8765/heatmap/MINING?from=3d&to=yesterday"
```

### Backwards Compatibility

The original `since`/`until` (epoch milliseconds) and `days` (integer) parameters still work. If both the new (`from`/`to`) and legacy parameters are provided, the new parameters take precedence.

## Endpoints

### GET `/openapi.json`
- Purpose: machine-readable OpenAPI 3.1 document for the HTTP API.
- Auth: none (public for tooling).

### GET `/stats/{uuid}`
- Returns the latest stats for the given player UUID.
- Errors: `400` if UUID missing/invalid, `404` if no record.

### GET `/stats/all`
- Returns a list of all player stats (live sessions override stored rows, sorted by name).

### GET `/online`
- Returns a JSON array of currently online player names (sorted case-insensitively).

### GET `/moments/recent?limit=&since=&from=`
- Purpose: recent moments snapshot.
- Query: 
  - `limit` (int, default 50): Maximum moments to return.
  - `from` (string): Human-readable time range (e.g., `6h`, `3d`, `today`, `this_week`). See [Time Range Filters](#time-range-filters).
  - `since` (ms epoch): Legacy parameter; use `from` for human-readable syntax. If both are provided, `from` takes precedence.
- Response: list of `MomentEntry`.

### GET `/moments/query?player=&type=&since=&from=&limit=`
- Purpose: filterable moments.
- Query: 
  - `player` (UUID string; ignored if invalid).
  - `type` (moment id; the handler uppercases it before filtering).
  - `from` (string): Human-readable time range. See [Time Range Filters](#time-range-filters).
  - `since` (ms epoch): Legacy parameter; `from` takes precedence if provided.
  - `limit` (int, default 100).
- Response: list of `MomentEntry` ordered by `startedAt` desc.
- Note: because the filter uppercases `type` and the DB stores the raw definition id, only all-uppercase ids will match.

### GET `/moments/stream?since=&from=&limit=`
- Purpose: server-sent event snapshot of moments.
- Query: 
  - `limit` (int, default 50).
  - `from` (string): Human-readable time range. See [Time Range Filters](#time-range-filters).
  - `since` (ms epoch): Legacy parameter; `from` takes precedence if provided.
- Response: `text/event-stream` containing one `data: <MomentEntry-json>\n\n` block per moment. It sends the snapshot once; it is not a long-lived live stream.

### GET `/heatmap/{type}`
- Purpose: aggregated heatmap data (by chunk).
- Path: `type` (uppercased internally). Built-in emitters use `MINING`, `DEATH`, `POSITION`.
- Query:
  - `from` (string): Human-readable time range start (e.g., `6h`, `3d`, `today`). See [Time Range Filters](#time-range-filters).
  - `to` (string): Human-readable time range end.
  - `since` (long, default: 7 days ago): Legacy start timestamp (ms). `from` takes precedence if provided.
  - `until` (long, default: now): Legacy end timestamp (ms). `to` takes precedence if provided.
  - `decay` (double, default: from settings): Half-life in hours for time-decay weighting. Set to 0 to disable decay.
  - `world` (string, default: "world"): World name to filter by.
  - `grid` (int, default: 16): Grid size for aggregation (8, 16, 32, 64).
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

### GET `/timeline/range/{uuid}?days=&from=`
- Purpose: aggregated deltas over a rolling range (weekly/monthly).
- Query: 
  - `from` (string): Human-readable time range (e.g., `3d`, `this_week`). See [Time Range Filters](#time-range-filters). Converted to equivalent days.
  - `days` (int, default 7): Legacy parameter; `from` takes precedence if provided.
- Response: map of deltas between the latest snapshot and the baseline before the window plus `from`/`to` day strings.

### GET `/timeline/leaderboard?days=&from=&limit=`
- Purpose: leaderboard across players for a range.
- Query: 
  - `from` (string): Human-readable time range (e.g., `7d`, `this_week`). See [Time Range Filters](#time-range-filters). Converted to equivalent days.
  - `days` (int, default 7): Legacy parameter; `from` takes precedence if provided.
  - `limit` (int, default 20).
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

---

# Web Dashboard

The web dashboard is a separate HTTP server that provides a browser-based UI for viewing SMPStats data. It serves static HTML/CSS/JS files and exposes a mix of public and admin-only API endpoints.

## Configuration

Dashboard settings are in `config.yml` under the `dashboard` section:

```yaml
dashboard:
  enabled: true
  bind_address: "0.0.0.0"
  port: 8080
  public:
    enabled: true
    show_online_players: true
    show_leaderboards: true
    show_recent_moments: true
    show_server_stats: true
  admin:
    enabled: true
    password: "ChangeThisAdminPassword"
    session_timeout_minutes: 60
```

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Enable/disable the dashboard server |
| `bind_address` | string | `"0.0.0.0"` | Network interface to bind to |
| `port` | int | `8080` | Port number for the dashboard |
| `public.enabled` | boolean | `true` | Enable public (unauthenticated) access |
| `public.show_online_players` | boolean | `true` | Show online players on public dashboard |
| `public.show_leaderboards` | boolean | `true` | Show leaderboards on public dashboard |
| `public.show_recent_moments` | boolean | `true` | Show recent moments on public dashboard |
| `public.show_server_stats` | boolean | `true` | Show aggregated server stats |
| `admin.enabled` | boolean | `true` | Enable admin endpoints |
| `admin.password` | string | `"ChangeThisAdminPassword"` | Password for admin login |
| `admin.session_timeout_minutes` | int | `60` | Admin session timeout in minutes |

## Dashboard Endpoints

The dashboard server provides both public and admin-protected endpoints. Admin endpoints require session-based authentication (login via `/api/admin/login` with password).

### Static Files

| Path | Description |
| --- | --- |
| `/` | Dashboard HTML page (redirects to `/index.html`) |
| `/index.html` | Main dashboard interface |
| `/css/style.css` | Dashboard stylesheet |
| `/js/app.js` | Dashboard JavaScript application |

### Public API Endpoints

These endpoints do not require authentication (when `public.enabled: true`).

#### GET `/api/config`
Returns the public dashboard configuration (what features are enabled).

**Response:**
```json
{
  "public": {
    "enabled": true,
    "showOnlinePlayers": true,
    "showLeaderboards": true,
    "showRecentMoments": true,
    "showServerStats": true
  },
  "adminEnabled": true
}
```

#### GET `/api/public/online`
Returns currently online players.

**Response:**
```json
{
  "count": 5,
  "players": ["Player1", "Player2", "Player3", "Player4", "Player5"]
}
```

#### GET `/api/public/leaderboards`
Returns leaderboards for various statistics.

**Query Parameters:**
- `limit` (int, default 10): Maximum players per leaderboard

**Response:**
```json
{
  "playtime": [
    {"uuid": "...", "name": "Player1", "value": 3600000}
  ],
  "blocks_broken": [...],
  "blocks_placed": [...],
  "mob_kills": [...],
  "player_kills": [...],
  "deaths": [...],
  "distance": [...]
}
```

#### GET `/api/public/moments`
Returns recent moments.

**Query Parameters:**
- `limit` (int, default 20): Maximum moments to return

**Response:**
```json
{
  "moments": [
    {
      "id": 1,
      "playerId": "uuid",
      "type": "KILL_STREAK",
      "title": "Player1 achieved a 5-kill streak!",
      "detail": "5 kills in 30 seconds",
      "world": "world",
      "x": 100,
      "y": 64,
      "z": -200,
      "startedAt": 1234567890000,
      "endedAt": 1234567920000
    }
  ]
}
```

#### GET `/api/public/server-stats`
Returns aggregated server statistics.

**Response:**
```json
{
  "totalPlayers": 150,
  "totalPlaytime": 54000000000,
  "totalBlocksBroken": 1234567,
  "totalBlocksPlaced": 987654,
  "totalMobKills": 45678,
  "totalDeaths": 1234
}
```

### Admin API Endpoints

These endpoints require session-based authentication:

1. **Login:** Send a `POST` request to `/api/admin/login` with the password in the JSON body:
   ```json
   { "password": "<dashboard.admin.password>" }
   ```
   On success, the server responds with a session cookie (`Set-Cookie: smpstats_session=...; HttpOnly; SameSite=Strict; Secure`).

2. **Authenticated requests:** Include the session cookie in the `Cookie` header for all subsequent admin API requests.
   - Requests without a valid session cookie will receive `401 Unauthorized`.

3. **Logout:** Send a request to `/api/admin/logout` to invalidate your session.

#### GET `/api/admin/stats`
Returns all player statistics (same data as `/stats/all` on the main API).

**Response:** Array of `StatsRecord` objects.

#### GET `/api/admin/heatmap/{type}`
Returns heatmap data for the specified type.

**Path Parameters:**
- `type`: Heatmap type (`MINING`, `DEATH`, `POSITION`)

**Query Parameters:**
- `world` (string, default "world"): World name to filter
- `since` (long, default 7 days ago): Start timestamp (ms)
- `until` (long, default now): End timestamp (ms)
- `limit` (int, default 100): Maximum bins to return

**Response:**
```json
{
  "type": "MINING",
  "world": "world",
  "bins": [
    {"chunkX": 10, "chunkZ": -5, "count": 1234}
  ]
}
```

#### GET `/api/admin/timeline/{uuid}`
Returns timeline data for a specific player.

**Path Parameters:**
- `uuid`: Player UUID

**Query Parameters:**
- `limit` (int, default 30): Maximum days to return

**Response:**
```json
{
  "uuid": "...",
  "name": "Player1",
  "timeline": [
    {
      "day": "2024-01-15",
      "playtime_ms": 3600000,
      "blocks_broken": 500,
      ...
    }
  ]
}
```

#### GET `/api/admin/social`
Returns social interaction data (players who played together).

**Query Parameters:**
- `limit` (int, default 50): Maximum pairs to return

**Response:**
```json
{
  "pairs": [
    {
      "a": "uuid1",
      "b": "uuid2",
      "name_a": "Player1",
      "name_b": "Player2",
      "seconds": 7200,
      "shared_kills": 15,
      "shared_player_kills": 2,
      "shared_mob_kills": 13
    }
  ]
}
```

## Dashboard Access

- **Public access:** `http://<host>:<dashboard.port>/` — No authentication required (if `public.enabled: true`)
- **Admin features:** Enter the admin key in the dashboard UI to unlock admin-only views
- **Security:** Use a reverse proxy with TLS for production deployments
