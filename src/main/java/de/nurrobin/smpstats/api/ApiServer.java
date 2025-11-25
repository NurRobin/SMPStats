package de.nurrobin.smpstats.api;

import com.google.gson.Gson;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

public class ApiServer {
    private final SMPStats plugin;
    private final StatsService statsService;
    private final Settings settings;
    private final MomentService momentService;
    private final HeatmapService heatmapService;
    private final Gson gson = new Gson();

    private HttpServer server;

    public ApiServer(SMPStats plugin, StatsService statsService, Settings settings, MomentService momentService, HeatmapService heatmapService) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.settings = settings;
        this.momentService = momentService;
        this.heatmapService = heatmapService;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(settings.getApiPort()), 0);
        } catch (IOException e) {
            plugin.getLogger().severe("HTTP API konnte nicht gestartet werden: " + e.getMessage());
            return;
        }

        server.createContext("/stats", new StatsHandler());
        server.createContext("/online", new OnlineHandler());
        server.createContext("/moments/recent", new RecentMomentsHandler());
        server.createContext("/moments/query", new QueryMomentsHandler());
        server.createContext("/heatmap", new HeatmapHandler());
        server.createContext("/heatmap/hotspots", new HeatmapHotspotHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        plugin.getLogger().info("HTTP API läuft auf Port " + settings.getApiPort());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getRequestHeaders();
        String key = headers.getFirst("X-API-Key");
        if (key == null || !key.equals(settings.getApiKey())) {
            sendText(exchange, 401, "Unauthorized");
            return false;
        }
        return true;
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

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = gson.toJson(body).getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] data = text.getBytes();
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            URI uri = exchange.getRequestURI();
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

    private class OnlineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            sendJson(exchange, 200, statsService.getOnlineNames());
        }
    }

    private class RecentMomentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            int limit = queryParam(exchange.getRequestURI(), "limit").map(Integer::parseInt).orElse(50);
            long since = queryParam(exchange.getRequestURI(), "since").map(Long::parseLong).orElse(-1L);
            List<?> recent = since > 0 ? momentService.getMomentsSince(since, limit) : momentService.getRecentMoments(limit);
            sendJson(exchange, 200, recent);
        }
    }

    private class HeatmapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            URI uri = exchange.getRequestURI();
            String path = uri.getPath().substring("/heatmap".length()); // expected /heatmap/<type>
            if (path.isEmpty() || "/".equals(path)) {
                sendText(exchange, 400, "Missing heatmap type");
                return;
            }
            String typeRaw = path.startsWith("/") ? path.substring(1) : path;
            try {
                sendJson(exchange, 200, heatmapService.loadTop(typeRaw.toUpperCase(), 200));
            } catch (IllegalArgumentException e) {
                sendText(exchange, 400, "Invalid heatmap type");
            }
        }
    }

    private class HeatmapHotspotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            URI uri = exchange.getRequestURI();
            String path = uri.getPath().substring("/heatmap/hotspots".length()); // /heatmap/hotspots/<type>
            if (path.isEmpty() || "/".equals(path)) {
                sendText(exchange, 400, "Missing heatmap type");
                return;
            }
            String typeRaw = path.startsWith("/") ? path.substring(1) : path;
            sendJson(exchange, 200, heatmapService.loadHotspots(typeRaw.toUpperCase()));
        }
    }

    private class QueryMomentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            int limit = queryParam(exchange.getRequestURI(), "limit").map(Integer::parseInt).orElse(100);
            long since = queryParam(exchange.getRequestURI(), "since").map(Long::parseLong).orElse(-1L);
            String type = queryParam(exchange.getRequestURI(), "type").orElse(null);
            java.util.Optional<String> playerParam = queryParam(exchange.getRequestURI(), "player");
            java.util.UUID playerId = null;
            if (playerParam.isPresent()) {
                try {
                    playerId = java.util.UUID.fromString(playerParam.get());
                } catch (IllegalArgumentException ignored) {
                }
            }
            List<?> moments = momentService.queryMoments(playerId, type != null ? type.toUpperCase() : null, since, limit);
            sendJson(exchange, 200, moments);
        }
    }
}
