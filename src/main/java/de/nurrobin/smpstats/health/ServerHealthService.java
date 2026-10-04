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
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 20L, periodTicks).getTaskId();
        sample();
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
    public void sampleNow() {
        sample();
    }

    private void sample() {
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
            int worldEntities = world.getEntities().size();
            int worldHoppers = 0;
            int worldRedstone = 0;
            for (Chunk chunk : chunks) {
                int chunkHoppers = 0;
                int chunkRedstone = 0;
                Map<UUID, Integer> ownerCounts = new HashMap<>();
                
                for (BlockState state : chunk.getTileEntities()) {
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
