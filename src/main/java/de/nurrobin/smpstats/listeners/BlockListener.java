package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener implements Listener {
    private final SMPStats plugin;
    private final StatsService statsService;

    public BlockListener(SMPStats plugin, StatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getSettings().isTrackBlocks()) {
            return;
        }
        statsService.addBlocksPlaced(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getSettings().isTrackBlocks()) {
            return;
        }
        statsService.addBlocksBroken(event.getPlayer().getUniqueId());
    }
}
