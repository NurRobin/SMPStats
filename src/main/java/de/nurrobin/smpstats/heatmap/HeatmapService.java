package de.nurrobin.smpstats.heatmap;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.HeatmapEntry;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    // Last recorded POSITION per player; standing still (AFK farms) adds no rows.
    private final Map<java.util.UUID, Location> lastPositions = new HashMap<>();
    private static final double MIN_POSITION_MOVE_SQUARED = 2.0 * 2.0;
    static final int MAX_PENDING_EVENTS = 200_000;
    static final long MAX_WINDOW_MILLIS = java.util.concurrent.TimeUnit.DAYS.toMillis(90);
    public static final int MIN_GRID = 8;
    public static final int MAX_GRID = 512;
    static final int MAX_BINS = 5000;
    static final long CACHE_TTL_MILLIS = 60_000L;
    static final long BUSY_WAIT_MILLIS = 5_000L;
    private static final int MAX_CACHE_ENTRIES = 64;
    // At most one heatmap scan at a time: every scan holds the storage lock.
    private final java.util.concurrent.locks.ReentrantLock computeLock = new java.util.concurrent.locks.ReentrantLock();
    private final Map<String, CachedHeatmap> heatmapCache = new ConcurrentHashMap<>();
    private volatile java.util.function.LongSupplier clock = System::currentTimeMillis;
    private int flushTaskId = -1;
    private int positionTaskId = -1;

    public HeatmapService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
        this.hotspots = settings.getHeatmapHotspots();
    }

    /** Thrown when another heatmap calculation is running and did not finish in time. */
    public static class HeatmapBusyException extends RuntimeException {
        public HeatmapBusyException() {
            super("Heatmap calculation busy, try again shortly");
        }
    }

    void setClock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
        heatmapCache.clear();
        this.hotspots = settings.getHeatmapHotspots();
    }

    public void start() {
        if (!settings.isHeatmapEnabled()) {
            return;
        }
        long periodTicks = settings.getHeatmapFlushMinutes() * 60L * 20L;
        flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush, periodTicks, periodTicks).getTaskId();
        
        // Track player positions every 5 seconds (100 ticks)
        positionTaskId = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> samplePositions(Bukkit.getOnlinePlayers()), 100L, 100L).getTaskId();
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

    void samplePositions(java.util.Collection<? extends org.bukkit.entity.Player> players) {
        java.util.Set<java.util.UUID> online = new java.util.HashSet<>();
        for (org.bukkit.entity.Player player : players) {
            online.add(player.getUniqueId());
            Location location = player.getLocation();
            Location last = lastPositions.get(player.getUniqueId());
            boolean moved = last == null
                    || last.getWorld() != location.getWorld()
                    || last.distanceSquared(location) >= MIN_POSITION_MOVE_SQUARED;
            if (moved) {
                lastPositions.put(player.getUniqueId(), location.clone());
                track("POSITION", location);
            }
        }
        lastPositions.keySet().retainAll(online);
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
        return generateHeatmap(type, world, since, until, decayHalfLifeHours, 16);
    }

    public List<HeatmapBin> generateHeatmap(String type, String world, long since, long until, double decayHalfLifeHours, int gridSize) {
        int grid = Math.max(MIN_GRID, Math.min(gridSize, MAX_GRID));
        long nowMs = clock.getAsLong();
        // since/until default to "now" in the callers; bucket them so identical views share a cache entry.
        String cacheKey = type + '|' + world + '|' + (since / 60_000L) + '|' + (until / 60_000L) + '|' + decayHalfLifeHours + '|' + grid;
        CachedHeatmap cached = heatmapCache.get(cacheKey);
        if (cached != null && nowMs - cached.createdAt < CACHE_TTL_MILLIS) {
            return cached.bins;
        }
        boolean locked;
        try {
            locked = computeLock.tryLock(BUSY_WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HeatmapBusyException();
        }
        if (!locked) {
            throw new HeatmapBusyException();
        }
        try {
            // Another request may have filled the cache while this one waited for the lock.
            cached = heatmapCache.get(cacheKey);
            if (cached != null && clock.getAsLong() - cached.createdAt < CACHE_TTL_MILLIS) {
                return cached.bins;
            }
            List<HeatmapBin> bins = computeHeatmap(type, world, since, until, decayHalfLifeHours, grid);
            if (bins != null) {
                if (heatmapCache.size() >= MAX_CACHE_ENTRIES) {
                    long cutoff = clock.getAsLong() - CACHE_TTL_MILLIS;
                    heatmapCache.values().removeIf(c -> c.createdAt < cutoff);
                    if (heatmapCache.size() >= MAX_CACHE_ENTRIES) {
                        heatmapCache.clear();
                    }
                }
                heatmapCache.put(cacheKey, new CachedHeatmap(clock.getAsLong(), bins));
                return bins;
            }
            return List.of();
        } finally {
            computeLock.unlock();
        }
    }

    // Returns null on a storage error so failures are not cached.
    private List<HeatmapBin> computeHeatmap(String type, String world, long since, long until, double decayHalfLifeHours, int grid) {
        // Every scan holds the storage lock, so the window is bounded no matter which caller asks.
        long from = Math.max(since, until - MAX_WINDOW_MILLIS);
        int retentionDays = settings.getRetentionSettings().heatmapEventsDays();
        if (retentionDays > 0) {
            from = Math.max(from, System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(retentionDays));
        }
        try {
            Map<Long, Double> chunkValues = new HashMap<>();
            long now = System.currentTimeMillis();
            double halfLifeMillis = decayHalfLifeHours * 3600 * 1000;

            storage.forEachHeatmapEvent(type, world, from, until, (x, z, eventValue, timestamp) -> {
                int binX = (int) Math.floor(x / grid);
                int binZ = (int) Math.floor(z / grid);
                long chunkKey = ((long) binX & 0xFFFFFFFFL) | (((long) binZ & 0xFFFFFFFFL) << 32);

                double value = eventValue;
                if (decayHalfLifeHours > 0) {
                    long age = now - timestamp;
                    if (age > 0) {
                        value *= Math.pow(0.5, age / halfLifeMillis);
                    }
                }
                chunkValues.merge(chunkKey, value, Double::sum);
            });

            List<HeatmapBin> bins = new ArrayList<>();
            for (Map.Entry<Long, Double> entry : chunkValues.entrySet()) {
                int cx = (int) (entry.getKey() & 0xFFFFFFFFL);
                int cz = (int) (entry.getKey() >>> 32);
                bins.add(new HeatmapBin(type, world, cx, cz, grid, entry.getValue()));
            }
            // Sort by value desc, keep only the heaviest bins
            bins.sort((a, b) -> Double.compare(b.getCount(), a.getCount()));
            if (bins.size() > MAX_BINS) {
                bins = new ArrayList<>(bins.subList(0, MAX_BINS));
            }
            return List.copyOf(bins);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not generate heatmap: " + e.getMessage());
            return null;
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
                synchronized (pendingEvents) {
                    // Keep the batch for the next flush instead of losing it; cap so a dead DB cannot grow the list forever.
                    pendingEvents.addAll(0, batch);
                    int excess = pendingEvents.size() - MAX_PENDING_EVENTS;
                    if (excess > 0) {
                        pendingEvents.subList(0, excess).clear();
                    }
                }
            }
        }

        long halfLife = (long) (settings.getHeatmapDecayHalfLifeHours() * 3600 * 1000);
        for (HotspotKey key : new ArrayList<>(hotspotCounts.keySet())) {
            // remove() hands over exactly the value that is persisted; increments after it start a new entry.
            Double delta = hotspotCounts.remove(key);
            if (delta == null) {
                continue;
            }
            try {
                storage.incrementHotspot(key.type, key.hotspot, key.world, delta, halfLife);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not persist hotspot bin: " + e.getMessage());
            }
        }
    }

    private record CachedHeatmap(long createdAt, List<HeatmapBin> bins) {
    }

    private record HotspotKey(String type, String hotspot, String world) {
    }
}
