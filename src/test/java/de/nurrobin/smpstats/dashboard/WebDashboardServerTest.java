package de.nurrobin.smpstats.dashboard;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpPrincipal;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.health.HealthThresholds;
import de.nurrobin.smpstats.health.ServerHealthService;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import de.nurrobin.smpstats.moments.MomentEntry;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.skills.SkillWeights;
import de.nurrobin.smpstats.social.SocialPairRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebDashboardServerTest {
    private static final String ADMIN_PASSWORD = "testPassword123";

    private SMPStats plugin;
    private StatsService statsService;
    private Settings settings;
    private MomentService momentService;
    private HeatmapService heatmapService;
    private ServerHealthService healthService;
    private WebDashboardServer dashboard;
    private StatsStorage storage;

    private Settings createSettings(boolean dashboardEnabled, boolean publicEnabled, boolean adminEnabled) {
        SkillWeights skillWeights = new SkillWeights(
                new SkillWeights.MiningWeights(0),
                new SkillWeights.CombatWeights(0, 0, 0),
                new SkillWeights.ExplorationWeights(0, 0),
                new SkillWeights.BuilderWeights(0),
                new SkillWeights.FarmerWeights(0, 0)
        );
        
        Settings.PublicSettings publicSettings = new Settings.PublicSettings(
                publicEnabled, true, true, true, true
        );
        
        Settings.AdminSettings adminSettings = new Settings.AdminSettings(
                adminEnabled, ADMIN_PASSWORD, 60, true, true, true, true
        );
        
        Settings.DashboardSettings dashboardSettings = new Settings.DashboardSettings(
                dashboardEnabled, "0.0.0.0", 8080, publicSettings, adminSettings
        );
        
        return new Settings(
                true, true, true, true, true, true, true,
                true, "127.0.0.1", 8765, "apiKey", 1, skillWeights,
                true, 0L, 0L, true, 1, 1.0, List.of(), List.of(),
                true, 1, 1, true, true, true, 1, 1,
                true, 1, 0, 0, 0, 0, HealthThresholds.defaults(),
                true, 1, 1, "", 1, 1,
                dashboardSettings
        );
    }

    @BeforeEach
    void setup() {
        plugin = mock(SMPStats.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        statsService = mock(StatsService.class);
        settings = createSettings(true, true, true);
        momentService = mock(MomentService.class);
        heatmapService = mock(HeatmapService.class);
        healthService = mock(ServerHealthService.class);
        storage = mock(StatsStorage.class);
        when(statsService.getStorage()).thenReturn(storage);
        
        dashboard = new WebDashboardServer(plugin, statsService, settings, momentService, heatmapService, healthService);
    }

    // ============== Public Config Handler Tests ==============
    
    @Test
    void publicConfigHandlerReturnsConfig() throws Exception {
        var handler = dashboard.publicConfigHandler();
        FakeExchange exchange = new FakeExchange("/api/public/config", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        String body = exchange.body();
        assertTrue(body.contains("\"publicEnabled\""));
        assertTrue(body.contains("\"adminEnabled\""));
    }

    // ============== Public Online Handler Tests ==============
    
    @Test
    void publicOnlineHandlerReturnsPlayerList() throws Exception {
        when(statsService.getOnlineNames()).thenReturn(List.of("Player1", "Player2"));
        
        var handler = dashboard.publicOnlineHandler();
        FakeExchange exchange = new FakeExchange("/api/public/online", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        String body = exchange.body();
        assertTrue(body.contains("\"count\""));
        assertTrue(body.contains("Player1"));
        assertTrue(body.contains("Player2"));
    }
    
    @Test
    void publicOnlineHandlerRespectsConfig() throws Exception {
        Settings restrictedSettings = new Settings(
                true, true, true, true, true, true, true,
                true, "127.0.0.1", 8765, "apiKey", 1, settings.getSkillWeights(),
                true, 0L, 0L, true, 1, 1.0, List.of(), List.of(),
                true, 1, 1, true, true, true, 1, 1,
                true, 1, 0, 0, 0, 0, HealthThresholds.defaults(),
                true, 1, 1, "", 1, 1,
                new Settings.DashboardSettings(true, "0.0.0.0", 8080,
                        new Settings.PublicSettings(true, false, true, true, true),
                        Settings.AdminSettings.defaults())
        );
        
        WebDashboardServer restrictedDashboard = new WebDashboardServer(plugin, statsService, restrictedSettings, momentService, heatmapService, healthService);
        var handler = restrictedDashboard.publicOnlineHandler();
        FakeExchange exchange = new FakeExchange("/api/public/online", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(403, exchange.status);
    }

    // ============== Public Leaderboard Handler Tests ==============
    
    @Test
    void publicLeaderboardHandlerReturnsData() throws Exception {
        UUID uuid = UUID.randomUUID();
        // Use HashMap instead of Map.of() since the handler modifies the map
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("uuid", uuid.toString());
        row.put("playtime_ms", 3600000L);
        row.put("blocks_broken", 1000L);
        
        when(storage.loadTimelineLeaderboard(7, 10)).thenReturn(List.of(row));
        when(statsService.getStats(uuid)).thenReturn(Optional.of(new StatsRecord(uuid, "TestPlayer")));
        
        var handler = dashboard.publicLeaderboardHandler();
        FakeExchange exchange = new FakeExchange("/api/public/leaderboard?days=7&limit=10", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        String body = exchange.body();
        assertTrue(body.contains("leaderboard"));
        assertTrue(body.contains("TestPlayer"));
    }

    // ============== Public Moments Handler Tests ==============
    
    @Test
    void publicMomentsHandlerReturnsData() throws Exception {
        MomentEntry moment = mock(MomentEntry.class);
        when(moment.getTitle()).thenReturn("Test Moment");
        when(momentService.getRecentMoments(20)).thenReturn(List.of(moment));
        
        var handler = dashboard.publicMomentsHandler();
        FakeExchange exchange = new FakeExchange("/api/public/moments?limit=20", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        String body = exchange.body();
        assertTrue(body.contains("moments"));
    }

    // ============== Public Stats Handler Tests ==============
    
    @Test
    void publicStatsHandlerReturnsAggregatedStats() throws Exception {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        
        StatsRecord record1 = new StatsRecord(uuid1, "Player1");
        record1.setPlaytimeMillis(3600000L);
        record1.setDeaths(5);
        record1.setBlocksBroken(1000L);
        
        StatsRecord record2 = new StatsRecord(uuid2, "Player2");
        record2.setPlaytimeMillis(7200000L);
        record2.setDeaths(10);
        record2.setBlocksBroken(2000L);
        
        when(statsService.getAllStats()).thenReturn(List.of(record1, record2));
        
        var handler = dashboard.publicStatsHandler();
        FakeExchange exchange = new FakeExchange("/api/public/stats", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        String body = exchange.body();
        assertTrue(body.contains("\"totalPlayers\""));
        assertTrue(body.contains("\"totalDeaths\""));
        assertTrue(body.contains("\"totalBlocksBroken\""));
    }

    // ============== Admin Login Handler Tests ==============
    
    @Test
    void adminLoginHandlerRejectsGetMethod() throws Exception {
        var handler = dashboard.adminLoginHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/login", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(405, exchange.status);
    }
    
    @Test
    void adminLoginHandlerRejectsInvalidPassword() throws Exception {
        var handler = dashboard.adminLoginHandler();
        String body = "{\"password\": \"wrongPassword\"}";
        FakeExchange exchange = new FakeExchange("/api/admin/login", null, body, "POST");
        handler.handle(exchange);
        
        assertEquals(401, exchange.status);
    }
    
    @Test
    void adminLoginHandlerAcceptsValidPassword() throws Exception {
        var handler = dashboard.adminLoginHandler();
        String body = "{\"password\": \"" + ADMIN_PASSWORD + "\"}";
        FakeExchange exchange = new FakeExchange("/api/admin/login", null, body, "POST");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        assertTrue(exchange.body().contains("\"success\""));
        assertTrue(exchange.responseHeaders.containsKey("Set-Cookie"));
    }

    // ============== Admin Check Handler Tests ==============
    
    @Test
    void adminCheckHandlerReturnsAuthStatus() throws Exception {
        var handler = dashboard.adminCheckHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/check", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        assertTrue(exchange.body().contains("\"authenticated\""));
    }

    // ============== Admin Logout Handler Tests ==============
    
    @Test
    void adminLogoutHandlerClearsCookie() throws Exception {
        var handler = dashboard.adminLogoutHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/logout", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        assertTrue(exchange.body().contains("\"success\""));
    }

    // ============== Admin Health Handler Tests ==============
    
    @Test
    void adminHealthHandlerRequiresAuth() throws Exception {
        var handler = dashboard.adminHealthHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/health", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(401, exchange.status);
    }

    // ============== Admin Heatmap Handler Tests ==============
    
    @Test
    void adminHeatmapHandlerRequiresAuth() throws Exception {
        var handler = dashboard.adminHeatmapHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/heatmap?type=MINING", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(401, exchange.status);
    }

    // ============== Admin Social Handler Tests ==============
    
    @Test
    void adminSocialHandlerRequiresAuth() throws Exception {
        var handler = dashboard.adminSocialHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/social", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(401, exchange.status);
    }

    // ============== Admin Deaths Handler Tests ==============
    
    @Test
    void adminDeathsHandlerRequiresAuth() throws Exception {
        var handler = dashboard.adminDeathsHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/deaths", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(401, exchange.status);
    }

    // ============== Admin Player Handler Tests ==============
    
    @Test
    void adminPlayerHandlerRequiresAuth() throws Exception {
        var handler = dashboard.adminPlayerHandler();
        FakeExchange exchange = new FakeExchange("/api/admin/player/all", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(401, exchange.status);
    }

    // ============== Static Handler Tests ==============
    
    @Test
    void staticHandlerReturns404ForMissingFiles() throws Exception {
        var handler = dashboard.staticHandler();
        FakeExchange exchange = new FakeExchange("/nonexistent.html", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(404, exchange.status);
    }
    
    @Test
    void staticHandlerServesIndexHtml() throws Exception {
        var handler = dashboard.staticHandler();
        FakeExchange exchange = new FakeExchange("/", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        assertTrue(exchange.body().contains("SMPStats"));
    }
    
    @Test
    void staticHandlerServesCss() throws Exception {
        var handler = dashboard.staticHandler();
        FakeExchange exchange = new FakeExchange("/css/style.css", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        assertTrue(exchange.responseHeaders.getFirst("Content-Type").contains("text/css"));
    }
    
    @Test
    void staticHandlerServesJs() throws Exception {
        var handler = dashboard.staticHandler();
        FakeExchange exchange = new FakeExchange("/js/app.js", null, null, "GET");
        handler.handle(exchange);
        
        assertEquals(200, exchange.status);
        assertTrue(exchange.responseHeaders.getFirst("Content-Type").contains("javascript"));
    }

    // ============== Test Utility Classes ==============
    
    private static class FakeExchange extends com.sun.net.httpserver.HttpExchange {
        private final URI uri;
        private final String cookie;
        private final String requestBody;
        private final String method;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final Headers responseHeaders = new Headers();
        private final Headers requestHeaders = new Headers();
        private int status;

        FakeExchange(String path, String cookie, String requestBody, String method) {
            this.uri = URI.create(path);
            this.cookie = cookie;
            this.method = method;
            this.requestBody = requestBody != null ? requestBody : "";
            if (cookie != null) {
                requestHeaders.add("Cookie", cookie);
            }
        }

        String body() {
            return body.toString(StandardCharsets.UTF_8);
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return uri;
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public com.sun.net.httpserver.HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getResponseBody() {
            return body;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.status = rCode;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress(0);
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress(0);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
