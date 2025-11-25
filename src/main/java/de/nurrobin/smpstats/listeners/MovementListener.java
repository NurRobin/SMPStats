package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MovementListener implements Listener {
    private final StatsService statsService;
    private final Settings settings;

    public MovementListener(StatsService statsService, Settings settings) {
        this.statsService = statsService;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!settings.isTrackMovement() && !settings.isTrackBiomes()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        boolean movedPosition = hasMovedPosition(from, to);

        if (settings.isTrackMovement() && movedPosition) {
            double distance = from.toVector().distance(to.toVector());
            if (distance > 0) {
                statsService.addDistance(player.getUniqueId(), to.getWorld().getEnvironment(), distance);
            }
        }

        if (settings.isTrackBiomes() && changedBlock(from, to)) {
            String biome = to.getBlock().getBiome().name();
            statsService.addBiome(player.getUniqueId(), biome);
        }
    }

    private boolean hasMovedPosition(Location from, Location to) {
        return from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    private boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
