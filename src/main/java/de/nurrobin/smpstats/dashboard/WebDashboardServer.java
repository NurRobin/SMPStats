package de.nurrobin.smpstats.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.health.ServerHealthService;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.social.SocialPairRow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Web dashboard server providing a user-friendly interface for SMPStats data.
 * Supports both public (unauthenticated) and admin (authenticated) sections.
 */
public class WebDashboardServer {
    private final SMPStats plugin;
    private final StatsService statsService;
    private final Settings settings;
    private final MomentService momentService;
    private final HeatmapService heatmapService;
    private final ServerHealthService serverHealthService;
    private final Gson gson;
    
    private HttpServer server;
    private ScheduledExecutorService sessionCleanupExecutor;
    
    // Session management for admin panel
    private final Map<String, AdminSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    private static final String SESSION_COOKIE_NAME = "smpstats_session";
    private static final long SESSION_CLEANUP_INTERVAL_MINUTES = 10;
    
    public WebDashboardServer(SMPStats plugin, StatsService statsService, Settings settings,
                              MomentService momentService, HeatmapService heatmapService,
                              ServerHealthService serverHealthService) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.settings = settings;
        this.momentService = momentService;
        this.heatmapService = heatmapService;
        this.serverHealthService = serverHealthService;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    public void start() {
        Settings.DashboardSettings dashSettings = settings.getDashboardSettings();
        if (!dashSettings.enabled()) {
            plugin.getLogger().info("Web Dashboard disabled in config.yml");
            return;
        }
        
        try {
            server = HttpServer.create(new InetSocketAddress(dashSettings.bindAddress(), dashSettings.port()), 0);
        } catch (IOException e) {
            plugin.getLogger().severe("Web Dashboard could not start: " + e.getMessage());
            return;
        }
        
        // Static assets
        server.createContext("/", new StaticHandler());
        server.createContext("/css/", new StaticHandler());
        server.createContext("/js/", new StaticHandler());
        
        // Public API endpoints
        server.createContext("/api/public/online", new PublicOnlineHandler());
        server.createContext("/api/public/leaderboard", new PublicLeaderboardHandler());
        server.createContext("/api/public/moments", new PublicMomentsHandler());
        server.createContext("/api/public/stats", new PublicStatsHandler());
        server.createContext("/api/public/config", new PublicConfigHandler());
        
        // Admin authentication
        server.createContext("/api/admin/login", new AdminLoginHandler());
        server.createContext("/api/admin/logout", new AdminLogoutHandler());
        server.createContext("/api/admin/check", new AdminCheckHandler());
        
        // Admin API endpoints (require authentication)
        server.createContext("/api/admin/health", new AdminHealthHandler());
        server.createContext("/api/admin/heatmap", new AdminHeatmapHandler());
        server.createContext("/api/admin/social", new AdminSocialHandler());
        server.createContext("/api/admin/deaths", new AdminDeathsHandler());
        server.createContext("/api/admin/player", new AdminPlayerHandler());
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        // Start session cleanup task
        sessionCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        sessionCleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            SESSION_CLEANUP_INTERVAL_MINUTES,
            SESSION_CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        plugin.getLogger().info("Web Dashboard running on " + dashSettings.bindAddress() + ":" + dashSettings.port());
    }
    
    public void stop() {
        if (sessionCleanupExecutor != null) {
            sessionCleanupExecutor.shutdownNow();
        }
        if (server != null) {
            server.stop(0);
            sessions.clear();
        }
    }
    
    private void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    // ============== Helper Methods ==============
    
    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
    
    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
    
    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
    
    private void sendStatic(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/dashboard" + resourcePath)) {
            if (is == null) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            byte[] data = is.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=3600");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }
    
    private Optional<String> queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isEmpty()) return Optional.empty();
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(name)) {
                return Optional.of(kv[1]);
            }
        }
        return Optional.empty();
    }
    
    private String generateSessionId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private Optional<AdminSession> getSession(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return Optional.empty();
        
        for (String part : cookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(SESSION_COOKIE_NAME + "=")) {
                String sessionId = trimmed.substring(SESSION_COOKIE_NAME.length() + 1);
                AdminSession session = sessions.get(sessionId);
                if (session != null && !session.isExpired()) {
                    return Optional.of(session);
                }
            }
        }
        return Optional.empty();
    }
    
    private boolean isAdminAuthenticated(HttpExchange exchange) {
        return getSession(exchange).isPresent();
    }
    
    private void setSessionCookie(HttpExchange exchange, String sessionId, int maxAgeSeconds) {
        String cookie = SESSION_COOKIE_NAME + "=" + sessionId + "; Path=/; HttpOnly; SameSite=Strict; Secure; Max-Age=" + maxAgeSeconds;
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }
    
    private void clearSessionCookie(HttpExchange exchange) {
        String cookie = SESSION_COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Strict; Secure; Max-Age=0";
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }
    
    // ============== Session Management ==============
    
    record AdminSession(String id, long createdAt, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
    
    /**
     * Constant-time password comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
    
    // ============== Handlers ==============
    
    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Default to index.html
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }
            
            // Determine content type
            String contentType = "text/plain";
            if (path.endsWith(".html")) {
                contentType = "text/html; charset=utf-8";
            } else if (path.endsWith(".css")) {
                contentType = "text/css; charset=utf-8";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript; charset=utf-8";
            } else if (path.endsWith(".json")) {
                contentType = "application/json; charset=utf-8";
            } else if (path.endsWith(".png")) {
                contentType = "image/png";
            } else if (path.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else if (path.endsWith(".ico")) {
                contentType = "image/x-icon";
            }
            
            sendStatic(exchange, path, contentType);
        }
    }
    
    // ============== Public Handlers ==============
    
    private class PublicConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Settings.DashboardSettings dash = settings.getDashboardSettings();
            Settings.PublicSettings pub = dash.publicSettings();
            Settings.AdminSettings admin = dash.adminSettings();
            
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("publicEnabled", pub.enabled());
            config.put("showOnlinePlayers", pub.showOnlinePlayers());
            config.put("showLeaderboards", pub.showLeaderboards());
            config.put("showRecentMoments", pub.showRecentMoments());
            config.put("showServerStats", pub.showServerStats());
            config.put("adminEnabled", admin.enabled());
            
            sendJson(exchange, 200, config);
        }
    }
    
    private class PublicOnlineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!settings.getDashboardSettings().publicSettings().showOnlinePlayers()) {
                sendJson(exchange, 403, Map.of("error", "Online players display is disabled"));
                return;
            }
            
            List<String> onlineNames = statsService.getOnlineNames();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("count", onlineNames.size());
            response.put("players", onlineNames);
            sendJson(exchange, 200, response);
        }
    }
    
    private class PublicLeaderboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!settings.getDashboardSettings().publicSettings().showLeaderboards()) {
                sendJson(exchange, 403, Map.of("error", "Leaderboards display is disabled"));
                return;
            }
            
            int limit;
            int days;
            try {
                limit = queryParam(exchange, "limit").map(Integer::parseInt).orElse(10);
                days = queryParam(exchange, "days").map(Integer::parseInt).orElse(7);
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("error", "Invalid parameter format"));
                return;
            }
            String sort = queryParam(exchange, "sort").orElse("playtime");
            
            limit = Math.min(50, Math.max(1, limit));
            days = Math.min(365, Math.max(1, days));
            
            try {
                List<Map<String, Object>> leaderboard = statsService.getStorage().loadTimelineLeaderboard(days, limit);
                
                // Enrich with player names
                for (Map<String, Object> row : leaderboard) {
                    try {
                        UUID uuid = UUID.fromString(row.get("uuid").toString());
                        Optional<StatsRecord> record = statsService.getStats(uuid);
                        row.put("name", record.map(StatsRecord::getName).orElse("Unknown"));
                    } catch (Exception ignored) {
                        row.put("name", "Unknown");
                    }
                }
                
                sendJson(exchange, 200, Map.of(
                    "days", days,
                    "leaderboard", leaderboard
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("Leaderboard query failed: " + e.getMessage());
                sendJson(exchange, 500, Map.of("error", "Failed to load leaderboard"));
            }
        }
    }
    
    private class PublicMomentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!settings.getDashboardSettings().publicSettings().showRecentMoments()) {
                sendJson(exchange, 403, Map.of("error", "Moments display is disabled"));
                return;
            }
            
            int limit;
            try {
                limit = queryParam(exchange, "limit").map(Integer::parseInt).orElse(20);
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("error", "Invalid parameter format"));
                return;
            }
            limit = Math.min(100, Math.max(1, limit));
            
            List<?> moments = momentService.getRecentMoments(limit);
            sendJson(exchange, 200, Map.of("moments", moments));
        }
    }
    
    private class PublicStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!settings.getDashboardSettings().publicSettings().showServerStats()) {
                sendJson(exchange, 403, Map.of("error", "Server stats display is disabled"));
                return;
            }
            
            List<StatsRecord> allStats = statsService.getAllStats();
            
            // Aggregate server-wide stats
            long totalPlaytime = 0;
            long totalDeaths = 0;
            long totalPlayerKills = 0;
            long totalMobKills = 0;
            long totalBlocksBroken = 0;
            long totalBlocksPlaced = 0;
            double totalDistance = 0;
            Set<String> allBiomes = new HashSet<>();
            
            for (StatsRecord record : allStats) {
                totalPlaytime += record.getPlaytimeMillis();
                totalDeaths += record.getDeaths();
                totalPlayerKills += record.getPlayerKills();
                totalMobKills += record.getMobKills();
                totalBlocksBroken += record.getBlocksBroken();
                totalBlocksPlaced += record.getBlocksPlaced();
                totalDistance += record.getDistanceOverworld() + record.getDistanceNether() + record.getDistanceEnd();
                if (record.getBiomesVisited() != null) {
                    allBiomes.addAll(record.getBiomesVisited());
                }
            }
            
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalPlayers", allStats.size());
            stats.put("totalPlaytimeHours", totalPlaytime / 3600000.0);
            stats.put("totalDeaths", totalDeaths);
            stats.put("totalPlayerKills", totalPlayerKills);
            stats.put("totalMobKills", totalMobKills);
            stats.put("totalBlocksBroken", totalBlocksBroken);
            stats.put("totalBlocksPlaced", totalBlocksPlaced);
            stats.put("totalDistanceKm", totalDistance / 1000.0);
            stats.put("uniqueBiomesDiscovered", allBiomes.size());
            
            sendJson(exchange, 200, stats);
        }
    }
    
    // ============== Admin Auth Handlers ==============
    
    private class AdminLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!settings.getDashboardSettings().adminSettings().enabled()) {
                sendJson(exchange, 403, Map.of("error", "Admin panel is disabled"));
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            
            // Read POST body
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<?, ?> data = gson.fromJson(body, Map.class);
            
            String password = data != null ? (String) data.get("password") : null;
            if (password == null || password.isEmpty() || !constantTimeEquals(password, settings.getDashboardSettings().adminSettings().password())) {
                sendJson(exchange, 401, Map.of("error", "Invalid password"));
                return;
            }
            
            // Create session
            String sessionId = generateSessionId();
            int timeoutMinutes = settings.getDashboardSettings().adminSettings().sessionTimeoutMinutes();
            long now = System.currentTimeMillis();
            AdminSession session = new AdminSession(sessionId, now, now + timeoutMinutes * 60000L);
            sessions.put(sessionId, session);
            
            // Set cookie
            setSessionCookie(exchange, sessionId, timeoutMinutes * 60);
            
            sendJson(exchange, 200, Map.of("success", true, "expiresIn", timeoutMinutes * 60));
        }
    }
    
    private class AdminLogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Optional<AdminSession> session = getSession(exchange);
            session.ifPresent(s -> sessions.remove(s.id()));
            clearSessionCookie(exchange);
            sendJson(exchange, 200, Map.of("success", true));
        }
    }
    
    private class AdminCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean authenticated = isAdminAuthenticated(exchange);
            sendJson(exchange, 200, Map.of(
                "authenticated", authenticated,
                "adminEnabled", settings.getDashboardSettings().adminSettings().enabled()
            ));
        }
    }
    
    // ============== Admin API Handlers ==============
    
    private class AdminHealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAdminAuthenticated(exchange)) {
                sendJson(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            
            if (!settings.getDashboardSettings().adminSettings().showHealthMetrics()) {
                sendJson(exchange, 403, Map.of("error", "Health metrics are disabled"));
                return;
            }
            
            if (serverHealthService == null) {
                sendJson(exchange, 404, Map.of("error", "Health service not available"));
                return;
            }
            
            var snapshot = serverHealthService.getLatest();
            if (snapshot == null) {
                sendJson(exchange, 404, Map.of("error", "No health samples yet"));
                return;
            }
            
            sendJson(exchange, 200, snapshot);
        }
    }
    
    private class AdminHeatmapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAdminAuthenticated(exchange)) {
                sendJson(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            
            if (!settings.getDashboardSettings().adminSettings().showHeatmaps()) {
                sendJson(exchange, 403, Map.of("error", "Heatmaps are disabled"));
                return;
            }
            
            String type = queryParam(exchange, "type").orElse("MINING");
            String world = queryParam(exchange, "world").orElse("world");
            long since;
            long until;
            double decay;
            int gridSize;
            try {
                since = queryParam(exchange, "since").map(Long::parseLong).orElse(System.currentTimeMillis() - 7L * 24 * 3600 * 1000);
                until = queryParam(exchange, "until").map(Long::parseLong).orElse(System.currentTimeMillis());
                decay = queryParam(exchange, "decay").map(Double::parseDouble).orElse(settings.getHeatmapDecayHalfLifeHours());
                gridSize = queryParam(exchange, "grid").map(Integer::parseInt).orElse(16);
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("error", "Invalid numeric parameter: " + e.getMessage()));
                return;
            }
            
            try {
                var heatmap = heatmapService.generateHeatmap(type.toUpperCase(), world, since, until, decay, gridSize);
                sendJson(exchange, 200, Map.of(
                    "type", type.toUpperCase(),
                    "world", world,
                    "gridSize", gridSize,
                    "bins", heatmap
                ));
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, Map.of("error", "Invalid heatmap type: " + type));
            }
        }
    }
    
    private class AdminSocialHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAdminAuthenticated(exchange)) {
                sendJson(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            
            if (!settings.getDashboardSettings().adminSettings().showSocialData()) {
                sendJson(exchange, 403, Map.of("error", "Social data is disabled"));
                return;
            }
            
            int limit;
            try {
                limit = queryParam(exchange, "limit").map(Integer::parseInt).orElse(20);
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("error", "Invalid parameter format"));
                return;
            }
            limit = Math.min(100, Math.max(1, limit));
            
            try {
                List<Map<String, Object>> pairs = new ArrayList<>();
                for (SocialPairRow row : statsService.getStorage().loadTopSocial(limit)) {
                    Map<String, Object> pair = new LinkedHashMap<>();
                    pair.put("playerA", Map.of(
                        "uuid", row.uuidA().toString(),
                        "name", statsService.getStats(row.uuidA()).map(StatsRecord::getName).orElse("Unknown")
                    ));
                    pair.put("playerB", Map.of(
                        "uuid", row.uuidB().toString(),
                        "name", statsService.getStats(row.uuidB()).map(StatsRecord::getName).orElse("Unknown")
                    ));
                    pair.put("timeTogetherSeconds", row.seconds());
                    pair.put("sharedKills", row.sharedKills());
                    pair.put("sharedPlayerKills", row.sharedPlayerKills());
                    pair.put("sharedMobKills", row.sharedMobKills());
                    pairs.add(pair);
                }
                sendJson(exchange, 200, Map.of("pairs", pairs));
            } catch (Exception e) {
                plugin.getLogger().warning("Social query failed: " + e.getMessage());
                sendJson(exchange, 500, Map.of("error", "Failed to load social data"));
            }
        }
    }
    
    private class AdminDeathsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAdminAuthenticated(exchange)) {
                sendJson(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            
            if (!settings.getDashboardSettings().adminSettings().showDeathReplays()) {
                sendJson(exchange, 403, Map.of("error", "Death replays are disabled"));
                return;
            }
            
            int limit;
            try {
                limit = queryParam(exchange, "limit").map(Integer::parseInt).orElse(20);
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("error", "Invalid parameter format"));
                return;
            }
            limit = Math.min(100, Math.max(1, limit));
            
            try {
                List<?> deaths = statsService.getStorage().loadDeathReplays(limit);
                sendJson(exchange, 200, Map.of("deaths", deaths));
            } catch (Exception e) {
                plugin.getLogger().warning("Death replay query failed: " + e.getMessage());
                sendJson(exchange, 500, Map.of("error", "Failed to load death replays"));
            }
        }
    }
    
    private class AdminPlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAdminAuthenticated(exchange)) {
                sendJson(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            
            String path = exchange.getRequestURI().getPath();
            String uuidStr = path.substring("/api/admin/player/".length());
            
            if (uuidStr.isEmpty() || uuidStr.equals("all")) {
                // Return all players
                List<StatsRecord> allStats = statsService.getAllStats();
                sendJson(exchange, 200, Map.of("players", allStats));
                return;
            }
            
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Optional<StatsRecord> record = statsService.getStats(uuid);
                if (record.isPresent()) {
                    sendJson(exchange, 200, record.get());
                } else {
                    sendJson(exchange, 404, Map.of("error", "Player not found"));
                }
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, Map.of("error", "Invalid UUID"));
            }
        }
    }
    
    // Package-private accessors for testing
    HttpHandler staticHandler() { return new StaticHandler(); }
    HttpHandler publicConfigHandler() { return new PublicConfigHandler(); }
    HttpHandler publicOnlineHandler() { return new PublicOnlineHandler(); }
    HttpHandler publicLeaderboardHandler() { return new PublicLeaderboardHandler(); }
    HttpHandler publicMomentsHandler() { return new PublicMomentsHandler(); }
    HttpHandler publicStatsHandler() { return new PublicStatsHandler(); }
    HttpHandler adminLoginHandler() { return new AdminLoginHandler(); }
    HttpHandler adminLogoutHandler() { return new AdminLogoutHandler(); }
    HttpHandler adminCheckHandler() { return new AdminCheckHandler(); }
    HttpHandler adminHealthHandler() { return new AdminHealthHandler(); }
    HttpHandler adminHeatmapHandler() { return new AdminHeatmapHandler(); }
    HttpHandler adminSocialHandler() { return new AdminSocialHandler(); }
    HttpHandler adminDeathsHandler() { return new AdminDeathsHandler(); }
    HttpHandler adminPlayerHandler() { return new AdminPlayerHandler(); }
}
