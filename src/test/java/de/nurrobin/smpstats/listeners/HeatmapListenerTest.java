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
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(breakEvent.getBlock()).thenReturn(block);
        when(block.getLocation()).thenReturn(breakLoc);
        when(block.getType()).thenReturn(org.bukkit.Material.STONE);
        listener.onBlockBreak(breakEvent);
        verify(service).track("MINING", breakLoc);

        PlayerDeathEvent deathEvent = mock(PlayerDeathEvent.class);
        Location deathLoc = mock(Location.class);
        when(deathEvent.getEntity()).thenReturn(mock(org.bukkit.entity.Player.class));
        when(deathEvent.getEntity().getLocation()).thenReturn(deathLoc);
        listener.onDeath(deathEvent);
        verify(service).track("DEATH", deathLoc);
    }

    @Test
    void tracksOresAndDamage() {
        HeatmapService service = mock(HeatmapService.class);
        HeatmapListener listener = new HeatmapListener(service);

        // Test Ore Mining
        BlockBreakEvent oreEvent = mock(BlockBreakEvent.class);
        Location oreLoc = mock(Location.class);
        org.bukkit.block.Block oreBlock = mock(org.bukkit.block.Block.class);
        when(oreEvent.getBlock()).thenReturn(oreBlock);
        when(oreBlock.getLocation()).thenReturn(oreLoc);
        when(oreBlock.getType()).thenReturn(org.bukkit.Material.DIAMOND_ORE);

        listener.onBlockBreak(oreEvent);
        verify(service).track("MINING", oreLoc);
        verify(service).track("MINING_DIAMOND_ORE", oreLoc);

        // Test Ancient Debris
        when(oreBlock.getType()).thenReturn(org.bukkit.Material.ANCIENT_DEBRIS);
        listener.onBlockBreak(oreEvent);
        verify(service).track("MINING_ANCIENT_DEBRIS", oreLoc);

        // Test Damage (Player)
        org.bukkit.event.entity.EntityDamageEvent damageEvent = mock(org.bukkit.event.entity.EntityDamageEvent.class);
        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        Location playerLoc = mock(Location.class);
        when(damageEvent.getEntity()).thenReturn(player);
        when(player.getLocation()).thenReturn(playerLoc);

        listener.onDamage(damageEvent);
        verify(service).track("DAMAGE", playerLoc);

        // Test Damage (Non-Player)
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        when(damageEvent.getEntity()).thenReturn(entity);
        listener.onDamage(damageEvent);
        // Should not track again (verify count is still 1)
        verify(service).track("DAMAGE", playerLoc);
    }
}
