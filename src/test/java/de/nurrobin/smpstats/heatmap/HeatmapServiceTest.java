package de.nurrobin.smpstats.heatmap;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.HeatmapEvent;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HeatmapServiceTest {

    @Test
    void tracksAndFlushesBinsAndHotspots() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        HotspotDefinition hotspot = new HotspotDefinition("spawn", "world", 0, 0, 100, 100);
        Settings settings = settings(true, List.of(hotspot));

        HeatmapService service = new HeatmapService(plugin, storage, settings);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 32, 64, 48); // chunk (2,3)

        service.track("BREAK", loc);
        service.shutdown(); // flush pending counts

        verify(storage).insertHeatmapEntries(anyList());
        verify(storage).incrementHotspot("BREAK", "spawn", "world", 1.0, 0L);
    }

    @Test
    void ignoresWhenDisabledOrInvalidLocation() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = settings(false, List.of());

        HeatmapService service = new HeatmapService(plugin, storage, settings);
        service.track("BREAK", null);
        verify(storage, never()).insertHeatmapEntries(anyList());
    }

    @Test
    void returnsEmptyOnStorageErrors() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.getHeatmapEvents(anyString(), anyString(), anyLong(), anyLong())).thenThrow(new java.sql.SQLException("fail"));
        when(storage.loadHotspotCounts(anyString())).thenThrow(new java.sql.SQLException("fail"));
        Settings settings = settings(true, List.of());

        HeatmapService service = new HeatmapService(plugin, storage, settings);
        assertTrue(service.loadTop("BREAK", 5).isEmpty());
        assertTrue(service.loadHotspots("BREAK").isEmpty());
    }

    @Test
    void calculatesDecayCorrectly() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = settings(true, List.of());

        HeatmapService service = new HeatmapService(plugin, storage, settings);

        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3600 * 1000;

        // Event 1 hour ago with value 10.0
        // Half-life is 1 hour. So value should be 5.0.
        when(storage.getHeatmapEvents(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(new HeatmapEvent(16, 64, 32, 10.0, oneHourAgo)));

        List<HeatmapBin> bins = service.generateHeatmap("BREAK", "world", 0, now, 1.0);

        assertEquals(1, bins.size());
        // Allow some delta for execution time
        assertEquals(5.0, bins.get(0).getCount(), 0.1);
    }

    @Test
    void updatesSettings() throws Exception {
        Plugin plugin = mock(Plugin.class);
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = settings(true, List.of());
        HeatmapService service = new HeatmapService(plugin, storage, settings);

        HotspotDefinition newHotspot = new HotspotDefinition("new", "world", 0, 0, 10, 10);
        Settings newSettings = settings(true, List.of(newHotspot));

        service.updateSettings(newSettings);

        // Verify internal state by tracking a location in the new hotspot
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 5, 64, 5);

        service.track("TEST", loc);
        service.shutdown();

        verify(storage).incrementHotspot(eq("TEST"), eq("new"), eq("world"), anyDouble(), anyLong());
    }

    @Test
    void startsAndStopsTasks() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = settings(true, List.of());

        // Mock scheduler
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);
        when(task.getTaskId()).thenReturn(123);
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

        // Mock Bukkit.getScheduler() - this is static, so we need MockedStatic
        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);

            HeatmapService service = new HeatmapService(plugin, storage, settings);
            service.start();

            verify(scheduler).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong());
            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());

            service.shutdown();
            verify(scheduler, times(2)).cancelTask(123);
        }
    }

    @Test
    void aggregatesWithCustomGridSize() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = settings(true, List.of());

        HeatmapService service = new HeatmapService(plugin, storage, settings);

        long now = System.currentTimeMillis();
        // Two events:
        // (10, 10) -> Grid 16: (0,0). Grid 8: (1,1).
        // (20, 20) -> Grid 16: (1,1). Grid 8: (2,2).
        // (12, 12) -> Grid 16: (0,0). Grid 8: (1,1).

        when(storage.getHeatmapEvents(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(
                        new HeatmapEvent(10, 64, 10, 1.0, now),
                        new HeatmapEvent(20, 64, 20, 1.0, now),
                        new HeatmapEvent(12, 64, 12, 1.0, now)
                ));

        // Grid Size 16
        List<HeatmapBin> bins16 = service.generateHeatmap("BREAK", "world", 0, now, 0.0, 16);
        // (10,10) and (12,12) are in (0,0). (20,20) is in (1,1).
        // So 2 bins. (0,0) count 2. (1,1) count 1.
        assertEquals(2, bins16.size());
        HeatmapBin bin00 = bins16.stream().filter(b -> b.getX() == 0 && b.getZ() == 0).findFirst().orElseThrow();
        assertEquals(2.0, bin00.getCount(), 0.01);
        assertEquals(16, bin00.getGridSize());

        // Grid Size 8
        List<HeatmapBin> bins8 = service.generateHeatmap("BREAK", "world", 0, now, 0.0, 8);
        // (10,10) -> (1,1). (12,12) -> (1,1). (20,20) -> (2,2).
        // So 2 bins. (1,1) count 2. (2,2) count 1.
        assertEquals(2, bins8.size());
        HeatmapBin bin11 = bins8.stream().filter(b -> b.getX() == 1 && b.getZ() == 1).findFirst().orElseThrow();
        assertEquals(2.0, bin11.getCount(), 0.01);
        assertEquals(8, bin11.getGridSize());

        // Grid Size 32
        List<HeatmapBin> bins32 = service.generateHeatmap("BREAK", "world", 0, now, 0.0, 32);
        // (10,10) -> (0,0). (12,12) -> (0,0). (20,20) -> (0,0).
        // So 1 bin. (0,0) count 3.
        assertEquals(1, bins32.size());
        assertEquals(3.0, bins32.get(0).getCount(), 0.01);
    }

    private Settings settings(boolean enabled, List<HotspotDefinition> hotspots) {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(0),
                new SkillWeights.CombatWeights(0, 0, 0),
                new SkillWeights.ExplorationWeights(0, 0),
                new SkillWeights.BuilderWeights(0),
                new SkillWeights.FarmerWeights(0, 0)
        );
        return new Settings(true, true, true, true, true, true, true,
                false, "127.0.0.1", 0, "KEY", 1, weights,
                true, 0L, 1L, enabled, 1, 0.0, List.of(), hotspots,
                false, 1, 1, false,
                true, true, 1, 1,
                false, 1, 0, 0, 0, 0, de.nurrobin.smpstats.health.HealthThresholds.defaults(),
                false, 1, 1, "", 1, 1,
                Settings.DashboardSettings.defaults());
    }
}
