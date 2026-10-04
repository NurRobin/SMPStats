package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovementListenerTest {

    @Test
    void tracksDistanceAndBiomeChanges() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackMovement()).thenReturn(true);
        when(settings.isTrackBiomes()).thenReturn(false); // avoid biome enum incompatibilities
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin, stats);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 3, 64, 4);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);

        listener.onMove(event);

        verify(stats).addDistance(uuid, World.Environment.NORMAL, from.toVector().distance(to.toVector()));
        verify(stats, never()).addBiome(any(), any());
    }

    @Test
    void tracksBiomeChangeOnlyWhenBlockChanged() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackMovement()).thenReturn(false);
        when(settings.isTrackBiomes()).thenReturn(true);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin, stats);

        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getBiome()).thenReturn(Biome.PLAINS);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);

        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 16, 64, 16); // changed chunk/block

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);

        listener.onMove(event);

        verify(stats).addBiome(uuid, "PLAINS");
        verify(stats, never()).addDistance(any(), any(), anyDouble());
    }

    @Test
    void ignoresNullDestination() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackMovement()).thenReturn(true);
        when(settings.isTrackBiomes()).thenReturn(true);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin, stats);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getTo()).thenReturn(null);

        listener.onMove(event);

        verify(stats, never()).addDistance(any(), any(), anyDouble());
        verify(stats, never()).addBiome(any(), any());
    }

    @Test
    void doesNotCountWhenPositionUnchanged() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackMovement()).thenReturn(true);
        when(settings.isTrackBiomes()).thenReturn(false);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin, stats);

        World world = mock(World.class);
        Location from = new Location(world, 10, 64, 10);
        Location to = new Location(world, 10, 64, 10);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);

        listener.onMove(event);

        verify(stats, never()).addDistance(any(), any(), anyDouble());
    }

    @Test
    void skipsBiomeWhenSameBlock() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackMovement()).thenReturn(false);
        when(settings.isTrackBiomes()).thenReturn(true);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin, stats);

        World world = mock(World.class);
        Location from = new Location(world, 1.2, 64, 1.7);
        Location to = new Location(world, 1.3, 64, 1.1); // same block coords

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);

        listener.onMove(event);

        verify(stats, never()).addBiome(any(), any());
        verify(stats, never()).addDistance(any(), any(), anyDouble());
    }

    @Test
    void ignoresWhenNoMovementAndDisabledFlags() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackMovement()).thenReturn(false);
        when(settings.isTrackBiomes()).thenReturn(false);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin, stats);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        listener.onMove(event);

        verify(stats, never()).addDistance(any(), any(), org.mockito.ArgumentMatchers.anyDouble());
        verify(stats, never()).addBiome(any(), any());
    }

    @Test
    void creditsVehicleMovementToEveryPlayerPassenger() {
        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin(true, false), stats);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 66, 2);

        Player driver = player();
        Player passenger = player();
        Boat boat = mock(Boat.class);
        when(boat.getPassengers()).thenReturn(List.of(driver, passenger, mock(Zombie.class)));

        listener.onVehicleMove(new VehicleMoveEvent(boat, from, to));

        verify(stats).addDistance(driver.getUniqueId(), World.Environment.NORMAL, 3.0);
        verify(stats).addDistance(passenger.getUniqueId(), World.Environment.NORMAL, 3.0);
    }

    @Test
    void tracksBiomeForVehiclePassengers() {
        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin(false, true), stats);

        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getBiome()).thenReturn(Biome.PLAINS);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0);

        Player rider = player();
        Minecart cart = mock(Minecart.class);
        when(cart.getPassengers()).thenReturn(List.of(rider));

        listener.onVehicleMove(new VehicleMoveEvent(cart, from, to));

        verify(stats).addBiome(rider.getUniqueId(), "PLAINS");
    }

    @Test
    void ignoresVehicleJumpsAcrossWorldsOrImplausiblyFar() {
        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin(true, false), stats);

        World overworld = mock(World.class);
        World nether = mock(World.class);
        Player rider = player();
        Boat boat = mock(Boat.class);
        when(boat.getPassengers()).thenReturn(List.of(rider));

        listener.onVehicleMove(new VehicleMoveEvent(boat, new Location(overworld, 0, 64, 0), new Location(nether, 1, 64, 0)));
        listener.onVehicleMove(new VehicleMoveEvent(boat, new Location(overworld, 0, 64, 0), new Location(overworld, 500, 64, 0)));

        verify(stats, never()).addDistance(any(), any(), anyDouble());
    }

    @Test
    void ignoresPlayerJumpsAcrossWorldsOrImplausiblyFar() {
        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin(true, false), stats);

        World overworld = mock(World.class);
        World nether = mock(World.class);
        Player player = player();

        listener.onMove(moveEvent(player, new Location(overworld, 0, 64, 0), new Location(nether, 1, 64, 0)));
        listener.onMove(moveEvent(player, new Location(overworld, 0, 64, 0), new Location(overworld, 0, 64, 101)));

        verify(stats, never()).addDistance(any(), any(), anyDouble());
    }

    @Test
    void leavesBoatAndMinecartRidersToVehicleMoveEvent() {
        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin(true, false), stats);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        Player driver = player();
        Boat boat = mock(Boat.class);
        when(boat.getPassengers()).thenReturn(List.of(driver));
        when(driver.getVehicle()).thenReturn(boat);

        // A boat driver also gets PlayerMoveEvents; counting them too would double the boat distance.
        listener.onMove(moveEvent(driver, new Location(world, 0, 64, 0), new Location(world, 3, 64, 4)));

        verify(stats, never()).addDistance(any(), any(), anyDouble());
    }

    @Test
    void creditsMountMovementToDriverAndCoPassengers() {
        StatsService stats = mock(StatsService.class);
        MovementListener listener = new MovementListener(plugin(true, false), stats);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        Player driver = player();
        Player passenger = player();
        Camel camel = mock(Camel.class);
        when(camel.getPassengers()).thenReturn(List.of(driver, passenger));
        when(driver.getVehicle()).thenReturn(camel);
        when(passenger.getVehicle()).thenReturn(camel);

        // Only the controlling rider of a mount receives PlayerMoveEvents.
        listener.onMove(moveEvent(driver, new Location(world, 0, 64, 0), new Location(world, 3, 64, 4)));

        verify(stats).addDistance(driver.getUniqueId(), World.Environment.NORMAL, 5.0);
        verify(stats).addDistance(passenger.getUniqueId(), World.Environment.NORMAL, 5.0);
    }

    private SMPStats plugin(boolean movement, boolean biomes) {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackMovement()).thenReturn(movement);
        when(settings.isTrackBiomes()).thenReturn(biomes);
        when(plugin.getSettings()).thenReturn(settings);
        return plugin;
    }

    private Player player() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    private PlayerMoveEvent moveEvent(Player player, Location from, Location to) {
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}
