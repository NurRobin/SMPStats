package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.heatmap.HeatmapService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class HeatmapListener implements Listener {
    private final HeatmapService heatmapService;

    public HeatmapListener(HeatmapService heatmapService) {
        this.heatmapService = heatmapService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        heatmapService.track("MINING", event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        heatmapService.track("DEATH", event.getEntity().getLocation());
    }
}
