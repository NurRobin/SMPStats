package de.nurrobin.smpstats.heatmap;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.heatmap.HotspotDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeatmapService {
    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;
    private List<HotspotDefinition> hotspots;
    private final Map<BinKey, Long> pending = new ConcurrentHashMap<>();
    private final Map<HotspotKey, Long> hotspotCounts = new ConcurrentHashMap<>();
    private int flushTaskId = -1;

    public HeatmapService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
        this.hotspots = settings.getHeatmapHotspots();
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
        this.hotspots = settings.getHeatmapHotspots();
    }

    public void start() {
        if (!settings.isHeatmapEnabled()) {
            return;
        }
        long periodTicks = settings.getHeatmapFlushMinutes() * 60L * 20L;
        flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, periodTicks, periodTicks).getTaskId();
    }

    public void shutdown() {
        if (flushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        flush();
    }

    public void track(String type, Location location) {
        if (!settings.isHeatmapEnabled()) {
            return;
        }
        if (location == null || location.getWorld() == null) {
            return;
        }
        BinKey key = BinKey.fromLocation(type, location);
        pending.merge(key, 1L, Long::sum);
        for (HotspotDefinition hotspot : hotspots) {
            if (hotspot.contains(location)) {
                hotspotCounts.merge(new HotspotKey(type, hotspot.getName(), hotspot.getWorld()), 1L, Long::sum);
            }
        }
    }

    public List<HeatmapBin> loadTop(String type, int limit) {
        try {
            return storage.loadHeatmapBins(type, limit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load heatmap bins: " + e.getMessage());
            return List.of();
        }
    }

    public Map<String, Long> loadHotspots(String type) {
        try {
            return storage.loadHotspotCounts(type);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load heatmap hotspots: " + e.getMessage());
            return Map.of();
        }
    }

    private void flush() {
        if (pending.isEmpty()) {
            // still flush hotspot counts
        }
        List<Map.Entry<BinKey, Long>> batch = new ArrayList<>(pending.entrySet());
        pending.clear();
        for (Map.Entry<BinKey, Long> entry : batch) {
            BinKey key = entry.getKey();
            try {
                storage.incrementHeatmapBin(key.type, key.world, key.chunkX, key.chunkZ, entry.getValue());
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not persist heatmap bin: " + e.getMessage());
            }
        }
        List<Map.Entry<HotspotKey, Long>> hotspotBatch = new ArrayList<>(hotspotCounts.entrySet());
        hotspotCounts.clear();
        for (Map.Entry<HotspotKey, Long> entry : hotspotBatch) {
            try {
                storage.incrementHotspot(entry.getKey().type, entry.getKey().hotspot, entry.getKey().world, entry.getValue());
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not persist hotspot bin: " + e.getMessage());
            }
        }
    }

    private record BinKey(String type, String world, int chunkX, int chunkZ) {
        static BinKey fromLocation(String type, Location location) {
            World world = location.getWorld();
            return new BinKey(type, world != null ? world.getName() : "unknown", location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }

    private record HotspotKey(String type, String hotspot, String world) {
    }
}
