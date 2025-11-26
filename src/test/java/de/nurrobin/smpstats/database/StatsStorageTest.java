package de.nurrobin.smpstats.database;

import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.moments.MomentEntry;
import de.nurrobin.smpstats.social.SocialPairRow;
import de.nurrobin.smpstats.timeline.DeathReplayEntry;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StatsStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsPlayerStatsRoundTrip() throws Exception {
        StatsStorage storage = newStorage();
        UUID uuid = UUID.randomUUID();

        StatsRecord record = new StatsRecord(uuid, "Alex");
        record.setFirstJoin(10);
        record.setLastJoin(20);
        record.setPlaytimeMillis(30);
        record.setDeaths(2);
        record.setLastDeathCause("FIRE");
        record.setPlayerKills(3);
        record.setMobKills(4);
        record.setBlocksPlaced(5);
        record.setBlocksBroken(6);
        record.setDistanceOverworld(7.5);
        record.setDistanceNether(8.5);
        record.setDistanceEnd(9.5);
        record.setBiomesVisited(new LinkedHashSet<>(Set.of("plains", "desert")));
        record.setDamageDealt(11.5);
        record.setDamageTaken(12.5);
        record.setItemsCrafted(13);
        record.setItemsConsumed(14);

        storage.save(record);

        StatsRecord byId = storage.load(uuid).orElseThrow();
        assertEquals(record.getName(), byId.getName());
        assertEquals(record.getLastDeathCause(), byId.getLastDeathCause());
        assertEquals(record.getBiomesVisited(), byId.getBiomesVisited());
        assertEquals(record.getDistanceEnd(), byId.getDistanceEnd());
        assertEquals(record.getItemsConsumed(), byId.getItemsConsumed());

        StatsRecord byName = storage.loadByName("alex").orElseThrow();
        assertEquals(uuid, byName.getUuid());

        List<StatsRecord> all = storage.loadAll();
        assertEquals(1, all.size());

        StatsRecord renamed = storage.loadOrCreate(uuid, "Alex_2");
        assertEquals("Alex_2", renamed.getName());
    }

    @Test
    void heatmapAndHotspotCountersAccumulate() throws Exception {
        StatsStorage storage = newStorage();
        storage.insertHeatmapEntries(List.of(
                new HeatmapEntry("break", "world", 16, 64, 32, 1.0, System.currentTimeMillis()),
                new HeatmapEntry("break", "world", 16, 64, 32, 1.0, System.currentTimeMillis())
        ));

        List<HeatmapEvent> events = storage.getHeatmapEvents("break", "world", 0, System.currentTimeMillis());
        assertEquals(2, events.size());

        storage.incrementHotspot("break", "spawn", "world", 4.0, 0L);
        storage.incrementHotspot("break", "spawn", "world", 1.0, 0L);
        Map<String, Double> hotspots = storage.loadHotspotCounts("break");
        assertEquals(1, hotspots.size());
        assertEquals(5.0, hotspots.get("spawn"));
    }

    @Test
    void momentsQueriesReturnSavedEntries() throws Exception {
        StatsStorage storage = newStorage();
        UUID uuid = UUID.randomUUID();
        MomentEntry entry = new MomentEntry(null, uuid, "BOSS", "Title", "Detail", "{}", "world", 1, 2, 3, 100, 200);
        storage.saveMoment(entry);

        List<MomentEntry> recent = storage.loadRecentMoments(5);
        assertEquals(1, recent.size());
        MomentEntry loaded = recent.get(0);
        assertEquals("BOSS", loaded.getType());
        assertEquals("world", loaded.getWorld());

        assertTrue(storage.hasMoment(uuid, "BOSS"));
        assertFalse(storage.hasMoment(uuid, "OTHER"));

        assertEquals(1, storage.loadMomentsSince(50, 10).size());
        assertEquals(1, storage.queryMoments(uuid, "BOSS", 50, 10).size());
        assertEquals(1, storage.queryMoments(null, null, 0, 0).size());
    }

    @Test
    void socialPairsAndTimelineCalculationsWork() throws Exception {
        StatsStorage storage = newStorage();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        storage.incrementSocialPair(a, b, 5, 1, 2, 3);
        storage.incrementSocialPair(a, b, 5, 0, 0, 1);

        List<SocialPairRow> top = storage.loadTopSocial(5);
        assertEquals(1, top.size());
        SocialPairRow row = top.get(0);
        assertEquals(10, row.seconds());
        assertEquals(1, row.sharedKills());
        assertEquals(2, row.sharedPlayerKills());
        assertEquals(4, row.sharedMobKills());

        StatsRecord record = new StatsRecord(a, "Alex");
        record.setBlocksBroken(1);
        record.setBlocksPlaced(2);
        record.setPlayerKills(3);
        record.setMobKills(4);
        record.setDeaths(5);
        record.setDistanceOverworld(6);
        record.setDistanceNether(7);
        record.setDistanceEnd(8);
        record.setDamageDealt(9);
        record.setDamageTaken(10);
        record.setItemsCrafted(11);
        record.setItemsConsumed(12);

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate dayMinus2 = today.minusDays(2);
        LocalDate dayMinus1 = today.minusDays(1);

        record.setPlaytimeMillis(10);
        storage.upsertTimeline(record, dayMinus2);
        record.setPlaytimeMillis(20);
        storage.upsertTimeline(record, dayMinus1);
        record.setPlaytimeMillis(30);
        record.setBlocksBroken(5);
        record.setBlocksPlaced(6);
        storage.upsertTimeline(record, today);

        List<Map<String, Object>> timeline = storage.loadTimeline(a, 5);
        assertEquals(3, timeline.size());
        assertEquals(today.toString(), timeline.get(0).get("day"));

        Map<String, Object> range = storage.loadTimelineRange(a, 2);
        assertEquals(dayMinus1.toString(), range.get("from"));
        assertEquals(today.toString(), range.get("to"));
        assertEquals(20L, range.get("playtime_ms"));
        assertEquals(4L, range.get("blocks_broken"));
        assertEquals(4L, range.get("blocks_placed"));
    }

    @Test
    void savesAndLoadsDeathReplays() throws Exception {
        StatsStorage storage = newStorage();
        DeathReplayEntry entry = new DeathReplayEntry(
                123L,
                "uuid-1",
                "Alex",
                "FALL",
                5.0,
                "world",
                1, 2, 3,
                4.5,
                List.of("A", "B"),
                List.of("Z"),
                List.of("item1", "item2")
        );
        storage.saveDeathReplay(entry);

        List<DeathReplayEntry> loaded = storage.loadDeathReplays(5);
        assertEquals(1, loaded.size());
        assertEquals("FALL", loaded.getFirst().cause());
        assertEquals(List.of("A", "B"), loaded.getFirst().nearbyPlayers());
        assertEquals(List.of("item1", "item2"), loaded.getFirst().inventory());
    }

    @Test
    void heatmapDecayWorks() throws Exception {
        StatsStorage storage = newStorage();
        long halfLife = 1000L; // 1 second

        // Initial insert
        storage.incrementHotspot("break", "spawn", "world", 100.0, halfLife);

        // Wait 1 second
        Thread.sleep(1100);

        // Update with 0 delta to trigger decay
        storage.incrementHotspot("break", "spawn", "world", 0.0, halfLife);

        Map<String, Double> hotspots = storage.loadHotspotCounts("break");
        assertEquals(1, hotspots.size());
        double count = hotspots.get("spawn");
        assertTrue(count < 60.0 && count > 40.0, "Count should be around 50, but was " + count);
    }

    @Test
    void loadsExistingUserInLoadOrCreate() throws Exception {
        StatsStorage storage = newStorage();
        UUID uuid = UUID.randomUUID();
        
        // First call creates
        StatsRecord created = storage.loadOrCreate(uuid, "User1");
        assertEquals("User1", created.getName());
        
        // Second call loads existing and updates name
        StatsRecord loaded = storage.loadOrCreate(uuid, "User1_Updated");
        assertEquals("User1_Updated", loaded.getName());
        assertEquals(created.getFirstJoin(), loaded.getFirstJoin());
    }

    @Test
    void notifiesAdminsOnSchemaMismatch() throws Exception {
        Path dataDir = Files.createDirectory(tempDir.resolve("plugin-data-" + UUID.randomUUID()));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        
        // Mock players for notification
        org.bukkit.entity.Player admin = mock(org.bukkit.entity.Player.class);
        when(admin.isOp()).thenReturn(true);
        org.bukkit.entity.Player user = mock(org.bukkit.entity.Player.class);
        when(user.isOp()).thenReturn(false);
        
        Server server = mock(Server.class);
        doReturn(List.of(admin, user)).when(server).getOnlinePlayers();
        when(plugin.getServer()).thenReturn(server);

        // Manually create DB with high version
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dataDir.resolve("stats.db").toAbsolutePath())) {
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("PRAGMA user_version=999;");
            }
        }

        StatsStorage storage = new StatsStorage(plugin);
        try {
            storage.init();
        } catch (java.sql.SQLException ignored) {
            // Expected
        }
        
        verify(admin).sendMessage(org.mockito.ArgumentMatchers.contains("Database schema (999) is newer"));
        verify(user, never()).sendMessage(anyString());
    }

    private StatsStorage newStorage() throws IOException, java.sql.SQLException {
        Path dataDir = Files.createDirectory(tempDir.resolve("plugin-data-" + UUID.randomUUID()));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(plugin.getServer()).thenReturn(server);

        StatsStorage storage = new StatsStorage(plugin);
        storage.init();
        return storage;
    }
}
