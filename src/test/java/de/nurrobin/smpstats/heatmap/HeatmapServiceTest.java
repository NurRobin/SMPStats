package de.nurrobin.smpstats.heatmap;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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

        verify(storage).incrementHeatmapBin("BREAK", "world", 2, 3, 1);
        verify(storage).incrementHotspot("BREAK", "spawn", "world", 1);
    }

    @Test
    void ignoresWhenDisabledOrInvalidLocation() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = settings(false, List.of());

        HeatmapService service = new HeatmapService(plugin, storage, settings);
        service.track("BREAK", null);
        verify(storage, never()).incrementHeatmapBin(any(), any(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void returnsEmptyOnStorageErrors() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.loadHeatmapBins(anyString(), anyInt())).thenThrow(new java.sql.SQLException("fail"));
        when(storage.loadHotspotCounts(anyString())).thenThrow(new java.sql.SQLException("fail"));
        Settings settings = settings(true, List.of());

        HeatmapService service = new HeatmapService(plugin, storage, settings);
        assertTrue(service.loadTop("BREAK", 5).isEmpty());
        assertTrue(service.loadHotspots("BREAK").isEmpty());
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
                false, 0, "KEY", 1, weights,
                true, 0, 1, enabled, 1, List.of(), hotspots,
                false, 1, 1, false,
                true, true, 1, 1,
                false, 1, 0, 0, 0, 0,
                false, 1, 1, "", 1, 1);
    }
}
