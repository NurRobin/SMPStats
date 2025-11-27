package de.nurrobin.smpstats.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.nurrobin.smpstats.Settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a lightweight OpenAPI 3.1 document for the HTTP API so external tools can ingest it.
 */
class OpenApiDocument {
    private final Settings settings;
    private final String version;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    OpenApiDocument(Settings settings, String version) {
        this.settings = settings;
        this.version = version;
    }

    String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("openapi", "3.1.0");
        root.put("info", info());
        root.put("servers", servers());
        root.put("security", secured());
        root.put("paths", paths());
        root.put("components", components());
        return gson.toJson(root);
    }

    private Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "SMPStats API");
        info.put("summary", "Minecraft analytics and monitoring API");
        info.put("version", version);
        info.put("description", """
                HTTP API exposed by the SMPStats plugin. All endpoints require the X-API-Key header unless noted otherwise.
                """);
        return info;
    }

    private List<Map<String, Object>> servers() {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "http://{host}:{port}");
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("host", Map.of("default", settings.getApiBindAddress()));
        variables.put("port", Map.of("default", Integer.toString(settings.getApiPort())));
        server.put("variables", variables);
        return List.of(server);
    }

    private Map<String, Object> paths() {
        Map<String, Object> paths = new LinkedHashMap<>();

        paths.put("/openapi.json", Map.of(
                "get", Map.of(
                        "summary", "Download OpenAPI document",
                        "description", "Machine-readable API description",
                        "responses", Map.of(
                                "200", jsonResponse("OpenAPI document", Map.of("type", "object"))
                        ),
                        "security", List.of()
                )
        ));

        paths.put("/stats/{playerId}", Map.of(
                "get", Map.of(
                        "summary", "Get stats for a player",
                        "parameters", List.of(
                                pathParam("playerId", "Player UUID", "string", "uuid")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Player stats", ref("StatsRecord")),
                                "400", textResponse("Invalid UUID"),
                                "404", textResponse("Player not found")
                        ),
                        "security", secured()
                )
        ));

        paths.put("/stats/all", Map.of(
                "get", Map.of(
                        "summary", "List stats for all players",
                        "responses", Map.of(
                                "200", jsonResponse("Stats for all players", arraySchema(ref("StatsRecord")))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/online", Map.of(
                "get", Map.of(
                        "summary", "List currently online player names",
                        "responses", Map.of(
                                "200", jsonResponse("Online player names", arraySchema(Map.of("type", "string")))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/moments/recent", Map.of(
                "get", Map.of(
                        "summary", "Recent moments",
                        "parameters", List.of(
                                queryParam("limit", "Maximum entries to return", "integer"),
                                queryParam("from", "Human-readable start (6h, today, this_week)", "string"),
                                queryParam("since", "Start timestamp (epoch millis)", "integer")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Recent moments", arraySchema(ref("MomentEntry")))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/moments/query", Map.of(
                "get", Map.of(
                        "summary", "Query moments",
                        "parameters", List.of(
                                queryParam("limit", "Maximum entries to return", "integer"),
                                queryParam("from", "Human-readable start (6h, today, this_week)", "string"),
                                queryParam("since", "Start timestamp (epoch millis)", "integer"),
                                queryParam("type", "Moment type id", "string"),
                                queryParam("player", "Player UUID to filter", "string")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Filtered moments", arraySchema(ref("MomentEntry")))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/moments/stream", Map.of(
                "get", Map.of(
                        "summary", "Server-sent events stream of recent moments",
                        "parameters", List.of(
                                queryParam("limit", "Maximum events to seed the stream with", "integer"),
                                queryParam("from", "Human-readable start (6h, today, this_week)", "string"),
                                queryParam("since", "Start timestamp (epoch millis)", "integer")
                        ),
                        "responses", Map.of(
                                "200", Map.of(
                                        "description", "SSE stream",
                                        "content", Map.of(
                                                "text/event-stream", Map.of(
                                                        "schema", Map.of("type", "string"),
                                                        "examples", Map.of("event", Map.of(
                                                                "summary", "Example SSE payload",
                                                                "value", "data: {\"type\":\"DIAMOND_RUN\",...}\\n\\n"
                                                        ))
                                                )
                                        )
                                )
                        ),
                        "security", secured()
                )
        ));

        paths.put("/heatmap/{type}", Map.of(
                "get", Map.of(
                        "summary", "Heatmap bins",
                        "parameters", List.of(
                                pathParam("type", "Heatmap type (MINING, DEATH, POSITION, DAMAGE)", "string", null),
                                queryParam("world", "World name (default: world)", "string"),
                                queryParam("from", "Human-readable start", "string"),
                                queryParam("to", "Human-readable end", "string"),
                                queryParam("since", "Start timestamp (epoch millis)", "integer"),
                                queryParam("until", "End timestamp (epoch millis)", "integer"),
                                queryParam("decay", "Half-life in hours", "number"),
                                queryParam("grid", "Grid size (8/16/32/64)", "integer")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Heatmap bins", arraySchema(ref("HeatmapBin"))),
                                "400", textResponse("Invalid type")
                        ),
                        "security", secured()
                )
        ));

        paths.put("/heatmap/hotspots/{type}", Map.of(
                "get", Map.of(
                        "summary", "Named hotspot counters",
                        "parameters", List.of(
                                pathParam("type", "Heatmap type", "string", null)
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Hotspot counts", ref("HeatmapHotspots"))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/timeline/{playerId}", Map.of(
                "get", Map.of(
                        "summary", "Per-day timeline snapshots",
                        "parameters", List.of(
                                pathParam("playerId", "Player UUID", "string", "uuid"),
                                queryParam("limit", "Number of days to return (default 30)", "integer")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Timeline snapshots", arraySchema(ref("TimelineEntry"))),
                                "400", textResponse("Invalid UUID")
                        ),
                        "security", secured()
                )
        ));

        paths.put("/timeline/range/{playerId}", Map.of(
                "get", Map.of(
                        "summary", "Range delta over timeline period",
                        "parameters", List.of(
                                pathParam("playerId", "Player UUID", "string", "uuid"),
                                queryParam("from", "Human-readable start (6h, today, this_week)", "string"),
                                queryParam("days", "Number of days to include (default 7)", "integer")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Range totals", ref("TimelineDelta")),
                                "400", textResponse("Invalid UUID")
                        ),
                        "security", secured()
                )
        ));

        paths.put("/timeline/leaderboard", Map.of(
                "get", Map.of(
                        "summary", "Timeline leaderboard",
                        "parameters", List.of(
                                queryParam("from", "Human-readable start (6h, today, this_week)", "string"),
                                queryParam("days", "Number of days to include (default 7)", "integer"),
                                queryParam("limit", "Number of players (default 20)", "integer")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Leaderboard rows", arraySchema(ref("TimelineDelta")))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/social/top", Map.of(
                "get", Map.of(
                        "summary", "Top shared playtime pairs",
                        "parameters", List.of(
                                queryParam("limit", "Number of rows (default 50)", "integer")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Pairs ordered by shared time", arraySchema(ref("SocialPair")))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/death/replay", Map.of(
                "get", Map.of(
                        "summary", "Recent death replays",
                        "parameters", List.of(
                                queryParam("limit", "Number of entries (default 20)", "integer")
                        ),
                        "responses", Map.of(
                                "200", jsonResponse("Death replay buffer", arraySchema(ref("DeathReplayEntry")))
                        ),
                        "security", secured()
                )
        ));

        paths.put("/health", Map.of(
                "get", Map.of(
                        "summary", "Latest server health snapshot",
                        "responses", Map.of(
                                "200", jsonResponse("Health snapshot", ref("HealthSnapshot")),
                                "404", textResponse("No samples yet")
                        ),
                        "security", secured()
                )
        ));

        return paths;
    }

    private Map<String, Object> components() {
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("StatsRecord", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("uuid", Map.of("type", "string", "format", "uuid")),
                        Map.entry("name", Map.of("type", "string")),
                        Map.entry("firstJoin", Map.of("type", "integer", "format", "int64")),
                        Map.entry("lastJoin", Map.of("type", "integer", "format", "int64")),
                        Map.entry("playtimeMillis", Map.of("type", "integer", "format", "int64")),
                        Map.entry("deaths", Map.of("type", "integer", "format", "int64")),
                        Map.entry("lastDeathCause", Map.of("type", "string", "nullable", true)),
                        Map.entry("playerKills", Map.of("type", "integer", "format", "int64")),
                        Map.entry("mobKills", Map.of("type", "integer", "format", "int64")),
                        Map.entry("blocksPlaced", Map.of("type", "integer", "format", "int64")),
                        Map.entry("blocksBroken", Map.of("type", "integer", "format", "int64")),
                        Map.entry("distanceOverworld", Map.of("type", "number")),
                        Map.entry("distanceNether", Map.of("type", "number")),
                        Map.entry("distanceEnd", Map.of("type", "number")),
                        Map.entry("biomesVisited", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("damageDealt", Map.of("type", "number")),
                        Map.entry("damageTaken", Map.of("type", "number")),
                        Map.entry("itemsCrafted", Map.of("type", "integer", "format", "int64")),
                        Map.entry("itemsConsumed", Map.of("type", "integer", "format", "int64"))
                )
        ));

        schemas.put("MomentEntry", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("id", Map.of("type", "integer", "format", "int64", "nullable", true)),
                        Map.entry("playerId", Map.of("type", "string", "format", "uuid")),
                        Map.entry("type", Map.of("type", "string")),
                        Map.entry("title", Map.of("type", "string")),
                        Map.entry("detail", Map.of("type", "string")),
                        Map.entry("payload", Map.of("type", "string", "nullable", true)),
                        Map.entry("world", Map.of("type", "string")),
                        Map.entry("x", Map.of("type", "integer")),
                        Map.entry("y", Map.of("type", "integer")),
                        Map.entry("z", Map.of("type", "integer")),
                        Map.entry("startedAt", Map.of("type", "integer", "format", "int64")),
                        Map.entry("endedAt", Map.of("type", "integer", "format", "int64"))
                )
        ));

        schemas.put("HeatmapBin", Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of("type", "string"),
                        "world", Map.of("type", "string"),
                        "x", Map.of("type", "integer", "description", "Grid x index"),
                        "z", Map.of("type", "integer", "description", "Grid z index"),
                        "gridSize", Map.of("type", "integer", "description", "Bin size in blocks"),
                        "count", Map.of("type", "number", "description", "Weighted hits recorded in the bin (decay applied when configured)")
                )
        ));

        schemas.put("HeatmapHotspots", Map.of(
                "type", "object",
                "additionalProperties", Map.of("type", "number"),
                "description", "Map of hotspot name to weighted count"
        ));

        schemas.put("TimelineEntry", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("day", Map.of("type", "string", "description", "ISO date YYYY-MM-DD")),
                        Map.entry("playtime_ms", Map.of("type", "integer", "format", "int64")),
                        Map.entry("blocks_broken", Map.of("type", "integer", "format", "int64")),
                        Map.entry("blocks_placed", Map.of("type", "integer", "format", "int64")),
                        Map.entry("player_kills", Map.of("type", "integer", "format", "int64")),
                        Map.entry("mob_kills", Map.of("type", "integer", "format", "int64")),
                        Map.entry("deaths", Map.of("type", "integer", "format", "int64")),
                        Map.entry("distance_overworld", Map.of("type", "number")),
                        Map.entry("distance_nether", Map.of("type", "number")),
                        Map.entry("distance_end", Map.of("type", "number")),
                        Map.entry("damage_dealt", Map.of("type", "number")),
                        Map.entry("damage_taken", Map.of("type", "number")),
                        Map.entry("items_crafted", Map.of("type", "integer", "format", "int64")),
                        Map.entry("items_consumed", Map.of("type", "integer", "format", "int64"))
                )
        ));

        schemas.put("TimelineDelta", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("from", Map.of("type", "string")),
                        Map.entry("to", Map.of("type", "string")),
                        Map.entry("uuid", Map.of("type", "string", "format", "uuid")),
                        Map.entry("name", Map.of("type", "string")),
                        Map.entry("playtime_ms", Map.of("type", "integer", "format", "int64")),
                        Map.entry("blocks_broken", Map.of("type", "integer", "format", "int64")),
                        Map.entry("blocks_placed", Map.of("type", "integer", "format", "int64")),
                        Map.entry("player_kills", Map.of("type", "integer", "format", "int64")),
                        Map.entry("mob_kills", Map.of("type", "integer", "format", "int64")),
                        Map.entry("deaths", Map.of("type", "integer", "format", "int64")),
                        Map.entry("distance_overworld", Map.of("type", "number")),
                        Map.entry("distance_nether", Map.of("type", "number")),
                        Map.entry("distance_end", Map.of("type", "number")),
                        Map.entry("damage_dealt", Map.of("type", "number")),
                        Map.entry("damage_taken", Map.of("type", "number")),
                        Map.entry("items_crafted", Map.of("type", "integer", "format", "int64")),
                        Map.entry("items_consumed", Map.of("type", "integer", "format", "int64"))
                )
        ));

        schemas.put("SocialPair", Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("type", "string", "format", "uuid"),
                        "b", Map.of("type", "string", "format", "uuid"),
                        "name_a", Map.of("type", "string"),
                        "name_b", Map.of("type", "string"),
                        "seconds", Map.of("type", "integer", "format", "int64"),
                        "shared_kills", Map.of("type", "integer", "format", "int64"),
                        "shared_player_kills", Map.of("type", "integer", "format", "int64"),
                        "shared_mob_kills", Map.of("type", "integer", "format", "int64")
                )
        ));

        schemas.put("DeathReplayEntry", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("timestamp", Map.of("type", "integer", "format", "int64")),
                        Map.entry("uuid", Map.of("type", "string", "format", "uuid")),
                        Map.entry("name", Map.of("type", "string")),
                        Map.entry("cause", Map.of("type", "string")),
                        Map.entry("health", Map.of("type", "number")),
                        Map.entry("world", Map.of("type", "string")),
                        Map.entry("x", Map.of("type", "integer")),
                        Map.entry("y", Map.of("type", "integer")),
                        Map.entry("z", Map.of("type", "integer")),
                        Map.entry("fallDistance", Map.of("type", "number")),
                        Map.entry("nearbyPlayers", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("nearbyMobs", Map.of("type", "array", "items", Map.of("type", "string"))),
                        Map.entry("inventory", Map.of("type", "array", "items", Map.of("type", "string")))
                )
        ));

        schemas.put("HealthSnapshot", Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("timestamp", Map.of("type", "integer", "format", "int64")),
                        Map.entry("tps", Map.of("type", "number")),
                        Map.entry("memoryUsed", Map.of("type", "integer", "format", "int64")),
                        Map.entry("memoryMax", Map.of("type", "integer", "format", "int64")),
                        Map.entry("chunks", Map.of("type", "integer")),
                        Map.entry("entities", Map.of("type", "integer")),
                        Map.entry("hoppers", Map.of("type", "integer")),
                        Map.entry("redstone", Map.of("type", "integer")),
                        Map.entry("costIndex", Map.of("type", "number")),
                        Map.entry("worlds", Map.of(
                                "type", "object",
                                "additionalProperties", ref("HealthWorldBreakdown")
                        )),
                        Map.entry("hotChunks", Map.of("type", "array", "items", ref("HealthHotChunk")))
                )
        ));

        schemas.put("HealthWorldBreakdown", Map.of(
                "type", "object",
                "properties", Map.of(
                        "chunks", Map.of("type", "integer"),
                        "entities", Map.of("type", "integer"),
                        "hoppers", Map.of("type", "integer"),
                        "redstone", Map.of("type", "integer")
                )
        ));

        schemas.put("HealthHotChunk", Map.of(
                "type", "object",
                "properties", Map.of(
                        "world", Map.of("type", "string"),
                        "x", Map.of("type", "integer"),
                        "z", Map.of("type", "integer"),
                        "entityCount", Map.of("type", "integer"),
                        "tileEntityCount", Map.of("type", "integer"),
                        "topOwner", Map.of("type", "string")
                )
        ));

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("securitySchemes", Map.of(
                "ApiKeyAuth", Map.of(
                        "type", "apiKey",
                        "in", "header",
                        "name", "X-API-Key"
                )
        ));
        components.put("schemas", schemas);
        return components;
    }

    private List<Map<String, List<String>>> secured() {
        return List.of(Map.of("ApiKeyAuth", List.of()));
    }

    private Map<String, Object> pathParam(String name, String description, String type, String format) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        if (format != null) {
            schema.put("format", format);
        }
        return Map.of(
                "name", name,
                "in", "path",
                "required", true,
                "schema", schema,
                "description", description
        );
    }

    private Map<String, Object> queryParam(String name, String description, String type) {
        return Map.of(
                "name", name,
                "in", "query",
                "required", false,
                "schema", Map.of("type", type),
                "description", description
        );
    }

    private Map<String, Object> jsonResponse(String description, Map<String, Object> schema) {
        return Map.of(
                "description", description,
                "content", Map.of(
                        "application/json", Map.of(
                                "schema", schema
                        )
                )
        );
    }

    private Map<String, Object> textResponse(String description) {
        return Map.of(
                "description", description,
                "content", Map.of(
                        "text/plain", Map.of(
                                "schema", Map.of("type", "string")
                        )
                )
        );
    }

    private Map<String, Object> ref(String schema) {
        return Map.of("$ref", "#/components/schemas/" + schema);
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        return Map.of(
                "type", "array",
                "items", items
        );
    }
}
