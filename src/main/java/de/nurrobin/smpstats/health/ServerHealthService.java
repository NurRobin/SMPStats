package de.nurrobin.smpstats.health;

import de.nurrobin.smpstats.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

        for (World world : Bukkit.getWorlds()) {
            Chunk[] chunks = world.getLoadedChunks();
            int worldChunks = chunks.length;
            int worldEntities = world.getEntities().size();
            int worldHoppers = 0;
            int worldRedstone = 0;
            for (Chunk chunk : chunks) {
                for (BlockState state : chunk.getTileEntities()) {
                    Material type = state.getType();
                    if (type == Material.HOPPER) {
                        worldHoppers++;
                    }
                    if (isRedstoneBlock(type)) {
                        worldRedstone++;
                    }
                }
            }
            totalChunks += worldChunks;
            totalEntities += worldEntities;
            totalHoppers += worldHoppers;
            totalRedstone += worldRedstone;
            worlds.put(world.getName(), new HealthSnapshot.WorldBreakdown(worldChunks, worldEntities, worldHoppers, worldRedstone));
        }
        double costIndex = computeCostIndex(totalChunks, totalEntities, totalHoppers, totalRedstone);
        latest.set(new HealthSnapshot(System.currentTimeMillis(), totalChunks, totalEntities, totalHoppers, totalRedstone, costIndex, worlds));
    }

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
