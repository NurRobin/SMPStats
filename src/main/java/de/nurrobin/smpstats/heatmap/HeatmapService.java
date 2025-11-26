package de.nurrobin.smpstats.heatmap;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.HeatmapEntry;
import de.nurrobin.smpstats.database.HeatmapEvent;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeatmapService {
    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;
    private List<HotspotDefinition> hotspots;
    private final List<HeatmapEntry> pendingEvents = new ArrayList<>();
    private final Map<HotspotKey, Double> hotspotCounts = new ConcurrentHashMap<>();
    private int flushTaskId = -1;
    private int positionTaskId = -1;

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
        
        // Track player positions every 5 seconds (100 ticks)
        positionTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                track("POSITION", player.getLocation());
            }
        }, 100L, 100L).getTaskId();
    }

    public void shutdown() {
        if (flushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        if (positionTaskId != -1) {
            Bukkit.getScheduler().cancelTask(positionTaskId);
            positionTaskId = -1;
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
        synchronized (pendingEvents) {
            pendingEvents.add(new HeatmapEntry(type, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), 1.0, System.currentTimeMillis()));
        }
        for (HotspotDefinition hotspot : hotspots) {
            if (hotspot.contains(location)) {
                hotspotCounts.merge(new HotspotKey(type, hotspot.getName(), hotspot.getWorld()), 1.0, Double::sum);
            }
        }
    }

    public List<HeatmapBin> loadTop(String type, int limit) {
        // Deprecated: Use generateHeatmap instead. Returning empty for now or could implement a default query.
        // For backward compatibility, let's return a default view (last 7 days, no decay?)
        return generateHeatmap(type, "world", System.currentTimeMillis() - 7L * 24 * 3600 * 1000, System.currentTimeMillis(), 0);
    }

    public List<HeatmapBin> generateHeatmap(String type, String world, long since, long until, double decayHalfLifeHours) {
        try {
            List<HeatmapEvent> events = storage.getHeatmapEvents(type, world, since, until);
            Map<Long, Double> chunkValues = new HashMap<>();
            long now = System.currentTimeMillis();
            double halfLifeMillis = decayHalfLifeHours * 3600 * 1000;

            for (HeatmapEvent event : events) {
                long chunkKey = (((long) (int) (event.x()) >> 4) & 0xFFFFFFFFL) | ((((long) (int) (event.z()) >> 4) & 0xFFFFFFFFL) << 32);
                double value = event.value();
                if (decayHalfLifeHours > 0) {
                    long age = now - event.timestamp();
                    if (age > 0) {
                        value *= Math.pow(0.5, age / halfLifeMillis);
                    }
                }
                chunkValues.merge(chunkKey, value, Double::sum);
            }

            List<HeatmapBin> bins = new ArrayList<>();
            for (Map.Entry<Long, Double> entry : chunkValues.entrySet()) {
                int cx = (int) (entry.getKey() & 0xFFFFFFFFL);
                int cz = (int) (entry.getKey() >>> 32);
                bins.add(new HeatmapBin(type, world, cx, cz, entry.getValue()));
            }
            // Sort by value desc
            bins.sort((a, b) -> Double.compare(b.getCount(), a.getCount()));
            return bins;
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not generate heatmap: " + e.getMessage());
            return List.of();
        }
    }

    public Map<String, Double> loadHotspots(String type) {
        try {
            return storage.loadHotspotCounts(type);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load heatmap hotspots: " + e.getMessage());
            return Map.of();
        }
    }

    private void flush() {
        List<HeatmapEntry> batch;
        synchronized (pendingEvents) {
            if (pendingEvents.isEmpty() && hotspotCounts.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
        }

        if (!batch.isEmpty()) {
            try {
                storage.insertHeatmapEntries(batch);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not persist heatmap events: " + e.getMessage());
            }
        }

        long halfLife = (long) (settings.getHeatmapDecayHalfLifeHours() * 3600 * 1000);
        List<Map.Entry<HotspotKey, Double>> hotspotBatch = new ArrayList<>(hotspotCounts.entrySet());
        hotspotCounts.clear();
        for (Map.Entry<HotspotKey, Double> entry : hotspotBatch) {
            try {
                storage.incrementHotspot(entry.getKey().type, entry.getKey().hotspot, entry.getKey().world, entry.getValue(), halfLife);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not persist hotspot bin: " + e.getMessage());
            }
        }
    }

    private record HotspotKey(String type, String hotspot, String world) {
    }
}
