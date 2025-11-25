package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.moments.MomentService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class MomentListener implements Listener {
    private final MomentService momentService;

    public MomentListener(MomentService momentService) {
        this.momentService = momentService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDiamondBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            momentService.onDiamondFound(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        momentService.onFirstDeath(event.getEntity(), event.getEntity().getLocation());
        if (event.getEntity().getFallDistance() > 50) {
            momentService.onBigFallDeath(event.getEntity(), event.getEntity().getLocation(), event.getEntity().getFallDistance());
        }
    }
}
