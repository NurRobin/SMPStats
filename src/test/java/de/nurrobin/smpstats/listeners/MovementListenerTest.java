package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

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
}
