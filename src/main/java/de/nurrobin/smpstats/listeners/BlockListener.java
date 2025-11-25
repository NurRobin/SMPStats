package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener implements Listener {
    private final StatsService statsService;
    private final Settings settings;

    public BlockListener(StatsService statsService, Settings settings) {
        this.statsService = statsService;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!settings.isTrackBlocks()) {
            return;
        }
        statsService.addBlocksPlaced(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!settings.isTrackBlocks()) {
            return;
        }
        statsService.addBlocksBroken(event.getPlayer().getUniqueId());
    }
}
