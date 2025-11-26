package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.heatmap.HeatmapService;
import org.bukkit.Location;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeatmapListenerTest {

    @Test
    void forwardsEventsToService() {
        HeatmapService service = mock(HeatmapService.class);
        HeatmapListener listener = new HeatmapListener(service);

        BlockBreakEvent breakEvent = mock(BlockBreakEvent.class);
        Location breakLoc = mock(Location.class);
        when(breakEvent.getBlock()).thenReturn(mock(org.bukkit.block.Block.class));
        when(breakEvent.getBlock().getLocation()).thenReturn(breakLoc);
        listener.onBlockBreak(breakEvent);
        verify(service).track("MINING", breakLoc);

        PlayerDeathEvent deathEvent = mock(PlayerDeathEvent.class);
        Location deathLoc = mock(Location.class);
        when(deathEvent.getEntity()).thenReturn(mock(org.bukkit.entity.Player.class));
        when(deathEvent.getEntity().getLocation()).thenReturn(deathLoc);
        listener.onDeath(deathEvent);
        verify(service).track("DEATH", deathLoc);
    }
}
