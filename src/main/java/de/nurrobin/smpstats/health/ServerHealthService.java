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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ServerHealthService {
    private final Plugin plugin;
    private Settings settings;
    private final AtomicReference<HealthSnapshot> latest = new AtomicReference<>();
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

    private void sample() {
        int totalChunks = 0;
        int totalEntities = 0;
        int totalHoppers = 0;
        int totalRedstone = 0;
        Map<String, HealthSnapshot.WorldBreakdown> worlds = new LinkedHashMap<>();
        List<ChunkSnapshot> chunkSnapshots = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            Chunk[] chunks = world.getLoadedChunks();
            int worldChunks = chunks.length;
            int worldEntities = world.getEntities().size();
            int worldHoppers = 0;
            int worldRedstone = 0;
            for (Chunk chunk : chunks) {
                int chunkHoppers = 0;
                int chunkRedstone = 0;
                for (BlockState state : chunk.getTileEntities()) {
                    Material type = state.getType();
                    if (type == Material.HOPPER) {
                        chunkHoppers++;
                    }
                    if (isRedstoneBlock(type)) {
                        chunkRedstone++;
                    }
                }
                worldHoppers += chunkHoppers;
                worldRedstone += chunkRedstone;
                
                int chunkEntities = chunk.getEntities().length;
                int load = chunkEntities + chunkHoppers + chunkRedstone; // Simple load metric
                if (load > 0) {
                    chunkSnapshots.add(new ChunkSnapshot(chunk, chunkEntities, chunkHoppers + chunkRedstone, load));
                }
            }
            totalChunks += worldChunks;
            totalEntities += worldEntities;
            totalHoppers += worldHoppers;
            totalRedstone += worldRedstone;
            worlds.put(world.getName(), new HealthSnapshot.WorldBreakdown(worldChunks, worldEntities, worldHoppers, worldRedstone));
        }
        
        // Process Hot Chunks
        chunkSnapshots.sort(Comparator.comparingInt(ChunkSnapshot::load).reversed());
        List<HealthSnapshot.HotChunk> hotChunks = new ArrayList<>();
        NamespacedKey ownerKey = new NamespacedKey(plugin, "owner");
        
        for (int i = 0; i < Math.min(10, chunkSnapshots.size()); i++) {
            ChunkSnapshot cs = chunkSnapshots.get(i);
            Chunk c = cs.chunk;
            
            // Determine Owner
            Map<UUID, Integer> ownerCounts = new HashMap<>();
            
            // Check Entities
            for (Entity e : c.getEntities()) {
                if (e instanceof Tameable tameable && tameable.getOwner() != null) {
                    ownerCounts.merge(tameable.getOwner().getUniqueId(), 1, Integer::sum);
                }
            }
            
            // Check Tile Entities
            for (BlockState state : c.getTileEntities()) {
                if (state instanceof TileState tileState) {
                    String uuidStr = tileState.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                    if (uuidStr != null) {
                        try {
                            ownerCounts.merge(UUID.fromString(uuidStr), 1, Integer::sum);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            
            String topOwner = "Unknown";
            if (!ownerCounts.isEmpty()) {
                UUID topUuid = Collections.max(ownerCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
                topOwner = Bukkit.getOfflinePlayer(topUuid).getName();
                if (topOwner == null) topOwner = "Unknown (" + topUuid.toString().substring(0, 8) + ")";
            }
            
            hotChunks.add(new HealthSnapshot.HotChunk(c.getWorld().getName(), c.getX(), c.getZ(), cs.entities, cs.tileEntities, topOwner));
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

        latest.set(new HealthSnapshot(System.currentTimeMillis(), tps, memUsed, memMax, totalChunks, totalEntities, totalHoppers, totalRedstone, costIndex, worlds, hotChunks));
    }

    private record ChunkSnapshot(Chunk chunk, int entities, int tileEntities, int load) {}

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
