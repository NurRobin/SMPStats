package de.nurrobin.smpstats.health;

import de.nurrobin.smpstats.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ServerHealthService {
    private static final int MAX_HISTORY_SIZE = 720; // 1 hour at 5 second intervals, or 60 hours at 5 min intervals
    
    private final Plugin plugin;
    private Settings settings;
    private final AtomicReference<HealthSnapshot> latest = new AtomicReference<>();
    private final Deque<HealthSnapshot> history = new LinkedList<>();
    private int taskId = -1;
    static final long FIRST_SAMPLE_DELAY_TICKS = 2L * 60L * 20L;
    static final long REFRESH_COOLDOWN_MILLIS = 30_000L;
    private volatile long lastSampleMillis;
    private volatile java.util.function.LongSupplier clock = System::currentTimeMillis;

    public ServerHealthService(Plugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        if (!settings.isHealthEnabled()) {
            return;
        }
        long periodTicks = Math.max(1, settings.getHealthSampleMinutes()) * 60L * 20L;
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        // No sample at start or reload: sampling walks every loaded chunk, which is the busiest moment.
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, FIRST_SAMPLE_DELAY_TICKS, periodTicks).getTaskId();
    }

    void setClock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    /** Seconds until the refresh button works again; 0 if a sample may be taken now. */
    public long refreshCooldownSeconds() {
        long remaining = REFRESH_COOLDOWN_MILLIS - (clock.getAsLong() - lastSampleMillis);
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public HealthSnapshot getLatest() {
        return latest.get();
    }
    
    /**
     * Returns historical snapshots within the specified time range.
     * @param minutes Number of minutes of history to retrieve
     * @return List of snapshots, oldest first
     */
    public List<HealthSnapshot> getHistory(int minutes) {
        long cutoffTime = System.currentTimeMillis() - (minutes * 60L * 1000L);
        synchronized (history) {
            return history.stream()
                    .filter(s -> s.timestamp() >= cutoffTime)
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * Triggers an immediate sample. Called by refresh button in GUI.
     */
    public boolean sampleNow() {
        if (refreshCooldownSeconds() > 0) {
            return false;
        }
        sample();
        return true;
    }

    private void sample() {
        lastSampleMillis = clock.getAsLong();
        int totalChunks = 0;
        int totalEntities = 0;
        int totalHoppers = 0;
        int totalRedstone = 0;
        Map<String, HealthSnapshot.WorldBreakdown> worlds = new LinkedHashMap<>();
        List<ChunkSnapshot> chunkSnapshots = new ArrayList<>();
        NamespacedKey ownerKey = new NamespacedKey(plugin, "owner");

        for (World world : Bukkit.getWorlds()) {
            Chunk[] chunks = world.getLoadedChunks();
            int worldChunks = chunks.length;
            int worldEntities = entityCount(world);
            int worldHoppers = 0;
            int worldRedstone = 0;
            for (Chunk chunk : chunks) {
                int chunkHoppers = 0;
                int chunkRedstone = 0;
                Map<UUID, Integer> ownerCounts = new HashMap<>();
                
                for (BlockState state : tileEntities(chunk)) {
                    Material type = state.getType();
                    if (type == Material.HOPPER) {
                        chunkHoppers++;
                    }
                    if (isRedstoneBlock(type)) {
                        chunkRedstone++;
                    }
                    // Collect owner info during initial pass to avoid re-scanning
                    if (state instanceof TileState tileState) {
                        String uuidStr = tileState.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                        if (uuidStr != null) {
                            try {
                                ownerCounts.merge(UUID.fromString(uuidStr), 1, Integer::sum);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
                
                // Get entity array once to avoid multiple iterations
                Entity[] chunkEntitiesArray = chunk.getEntities();
                int chunkEntities = chunkEntitiesArray.length;
                
                // Collect entity owners during single pass
                for (Entity e : chunkEntitiesArray) {
                    if (e instanceof Tameable tameable && tameable.getOwner() != null) {
                        ownerCounts.merge(tameable.getOwner().getUniqueId(), 1, Integer::sum);
                    }
                }
                
                worldHoppers += chunkHoppers;
                worldRedstone += chunkRedstone;
                
                int load = chunkEntities + chunkHoppers + chunkRedstone; // Simple load metric
                if (load > 0) {
                    // Store extracted chunk data instead of Chunk reference to avoid memory leaks
                    chunkSnapshots.add(new ChunkSnapshot(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), 
                            chunkEntities, chunkHoppers + chunkRedstone, load, ownerCounts));
                }
            }
            totalChunks += worldChunks;
            totalEntities += worldEntities;
            totalHoppers += worldHoppers;
            totalRedstone += worldRedstone;
            worlds.put(world.getName(), new HealthSnapshot.WorldBreakdown(worldChunks, worldEntities, worldHoppers, worldRedstone));
        }
        
        // Process Hot Chunks - owner info already collected during initial pass
        chunkSnapshots.sort(Comparator.comparingInt(ChunkSnapshot::load).reversed());
        List<HealthSnapshot.HotChunk> hotChunks = new ArrayList<>();
        
        for (int i = 0; i < Math.min(10, chunkSnapshots.size()); i++) {
            ChunkSnapshot cs = chunkSnapshots.get(i);
            
            String topOwner = "Unknown";
            if (!cs.ownerCounts.isEmpty()) {
                UUID topUuid = Collections.max(cs.ownerCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
                topOwner = Bukkit.getOfflinePlayer(topUuid).getName();
                if (topOwner == null) topOwner = "Unknown (" + topUuid.toString() + ")";
            }
            
            hotChunks.add(new HealthSnapshot.HotChunk(cs.worldName, cs.chunkX, cs.chunkZ, cs.entities, cs.tileEntities, topOwner));
        }

        double costIndex = computeCostIndex(totalChunks, totalEntities, totalHoppers, totalRedstone);
        
        double tps = 20.0;
        try {
            double[] tpsArr = Bukkit.getTPS();
            if (tpsArr != null && tpsArr.length > 0) {
                tps = tpsArr[0];
            }
        } catch (Throwable ignored) {
            // Fallback if method missing
        }
        
        long memMax = Runtime.getRuntime().maxMemory();
        long memUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        HealthSnapshot snapshot = new HealthSnapshot(System.currentTimeMillis(), tps, memUsed, memMax, totalChunks, totalEntities, totalHoppers, totalRedstone, costIndex, worlds, hotChunks);
        latest.set(snapshot);
        
        // Add to history
        synchronized (history) {
            history.addLast(snapshot);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.removeFirst();
            }
        }
    }

    /**
     * Live tile entities without the per-block snapshot copy (the expensive part of a sample on a
     * big server). Falls back to the snapshot variant where the non-snapshot one is not implemented
     * (MockBukkit).
     */
    private static BlockState[] tileEntities(Chunk chunk) {
        try {
            return chunk.getTileEntities(false);
        } catch (UnsupportedOperationException e) {
            return chunk.getTileEntities();
        }
    }

    /** O(1) counter instead of copying the whole entity list. */
    private static int entityCount(World world) {
        try {
            return world.getEntityCount();
        } catch (UnsupportedOperationException e) {
            return world.getEntities().size();
        }
    }

    /** Stores extracted chunk data to avoid holding Chunk references which could prevent garbage collection */
    private record ChunkSnapshot(String worldName, int chunkX, int chunkZ, int entities, int tileEntities, int load, Map<UUID, Integer> ownerCounts) {}

    private double computeCostIndex(int chunks, int entities, int hoppers, int redstone) {
        double value = chunks * settings.getHealthChunkWeight()
                + entities * settings.getHealthEntityWeight()
                + hoppers * settings.getHealthHopperWeight()
                + redstone * settings.getHealthRedstoneWeight();
        return Math.min(100.0, Math.round(value * 100.0) / 100.0);
    }

    private boolean isRedstoneBlock(Material type) {
        return switch (type) {
            case DROPPER, DISPENSER, OBSERVER, PISTON, STICKY_PISTON, NOTE_BLOCK,
                    COMPARATOR, REPEATER, TARGET, LECTERN, REDSTONE_LAMP, REDSTONE_TORCH, REDSTONE_BLOCK -> true;
            default -> false;
        };
    }
}
