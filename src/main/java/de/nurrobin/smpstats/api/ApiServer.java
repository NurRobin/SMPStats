package de.nurrobin.smpstats.api;

import com.google.gson.Gson;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import de.nurrobin.smpstats.health.ServerHealthService;
import de.nurrobin.smpstats.social.SocialPairRow;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ApiServer {
    // Upper bounds for client-supplied numeric parameters
    static final int MAX_LIMIT = 500;
    static final int MAX_DAYS = 365;
    static final int MAX_GRID_SIZE = 512;
    static final long MAX_HEATMAP_LOOKBACK_MS = 90L * 24 * 3600 * 1000;

    private static final int REQUEST_THREADS = 4;
    private static final long AUTH_FAILURE_LOG_INTERVAL_MS = 60_000;

    // Placeholder keys shipped in earlier config.yml versions / code defaults; treated as "not set"
    private static final Set<String> PLACEHOLDER_API_KEYS = Set.of(
            "YouShouldChangeThisKey", "CHANGEME123", "ChangeThisAdminPassword", "changeme"
    );

    private final SMPStats plugin;
    private final StatsService statsService;
    private final Settings settings;
    private final MomentService momentService;
    private final HeatmapService heatmapService;
    private final de.nurrobin.smpstats.timeline.TimelineService timelineService;
    private final ServerHealthService serverHealthService;
    private final OpenApiDocument openApiDocument;
    private final Gson gson = new Gson();
    private final TimeRangeParser timeRangeParser = new TimeRangeParser();

    // Auth failures are logged at most once per interval, with a count of suppressed ones
    private final AtomicLong lastAuthFailureLog = new AtomicLong();
    private final AtomicInteger suppressedAuthFailures = new AtomicInteger();

    private final RateLimiter rateLimiter;

    private HttpServer server;
    private ExecutorService requestExecutor;

    public ApiServer(SMPStats plugin, StatsService statsService, Settings settings, MomentService momentService, HeatmapService heatmapService, de.nurrobin.smpstats.timeline.TimelineService timelineService, ServerHealthService serverHealthService) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.settings = settings;
        this.momentService = momentService;
        this.heatmapService = heatmapService;
        this.timelineService = timelineService;
        this.serverHealthService = serverHealthService;
        this.openApiDocument = new OpenApiDocument(settings, resolvePluginVersion(plugin));
        int perSecond = settings.getApiRateLimitPerSecond();
        this.rateLimiter = perSecond > 0 ? new RateLimiter(perSecond) : null;
    }

    private String resolvePluginVersion(SMPStats plugin) {
        PluginDescriptionFile description = plugin != null ? plugin.getDescription() : null;
        return description != null ? description.getVersion() : "unknown";
    }

    public void start() {
        if (isPlaceholderKey(settings.getApiKey())) {
            plugin.getLogger().warning("HTTP API NOT started: api.api_key is empty or still a default placeholder. "
                    + "Set a long random secret in config.yml and reload to enable the API.");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(settings.getApiBindAddress(), settings.getApiPort()), 0);
        } catch (IOException | RuntimeException e) {
            // RuntimeException: an api.bind_address that does not resolve fails with an unchecked exception
            plugin.getLogger().severe("HTTP API konnte nicht gestartet werden (api.bind_address "
                    + settings.getApiBindAddress() + ", api.port " + settings.getApiPort() + "): " + e);
            return;
        }

        server.createContext("/openapi.json", new OpenApiHandler());
        server.createContext("/stats", new StatsHandler());
        server.createContext("/online", new OnlineHandler());
        server.createContext("/moments/recent", new RecentMomentsHandler());
        server.createContext("/moments/query", new QueryMomentsHandler());
        server.createContext("/moments/stream", new MomentsStreamHandler());
        server.createContext("/heatmap", new HeatmapHandler());
        server.createContext("/heatmap/hotspots", new HeatmapHotspotHandler());
        server.createContext("/timeline", new TimelineHandler());
        server.createContext("/social/top", new SocialTopHandler());
        server.createContext("/death/replay", new DeathReplayHandler());
        server.createContext("/health", new HealthHandler());
        AtomicInteger threadCounter = new AtomicInteger();
        requestExecutor = Executors.newFixedThreadPool(REQUEST_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "SMPStats-API-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(requestExecutor);
        server.start();

        plugin.getLogger().info("HTTP API läuft auf Port " + settings.getApiPort());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (requestExecutor != null) {
            requestExecutor.shutdownNow();
            requestExecutor = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    static boolean isPlaceholderKey(String key) {
        return key == null || key.isBlank() || PLACEHOLDER_API_KEYS.contains(key.trim());
    }

    /** Token bucket per client IP; the key is checked only after this, so wrong keys are throttled too. */
    private boolean allowRequest(HttpExchange exchange) throws IOException {
        if (rateLimiter == null) {
            return true;
        }
        InetSocketAddress remote = exchange.getRemoteAddress();
        String client = remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
        if (rateLimiter.tryAcquire(client)) {
            return true;
        }
        exchange.getResponseHeaders().add("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds()));
        sendText(exchange, 429, "Too Many Requests");
        return false;
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getRequestHeaders();
        String provided = headers.getFirst("X-API-Key");
        String expected = settings.getApiKey();
        boolean valid = provided != null && !provided.isEmpty()
                && expected != null && !expected.isEmpty()
                && MessageDigest.isEqual(sha256(provided), sha256(expected));
        if (!valid) {
            logAuthFailure(exchange);
            sendText(exchange, 401, "Unauthorized");
            return false;
        }
        return true;
    }

    private void logAuthFailure(HttpExchange exchange) {
        long now = System.currentTimeMillis();
        long last = lastAuthFailureLog.get();
        if (now - last >= AUTH_FAILURE_LOG_INTERVAL_MS && lastAuthFailureLog.compareAndSet(last, now)) {
            int suppressed = suppressedAuthFailures.getAndSet(0);
            InetSocketAddress remote = exchange.getRemoteAddress();
            String source = remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
            plugin.getLogger().warning("HTTP API rejected request with missing/invalid API key from " + source
                    + (suppressed > 0 ? " (" + suppressed + " more rejected since last report)" : ""));
        } else {
            suppressedAuthFailures.incrementAndGet();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private java.util.Optional<String> queryParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return java.util.Optional.empty();
        }
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(name)) {
                return java.util.Optional.of(kv[1]);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Thrown by the parameter helpers; translated into an HTTP 400 JSON response.
     */
    private static final class BadRequestException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BadRequestException(String message) {
            super(message, null, false, false);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int parseIntParam(String name, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid integer for parameter '" + name + "'");
        }
    }

    private long parseLongParam(String name, String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid number for parameter '" + name + "'");
        }
    }

    /** Reads an integer parameter, clamped to [1, max]. */
    private int boundedIntParam(URI uri, String name, int defaultValue, int max) {
        return queryParam(uri, name)
                .map(raw -> clamp(parseIntParam(name, raw), 1, max))
                .orElse(defaultValue);
    }

    private int limitParam(URI uri, int defaultValue) {
        return boundedIntParam(uri, "limit", defaultValue, MAX_LIMIT);
    }

    /** Resolves 'from' (human-readable) or 'since' (epoch ms) into a start timestamp. */
    private long sinceParam(URI uri, long defaultValue) {
        return queryParam(uri, "from")
                .flatMap(timeRangeParser::parse)
                .map(TimeRangeParser.TimeRange::since)
                .orElseGet(() -> queryParam(uri, "since").map(raw -> parseLongParam("since", raw)).orElse(defaultValue));
    }

    /** Resolves 'from' (human-readable) or 'days' (integer) into a day count clamped to [1, MAX_DAYS]. */
    private int daysParam(URI uri, int defaultValue) {
        return queryParam(uri, "from")
                .flatMap(timeRangeParser::parse)
                .map(range -> {
                    double days = Math.ceil((System.currentTimeMillis() - range.since()) / (24.0 * 3600 * 1000));
                    return (int) Math.max(1, Math.min(MAX_DAYS, days));
                })
                .orElseGet(() -> boundedIntParam(uri, "days", defaultValue, MAX_DAYS));
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendJsonString(HttpExchange exchange, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * Base class for endpoints that require the API key. Malformed parameters yield HTTP 400 with a JSON error.
     */
    private abstract class AuthorizedHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            if (!allowRequest(exchange) || !authorize(exchange)) {
                return;
            }
            try {
                handleAuthorized(exchange, exchange.getRequestURI());
            } catch (BadRequestException e) {
                sendJson(exchange, 400, Map.of("error", e.getMessage()));
            } catch (NumberFormatException e) {
                // e.g. an out-of-range relative duration in a 'from' parameter
                sendJson(exchange, 400, Map.of("error", "Invalid parameter format"));
            }
        }

        protected abstract void handleAuthorized(HttpExchange exchange, URI uri) throws IOException;
    }

    private class OpenApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Documentation should stay reachable without authentication for tool discovery
            if (!allowRequest(exchange)) {
                return;
            }
            sendJsonString(exchange, 200, openApiDocument.toJson());
        }
    }

    private class StatsHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            String path = uri.getPath().substring("/stats".length()); // can be "" or "/<uuid>" or "/all"
            if (path.isEmpty() || path.equals("/")) {
                sendText(exchange, 400, "Missing player id");
                return;
            }

            if (path.equalsIgnoreCase("/all")) {
                List<StatsRecord> all = statsService.getAllStats();
                sendJson(exchange, 200, all);
                return;
            }

            String id = path.startsWith("/") ? path.substring(1) : path;
            try {
                UUID uuid = UUID.fromString(id);
                Optional<StatsRecord> record = statsService.getStats(uuid);
                if (record.isPresent()) {
                    sendJson(exchange, 200, record.get());
                } else {
                    sendText(exchange, 404, "Not found");
                }
            } catch (IllegalArgumentException ex) {
                sendText(exchange, 400, "Invalid UUID");
            }
        }
    }

    private class OnlineHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            sendJson(exchange, 200, statsService.getOnlineNames());
        }
    }

    private class RecentMomentsHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            int limit = limitParam(uri, 50);
            long since = sinceParam(uri, -1L);

            List<?> recent = since > 0 ? momentService.getMomentsSince(since, limit) : momentService.getRecentMoments(limit);
            sendJson(exchange, 200, recent);
        }
    }

    private class HeatmapHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            String path = uri.getPath().substring("/heatmap".length()); // expected /heatmap/<type>
            if (path.isEmpty() || "/".equals(path)) {
                sendText(exchange, 400, "Missing heatmap type");
                return;
            }
            String typeRaw = path.startsWith("/") ? path.substring(1) : path;

            // Support both epoch timestamps (since/until) and human-readable time ranges (from/to)
            long now = System.currentTimeMillis();
            long defaultSince = now - 7L * 24 * 3600 * 1000;

            long since = sinceParam(uri, defaultSince);
            // Bound the scanned window so a single request cannot aggregate the whole history
            since = Math.max(since, now - MAX_HEATMAP_LOOKBACK_MS);

            // Parse 'to' parameter (human-readable) with fallback to 'until' (epoch)
            long until = queryParam(uri, "to")
                    .flatMap(timeRangeParser::parse)
                    .map(TimeRangeParser.TimeRange::until)
                    .orElseGet(() -> queryParam(uri, "until").map(raw -> parseLongParam("until", raw)).orElse(now));

            double decay = settings.getHeatmapDecayHalfLifeHours();
            Optional<String> decayRaw = queryParam(uri, "decay");
            if (decayRaw.isPresent()) {
                try {
                    decay = Double.parseDouble(decayRaw.get().trim());
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Invalid number for parameter 'decay'");
                }
                if (!Double.isFinite(decay)) {
                    throw new BadRequestException("Invalid number for parameter 'decay'");
                }
            }
            String world = queryParam(uri, "world").orElse("world");
            int gridSize = queryParam(uri, "grid").map(raw -> parseIntParam("grid", raw)).orElse(16);
            if (gridSize <= 0) gridSize = 16;
            gridSize = Math.min(gridSize, MAX_GRID_SIZE);

            try {
                sendJson(exchange, 200, heatmapService.generateHeatmap(typeRaw.toUpperCase(), world, since, until, decay, gridSize));
            } catch (HeatmapService.HeatmapBusyException e) {
                exchange.getResponseHeaders().set("Retry-After", "5");
                sendText(exchange, 503, "Heatmap busy, retry shortly");
            } catch (IllegalArgumentException e) {
                sendText(exchange, 400, "Invalid heatmap type");
            }
        }
    }

    private class HeatmapHotspotHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            String path = uri.getPath().substring("/heatmap/hotspots".length()); // /heatmap/hotspots/<type>
            if (path.isEmpty() || "/".equals(path)) {
                sendText(exchange, 400, "Missing heatmap type");
                return;
            }
            String typeRaw = path.startsWith("/") ? path.substring(1) : path;
            sendJson(exchange, 200, heatmapService.loadHotspots(typeRaw.toUpperCase()));
        }
    }

    private class QueryMomentsHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            int limit = limitParam(uri, 100);
            long since = sinceParam(uri, -1L);

            String type = queryParam(uri, "type").orElse(null);
            java.util.Optional<String> playerParam = queryParam(uri, "player");
            java.util.UUID playerId = null;
            if (playerParam.isPresent()) {
                try {
                    playerId = java.util.UUID.fromString(playerParam.get());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid player UUID");
                }
            }
            List<?> moments = momentService.queryMoments(playerId, type != null ? type.toUpperCase() : null, since, limit);
            sendJson(exchange, 200, moments);
        }
    }

    private class MomentsStreamHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            int limit = limitParam(uri, 50);
            long since = sinceParam(uri, -1L);

            List<?> moments = since > 0 ? momentService.getMomentsSince(since, limit) : momentService.getRecentMoments(limit);
            StringBuilder sb = new StringBuilder();
            for (Object m : moments) {
                sb.append("data: ").append(gson.toJson(m)).append("\n\n");
            }
            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }

    private class TimelineHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            String path = uri.getPath().substring("/timeline".length());
            if (path.startsWith("/leaderboard")) {
                int days = daysParam(uri, 7);
                int limit = limitParam(uri, 20);
                sendJson(exchange, 200, timelineLeaderboard(days, limit));
                return;
            }
            if (path.startsWith("/range")) {
                String[] parts = path.split("/");
                if (parts.length < 3) {
                    sendText(exchange, 400, "Missing player id");
                    return;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(parts[2]);
                } catch (IllegalArgumentException e) {
                    sendText(exchange, 400, "Invalid UUID");
                    return;
                }
                int days = daysParam(uri, 7);
                try {
                    sendJson(exchange, 200, statsService.getStorage().loadTimelineRange(uuid, days));
                } catch (Exception e) {
                    plugin.getLogger().warning("Timeline range query failed: " + e.getMessage());
                    sendText(exchange, 500, "Error");
                }
                return;
            }
            if (path.isEmpty() || "/".equals(path)) {
                sendText(exchange, 400, "Missing player id");
                return;
            }
            String id = path.startsWith("/") ? path.substring(1) : path;
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                sendText(exchange, 400, "Invalid UUID");
                return;
            }
            int limit = limitParam(uri, 30);
            sendJson(exchange, 200, timelineService != null ? timelineServiceQuery(uuid, limit) : List.of());
        }

        private List<?> timelineServiceQuery(UUID uuid, int limit) {
            try {
                return statsService.getStorage().loadTimeline(uuid, limit);
            } catch (Exception e) {
                plugin.getLogger().warning("Timeline query failed: " + e.getMessage());
                return List.of();
            }
        }

        private List<Map<String, Object>> timelineLeaderboard(int days, int limit) {
            List<Map<String, Object>> out = new ArrayList<>();
            try {
                for (Map<String, Object> row : statsService.getStorage().loadTimelineLeaderboard(days, limit)) {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    try {
                        UUID uuid = UUID.fromString(row.get("uuid").toString());
                        copy.put("name", resolveName(uuid));
                    } catch (IllegalArgumentException ignored) {
                    }
                    out.add(copy);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Timeline leaderboard failed: " + e.getMessage());
            }
            return out;
        }
    }

    private class SocialTopHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            int limit = limitParam(uri, 50);
            List<Map<String, Object>> out = new ArrayList<>();
            try {
                for (SocialPairRow row : statsService.getStorage().loadTopSocial(limit)) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("a", row.uuidA().toString());
                    map.put("b", row.uuidB().toString());
                    map.put("name_a", resolveName(row.uuidA()));
                    map.put("name_b", resolveName(row.uuidB()));
                    map.put("seconds", row.seconds());
                    map.put("shared_kills", row.sharedKills());
                    map.put("shared_player_kills", row.sharedPlayerKills());
                    map.put("shared_mob_kills", row.sharedMobKills());
                    out.add(map);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load social top: " + e.getMessage());
            }
            sendJson(exchange, 200, out);
        }
    }

    private class DeathReplayHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            int limit = limitParam(uri, 20);
            try {
                sendJson(exchange, 200, statsService.getStorage().loadDeathReplays(limit));
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load death replays: " + e.getMessage());
                sendText(exchange, 500, "Error");
            }
        }
    }

    private class HealthHandler extends AuthorizedHandler {
        @Override
        protected void handleAuthorized(HttpExchange exchange, URI uri) throws IOException {
            if (serverHealthService == null) {
                sendJson(exchange, 200, Map.of());
                return;
            }
            var snapshot = serverHealthService.getLatest();
            if (snapshot == null) {
                sendText(exchange, 404, "No samples yet");
                return;
            }
            sendJson(exchange, 200, snapshot);
        }
    }

    // Package-private accessors to allow unit tests to exercise handler logic without running an HTTP server
    HttpHandler statsHandler() { return new StatsHandler(); }
    HttpHandler onlineHandler() { return new OnlineHandler(); }
    HttpHandler recentMomentsHandler() { return new RecentMomentsHandler(); }
    HttpHandler queryMomentsHandler() { return new QueryMomentsHandler(); }
    HttpHandler momentsStreamHandler() { return new MomentsStreamHandler(); }
    HttpHandler heatmapHandler() { return new HeatmapHandler(); }
    HttpHandler heatmapHotspotHandler() { return new HeatmapHotspotHandler(); }
    HttpHandler timelineHandler() { return new TimelineHandler(); }
    HttpHandler socialTopHandler() { return new SocialTopHandler(); }
    HttpHandler deathReplayHandler() { return new DeathReplayHandler(); }
    HttpHandler healthHandler() { return new HealthHandler(); }
    HttpHandler openApiHandler() { return new OpenApiHandler(); }

    private String resolveName(UUID uuid) {
        return statsService.getStats(uuid)
                .map(StatsRecord::getName)
                .orElse(uuid.toString());
    }
}
