package de.nurrobin.smpstats.timeline;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public class DeathReplayService {
    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;
    private final Deque<DeathReplayEntry> buffer = new ArrayDeque<>();

    public DeathReplayService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        // no scheduler needed
    }

    public void shutdown() {
        // nothing to stop
    }

    public void capture(Player player, String cause, double fallDistance) {
        if (!settings.isDeathReplayEnabled()) {
            return;
        }
        Location loc = player.getLocation();
        List<String> nearbyPlayers = new ArrayList<>();
        List<String> nearbyMobs = new ArrayList<>();
        int radius = settings.getDeathReplayNearbyRadius();
        World world = player.getWorld();
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof Player p) {
                if (!p.getUniqueId().equals(player.getUniqueId())) {
                    nearbyPlayers.add(p.getName());
                }
            } else if (e instanceof LivingEntity living) {
                nearbyMobs.add(living.getType().name());
            }
        }

        double value = 0;
        if (settings.isDeathReplayInventoryValue()) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null) {
                    value += stack.getAmount(); // simple value proxy
                }
            }
        }

        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                player.getUniqueId().toString(),
                player.getName(),
                cause,
                player.getHealth(),
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                fallDistance,
                value,
                nearbyPlayers,
                nearbyMobs
        );
        buffer.addFirst(entry);
        while (buffer.size() > settings.getDeathReplayLimit()) {
            buffer.removeLast();
        }
        try {
            storage.saveDeathReplay(entry);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save death replay: " + e.getMessage());
        }
    }

    public List<DeathReplayEntry> recent(int limit) {
        return buffer.stream().limit(limit).toList();
    }
}
