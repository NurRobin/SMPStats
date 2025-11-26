package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockListenerTest {

    @Test
    void countsBlocksWhenTrackingEnabled() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackBlocks()).thenReturn(true);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        BlockListener listener = new BlockListener(plugin, stats);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);

        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getPlayer()).thenReturn(player);
        when(place.getBlock()).thenReturn(block);
        listener.onBlockPlace(place);
        verify(stats).addBlocksPlaced(player.getUniqueId());

        BlockBreakEvent breakEvent = mock(BlockBreakEvent.class);
        when(breakEvent.getPlayer()).thenReturn(player);
        listener.onBlockBreak(breakEvent);
        verify(stats).addBlocksBroken(player.getUniqueId());
    }

    @Test
    void ignoresBlocksWhenTrackingDisabled() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackBlocks()).thenReturn(false);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        BlockListener listener = new BlockListener(plugin, stats);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        Block block = mock(Block.class);
        BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);

        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getPlayer()).thenReturn(player);
        when(place.getBlock()).thenReturn(block);
        listener.onBlockPlace(place);
        verify(stats, never()).addBlocksPlaced(player.getUniqueId());
    }
}
