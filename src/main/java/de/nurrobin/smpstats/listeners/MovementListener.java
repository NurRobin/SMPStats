package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.Objects;

public class MovementListener implements Listener {
    /** Deltas above this (blocks per event) are teleports or stale positions, not travel. */
    static final double MAX_DISTANCE_PER_EVENT = 100.0;

    private final SMPStats plugin;
    private final StatsService statsService;

    public MovementListener(SMPStats plugin, StatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isTrackingEnabled()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        Entity vehicle = player.getVehicle();
        if (firesVehicleMoveEvent(vehicle)) {
            return; // credited to all riders by onVehicleMove
        }

        record(player, from, to);

        // Passengers never get PlayerMoveEvents; only the controlling rider of a mount does.
        if (vehicle != null) {
            for (Entity passenger : vehicle.getPassengers()) {
                if (passenger instanceof Player rider && !rider.getUniqueId().equals(player.getUniqueId())) {
                    record(rider, from, to);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!isTrackingEnabled()) {
            return;
        }
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player rider) {
                record(rider, event.getFrom(), event.getTo());
            }
        }
    }

    private void record(Player player, Location from, Location to) {
        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            return;
        }

        if (plugin.getSettings().isTrackMovement() && hasMovedPosition(from, to)) {
            double distance = from.toVector().distance(to.toVector());
            if (distance > 0 && distance <= MAX_DISTANCE_PER_EVENT) {
                statsService.addDistance(player.getUniqueId(), to.getWorld().getEnvironment(), distance);
            }
        }

        if (plugin.getSettings().isTrackBiomes() && changedBlock(from, to)) {
            String biome = to.getBlock().getBiome().name();
            statsService.addBiome(player.getUniqueId(), biome);
        }
    }

    private boolean isTrackingEnabled() {
        return plugin.getSettings().isTrackMovement() || plugin.getSettings().isTrackBiomes();
    }

    /** Paper fires VehicleMoveEvent only from the boat and minecart tick loops. */
    private boolean firesVehicleMoveEvent(Entity vehicle) {
        return vehicle instanceof Boat || vehicle instanceof Minecart;
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
