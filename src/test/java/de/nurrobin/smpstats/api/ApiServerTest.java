package de.nurrobin.smpstats.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpPrincipal;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.health.ServerHealthService;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.social.SocialPairRow;
import de.nurrobin.smpstats.timeline.TimelineService;
import org.bukkit.plugin.PluginDescriptionFile;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.and;

class ApiServerTest {
    private static final String API_KEY = "secret";

    private SMPStats plugin;
    private StatsService stats;
    private Settings settings;
    private MomentService moments;
    private HeatmapService heatmap;
    private TimelineService timeline;
    private ServerHealthService health;
    private ApiServer server;
    private StatsStorage storage;

    @BeforeEach
    void setup() {
        plugin = mock(SMPStats.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        PluginDescriptionFile pdf = new PluginDescriptionFile("SMPStats", "1.2.3", "de.nurrobin.smpstats.SMPStats");
        when(plugin.getDescription()).thenReturn(pdf);
        stats = mock(StatsService.class);
        settings = new Settings(
                true, true, true, true, true, true, true,
                true, "127.0.0.1", 0, API_KEY, 1, new de.nurrobin.smpstats.skills.SkillWeights(
                new de.nurrobin.smpstats.skills.SkillWeights.MiningWeights(0),
                new de.nurrobin.smpstats.skills.SkillWeights.CombatWeights(0, 0, 0),
                new de.nurrobin.smpstats.skills.SkillWeights.ExplorationWeights(0, 0),
                new de.nurrobin.smpstats.skills.SkillWeights.BuilderWeights(0),
                new de.nurrobin.smpstats.skills.SkillWeights.FarmerWeights(0, 0)
        ), true, 0L, 0L, true, 1, 1.0, List.of(), List.of(), true, 1, 1, true, true, true, 1, 1, true, 1, 0, 0, 0, 0, de.nurrobin.smpstats.health.HealthThresholds.defaults(), true, 1, 1, "", 1, 1, Settings.DashboardSettings.defaults(), true);
        moments = mock(MomentService.class);
        heatmap = mock(HeatmapService.class);
        timeline = mock(TimelineService.class);
        health = mock(ServerHealthService.class);
        storage = mock(StatsStorage.class);
        when(stats.getStorage()).thenReturn(storage);
        server = new ApiServer(plugin, stats, settings, moments, heatmap, timeline, health);
    }

    @Test
    void rejectsUnauthorizedRequests() throws Exception {
        var handler = server.statsHandler();
        FakeExchange exchange = new FakeExchange("/stats/anything", null);
        handler.handle(exchange);
        assertEquals(401, exchange.status);
        assertTrue(exchange.body().contains("Unauthorized"));
    }

    @Test
    void statsHandlerReturnsRecords() throws Exception {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        when(stats.getStats(uuid)).thenReturn(Optional.of(record));

        var handler = server.statsHandler();
        FakeExchange missing = new FakeExchange("/stats/", API_KEY);
        handler.handle(missing);
        assertEquals(400, missing.status);

        FakeExchange invalid = new FakeExchange("/stats/not-a-uuid", API_KEY);
        handler.handle(invalid);
        assertEquals(400, invalid.status);

        FakeExchange found = new FakeExchange("/stats/" + uuid, API_KEY);
        handler.handle(found);
        assertEquals(200, found.status);
        assertTrue(found.body().contains("Alex"));

        when(stats.getStats(uuid)).thenReturn(Optional.empty());
        FakeExchange missingRecord = new FakeExchange("/stats/" + uuid, API_KEY);
        handler.handle(missingRecord);
        assertEquals(404, missingRecord.status);

        when(stats.getAllStats()).thenReturn(List.of(record));
        FakeExchange all = new FakeExchange("/stats/all", API_KEY);
        handler.handle(all);
        assertEquals(200, all.status);
        assertTrue(all.body().contains("Alex"));
    }

    @Test
    void onlineAndMomentsEndpointsWork() throws Exception {
        when(stats.getOnlineNames()).thenReturn(List.of("Alex"));
        var online = server.onlineHandler();
        FakeExchange onlineReq = new FakeExchange("/online", API_KEY);
        online.handle(onlineReq);
        assertEquals(200, onlineReq.status);
        assertTrue(onlineReq.body().contains("Alex"));

        when(moments.getRecentMoments(10)).thenReturn(List.of(mock(de.nurrobin.smpstats.moments.MomentEntry.class)));
        var recent = server.recentMomentsHandler();
        FakeExchange momentsReq = new FakeExchange("/moments/recent?limit=10", API_KEY);
        recent.handle(momentsReq);
        assertEquals(200, momentsReq.status);

        when(moments.queryMoments(null, null, -1, 100)).thenReturn(List.of());
        var query = server.queryMomentsHandler();
        FakeExchange queryReq = new FakeExchange("/moments/query", API_KEY);
        query.handle(queryReq);
        assertEquals(200, queryReq.status);

        when(moments.getMomentsSince(5L, 2)).thenReturn(List.of(mock(de.nurrobin.smpstats.moments.MomentEntry.class)));
        var stream = server.momentsStreamHandler();
        FakeExchange streamReq = new FakeExchange("/moments/stream?since=5&limit=2", API_KEY);
        stream.handle(streamReq);
        assertEquals(200, streamReq.status);
        assertTrue(streamReq.body().contains("data:"));

        FakeExchange queryPlayerParam = new FakeExchange("/moments/query?player=not-a-uuid", API_KEY);
        query.handle(queryPlayerParam);
        assertEquals(200, queryPlayerParam.status);
    }

    @Test
    void heatmapEndpointsValidateInput() throws Exception {
        when(heatmap.loadTop("BREAK", 200)).thenReturn(List.of());
        var handler = server.heatmapHandler();
        FakeExchange missing = new FakeExchange("/heatmap/", API_KEY);
        handler.handle(missing);
        assertEquals(400, missing.status);

        doThrow(new IllegalArgumentException("bad")).when(heatmap).generateHeatmap(eq("BAD"), anyString(), anyLong(), anyLong(), anyDouble(), anyInt());
        FakeExchange invalid = new FakeExchange("/heatmap/BAD", API_KEY);
        handler.handle(invalid);
        assertEquals(400, invalid.status);

        FakeExchange ok = new FakeExchange("/heatmap/break", API_KEY);
        handler.handle(ok);
        assertEquals(200, ok.status);

        when(heatmap.loadHotspots("BREAK")).thenReturn(Map.of("spawn", 5.0));
        var hotspots = server.heatmapHotspotHandler();
        FakeExchange hotspotsReq = new FakeExchange("/heatmap/hotspots/break", API_KEY);
        hotspots.handle(hotspotsReq);
        assertEquals(200, hotspotsReq.status);
        assertTrue(hotspotsReq.body().contains("spawn"));
    }

    @Test
    void heatmapEndpointsGridSize() throws Exception {
        var handler = server.heatmapHandler();
        
        // Test grid size
        FakeExchange grid = new FakeExchange("/heatmap/break?grid=32", API_KEY);
        handler.handle(grid);
        assertEquals(200, grid.status);
        verify(heatmap).generateHeatmap(eq("BREAK"), anyString(), anyLong(), anyLong(), anyDouble(), eq(32));

        // Test invalid grid size (should default to 16)
        FakeExchange invalidGrid = new FakeExchange("/heatmap/break?grid=-5", API_KEY);
        handler.handle(invalidGrid);
        assertEquals(200, invalidGrid.status);
        verify(heatmap).generateHeatmap(eq("BREAK"), anyString(), anyLong(), anyLong(), anyDouble(), eq(16));
    }

    @Test
    void heatmapEndpointsAcceptTimeRangeFromParameter() throws Exception {
        var handler = server.heatmapHandler();
        
        // Test 'from' parameter with human-readable time range (e.g., "6h")
        long beforeMs = System.currentTimeMillis();
        FakeExchange fromReq = new FakeExchange("/heatmap/break?from=6h", API_KEY);
        handler.handle(fromReq);
        assertEquals(200, fromReq.status);
        
        // Verify the heatmap was called with a 'since' value approximately 6 hours ago
        verify(heatmap, atLeastOnce()).generateHeatmap(
                eq("BREAK"), 
                anyString(), 
                longThat(since -> since >= beforeMs - 6L * 3600 * 1000 - 1000 && since <= beforeMs - 6L * 3600 * 1000 + 1000),
                anyLong(), 
                anyDouble(), 
                anyInt()
        );
    }

    @Test
    void heatmapEndpointsAcceptTimeRangeToParameter() throws Exception {
        var handler = server.heatmapHandler();
        
        // Test 'from' and 'to' parameters together
        FakeExchange fromToReq = new FakeExchange("/heatmap/break?from=today&to=today", API_KEY);
        handler.handle(fromToReq);
        assertEquals(200, fromToReq.status);
    }

    @Test
    void heatmapEndpointsFallbackToSinceUntilParameters() throws Exception {
        var handler = server.heatmapHandler();
        
        // Test that 'since' and 'until' still work for backwards compatibility
        long since = System.currentTimeMillis() - 3600000L;
        long until = System.currentTimeMillis();
        FakeExchange legacyReq = new FakeExchange("/heatmap/break?since=" + since + "&until=" + until, API_KEY);
        handler.handle(legacyReq);
        assertEquals(200, legacyReq.status);
        
        verify(heatmap, atLeastOnce()).generateHeatmap(
                eq("BREAK"), 
                anyString(), 
                eq(since),
                eq(until), 
                anyDouble(), 
                anyInt()
        );
    }

    @Test
    void openApiEndpointIsPublicAndWellFormed() throws Exception {
        var handler = server.openApiHandler();
        FakeExchange exchange = new FakeExchange("/openapi.json", null); // no auth required
        handler.handle(exchange);

        assertEquals(200, exchange.status);
        JsonObject json = JsonParser.parseString(exchange.body()).getAsJsonObject();
        assertEquals("3.1.0", json.get("openapi").getAsString());
        assertTrue(json.getAsJsonObject("info").get("version").getAsString().contains("1.2.3"));
        assertTrue(json.getAsJsonObject("paths").has("/stats/{playerId}"));
        assertTrue(json.getAsJsonObject("components").getAsJsonObject("schemas").has("HeatmapBin"));
    }

    @Test
    void momentsEndpointsAcceptFromParameter() throws Exception {
        when(moments.queryMoments(any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(moments.getRecentMoments(anyInt())).thenReturn(List.of());
        
        // Test query moments with 'from' parameter
        var queryHandler = server.queryMomentsHandler();
        FakeExchange queryReq = new FakeExchange("/moments/query?from=6h", API_KEY);
        queryHandler.handle(queryReq);
        assertEquals(200, queryReq.status);
        
        // Test recent moments with 'from' parameter
        when(moments.getMomentsSince(anyLong(), anyInt())).thenReturn(List.of());
        var recentHandler = server.recentMomentsHandler();
        FakeExchange recentReq = new FakeExchange("/moments/recent?from=today", API_KEY);
        recentHandler.handle(recentReq);
        assertEquals(200, recentReq.status);
        
        // Test stream moments with 'from' parameter
        var streamHandler = server.momentsStreamHandler();
        FakeExchange streamReq = new FakeExchange("/moments/stream?from=this_week", API_KEY);
        streamHandler.handle(streamReq);
        assertEquals(200, streamReq.status);
    }

    @Test
    void timelineEndpointsAcceptFromParameter() throws Exception {
        when(storage.loadTimelineLeaderboard(anyInt(), anyInt())).thenReturn(List.of());
        
        // Test leaderboard with 'from' parameter (should convert to days)
        var handler = server.timelineHandler();
        FakeExchange leaderboardReq = new FakeExchange("/timeline/leaderboard?from=this_week", API_KEY);
        handler.handle(leaderboardReq);
        assertEquals(200, leaderboardReq.status);
        
        // Test range with 'from' parameter
        UUID uuid = UUID.randomUUID();
        when(storage.loadTimelineRange(eq(uuid), anyInt())).thenReturn(Map.of("from", "2024-01-01"));
        FakeExchange rangeReq = new FakeExchange("/timeline/range/" + uuid + "?from=3d", API_KEY);
        handler.handle(rangeReq);
        assertEquals(200, rangeReq.status);
    }

    @Test
    void timelineEndpointsHandleRangesAndLeaderboard() throws Exception {
        when(storage.loadTimelineLeaderboard(7, 20)).thenReturn(List.of(Map.of("uuid", UUID.randomUUID().toString(), "playtime_ms", 1)));
        var handler = server.timelineHandler();
        FakeExchange leaderboard = new FakeExchange("/timeline/leaderboard", API_KEY);
        handler.handle(leaderboard);
        assertEquals(200, leaderboard.status);

        FakeExchange missing = new FakeExchange("/timeline/range/", API_KEY);
        handler.handle(missing);
        assertEquals(400, missing.status);

        FakeExchange invalid = new FakeExchange("/timeline/range/not-a-uuid", API_KEY);
        handler.handle(invalid);
        assertEquals(400, invalid.status);

        UUID uuid = UUID.randomUUID();
        when(storage.loadTimelineRange(uuid, 7)).thenReturn(Map.of("from", "2024-01-01"));
        FakeExchange range = new FakeExchange("/timeline/range/" + uuid, API_KEY);
        handler.handle(range);
        assertEquals(200, range.status);

        when(storage.loadTimeline(uuid, 30)).thenReturn(List.of(Map.of("day", "2024-01-01")));
        FakeExchange timelineReq = new FakeExchange("/timeline/" + uuid + "?limit=30", API_KEY);
        handler.handle(timelineReq);
        assertEquals(200, timelineReq.status);

        when(storage.loadTimeline(uuid, 30)).thenThrow(new RuntimeException("fail"));
        FakeExchange timelineError = new FakeExchange("/timeline/" + uuid, API_KEY);
        handler.handle(timelineError);
        assertEquals(200, timelineError.status); // returns empty list on error

        ApiServer noTimeline = new ApiServer(plugin, stats, settings, moments, heatmap, null, health);
        FakeExchange timelineNoService = new FakeExchange("/timeline/" + uuid, API_KEY);
        noTimeline.timelineHandler().handle(timelineNoService);
        assertEquals(200, timelineNoService.status);
    }

    @Test
    void socialTopHealthAndDeathReplayEndpoints() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(storage.loadTopSocial(50)).thenReturn(List.of(new SocialPairRow(a, b, 10, 1, 2, 3)));
        when(stats.getStats(a)).thenReturn(Optional.of(new StatsRecord(a, "Alex")));
        when(stats.getStats(b)).thenReturn(Optional.empty());
        var social = server.socialTopHandler();
        FakeExchange socialReq = new FakeExchange("/social/top", API_KEY);
        social.handle(socialReq);
        assertEquals(200, socialReq.status);
        assertTrue(socialReq.body().contains("Alex"));

        when(storage.loadDeathReplays(20)).thenReturn(List.of());
        var death = server.deathReplayHandler();
        FakeExchange deathReq = new FakeExchange("/death/replay", API_KEY);
        death.handle(deathReq);
        assertEquals(200, deathReq.status);

        var snapshot = mock(de.nurrobin.smpstats.health.HealthSnapshot.class);
        when(health.getLatest()).thenReturn(snapshot);
        var healthHandler = server.healthHandler();
        FakeExchange healthReq = new FakeExchange("/health", API_KEY);
        healthHandler.handle(healthReq);
        assertEquals(200, healthReq.status);

        when(stats.getStorage().loadTopSocial(anyInt())).thenThrow(new RuntimeException("fail"));
        FakeExchange socialError = new FakeExchange("/social/top", API_KEY);
        social.handle(socialError);
        assertEquals(200, socialError.status);

        when(health.getLatest()).thenReturn(null);
        FakeExchange noSnapshot = new FakeExchange("/health", API_KEY);
        healthHandler.handle(noSnapshot);
        assertEquals(404, noSnapshot.status);

        ApiServer noHealth = new ApiServer(plugin, stats, settings, moments, heatmap, timeline, null);
        FakeExchange noHealthReq = new FakeExchange("/health", API_KEY);
        noHealth.healthHandler().handle(noHealthReq);
        assertEquals(200, noHealthReq.status);

        when(stats.getStorage().loadDeathReplays(anyInt())).thenThrow(new RuntimeException("fail"));
        FakeExchange deathErr = new FakeExchange("/death/replay", API_KEY);
        server.deathReplayHandler().handle(deathErr);
        assertEquals(500, deathErr.status);
    }

    // ============== Hardening Tests ==============

    private Settings settingsWithKey(String apiKey) {
        return new Settings(
                true, true, true, true, true, true, true,
                true, "127.0.0.1", 0, apiKey, 1, settings.getSkillWeights(),
                true, 0L, 0L, true, 1, 1.0, List.of(), List.of(), true, 1, 1, true, true, true, 1, 1,
                true, 1, 0, 0, 0, 0, de.nurrobin.smpstats.health.HealthThresholds.defaults(),
                true, 1, 1, "", 1, 1, Settings.DashboardSettings.defaults(), true);
    }

    @Test
    void placeholderOrEmptyApiKeyPreventsStartup() {
        for (String key : new String[]{"", "   ", "YouShouldChangeThisKey", "CHANGEME123", null}) {
            ApiServer insecure = new ApiServer(plugin, stats, settingsWithKey(key), moments, heatmap, timeline, health);
            insecure.start();
            assertFalse(insecure.isRunning(), "API must not start with key '" + key + "'");
            insecure.stop();
        }
    }

    @Test
    void startAndStopWithRealKey() {
        server.start();
        assertTrue(server.isRunning());
        server.stop();
        assertFalse(server.isRunning());
        server.stop();
    }

    @Test
    void emptyConfiguredKeyRejectsEmptyHeader() throws Exception {
        ApiServer emptyKey = new ApiServer(plugin, stats, settingsWithKey(""), moments, heatmap, timeline, health);
        when(stats.getOnlineNames()).thenReturn(List.of("Alex"));
        FakeExchange exchange = new FakeExchange("/online", "");
        emptyKey.onlineHandler().handle(exchange);
        assertEquals(401, exchange.status);
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        FakeExchange exchange = new FakeExchange("/online", API_KEY + "x");
        server.onlineHandler().handle(exchange);
        assertEquals(401, exchange.status);
        FakeExchange shorter = new FakeExchange("/online", "sec");
        server.onlineHandler().handle(shorter);
        assertEquals(401, shorter.status);
    }

    @Test
    void malformedNumbersReturnJson400() throws Exception {
        UUID uuid = UUID.randomUUID();
        Object[][] cases = {
                {server.recentMomentsHandler(), "/moments/recent?limit=abc"},
                {server.recentMomentsHandler(), "/moments/recent?since=abc"},
                {server.queryMomentsHandler(), "/moments/query?limit=1e9"},
                {server.queryMomentsHandler(), "/moments/query?since=x"},
                {server.momentsStreamHandler(), "/moments/stream?limit=abc"},
                {server.momentsStreamHandler(), "/moments/stream?since=abc"},
                {server.heatmapHandler(), "/heatmap/break?since=abc"},
                {server.heatmapHandler(), "/heatmap/break?until=abc"},
                {server.heatmapHandler(), "/heatmap/break?decay=abc"},
                {server.heatmapHandler(), "/heatmap/break?decay=NaN"},
                {server.heatmapHandler(), "/heatmap/break?grid=abc"},
                {server.timelineHandler(), "/timeline/leaderboard?days=abc"},
                {server.timelineHandler(), "/timeline/leaderboard?limit=abc"},
                {server.timelineHandler(), "/timeline/range/" + uuid + "?days=abc"},
                {server.timelineHandler(), "/timeline/" + uuid + "?limit=abc"},
                {server.socialTopHandler(), "/social/top?limit=99999999999"},
                {server.deathReplayHandler(), "/death/replay?limit=abc"},
        };
        for (Object[] c : cases) {
            FakeExchange exchange = new FakeExchange((String) c[1], API_KEY);
            ((com.sun.net.httpserver.HttpHandler) c[0]).handle(exchange);
            assertEquals(400, exchange.status, "expected 400 for " + c[1]);
            JsonObject json = JsonParser.parseString(exchange.body()).getAsJsonObject();
            assertTrue(json.has("error"), "expected JSON error for " + c[1]);
        }
    }

    @Test
    void numericParametersAreClamped() throws Exception {
        when(storage.loadTopSocial(anyInt())).thenReturn(List.of());
        FakeExchange social = new FakeExchange("/social/top?limit=100000", API_KEY);
        server.socialTopHandler().handle(social);
        assertEquals(200, social.status);
        verify(storage).loadTopSocial(500);

        when(storage.loadDeathReplays(anyInt())).thenReturn(List.of());
        FakeExchange death = new FakeExchange("/death/replay?limit=-4", API_KEY);
        server.deathReplayHandler().handle(death);
        verify(storage).loadDeathReplays(1);

        when(moments.getRecentMoments(anyInt())).thenReturn(List.of());
        FakeExchange recent = new FakeExchange("/moments/recent?limit=900000", API_KEY);
        server.recentMomentsHandler().handle(recent);
        verify(moments).getRecentMoments(500);

        when(storage.loadTimelineLeaderboard(anyInt(), anyInt())).thenReturn(List.of());
        FakeExchange leaderboard = new FakeExchange("/timeline/leaderboard?days=100000&limit=100000", API_KEY);
        server.timelineHandler().handle(leaderboard);
        verify(storage).loadTimelineLeaderboard(365, 500);

        UUID uuid = UUID.randomUUID();
        when(storage.loadTimelineRange(eq(uuid), anyInt())).thenReturn(Map.of());
        FakeExchange range = new FakeExchange("/timeline/range/" + uuid + "?days=100000", API_KEY);
        server.timelineHandler().handle(range);
        verify(storage).loadTimelineRange(uuid, 365);

        long before = System.currentTimeMillis();
        FakeExchange heat = new FakeExchange("/heatmap/break?since=0&grid=100000", API_KEY);
        server.heatmapHandler().handle(heat);
        assertEquals(200, heat.status);
        verify(heatmap).generateHeatmap(eq("BREAK"), anyString(),
                longThat(since -> since >= before - 90L * 24 * 3600 * 1000 - 1000),
                anyLong(), anyDouble(), eq(512));
    }

    private static class FakeExchange extends com.sun.net.httpserver.HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final URI uri;
        private int status;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        FakeExchange(String path, String apiKey) {
            this.uri = URI.create("http://localhost" + path);
            if (apiKey != null) {
                requestHeaders.add("X-API-Key", apiKey);
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
            return "GET";
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
            return new ByteArrayInputStream(new byte[0]);
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
