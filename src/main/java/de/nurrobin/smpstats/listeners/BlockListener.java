package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

public class BlockListener implements Listener {
    private final SMPStats plugin;
    private final StatsService statsService;
    private final NamespacedKey ownerKey;

    public BlockListener(SMPStats plugin, StatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.ownerKey = new NamespacedKey(plugin, "owner");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getState() instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
            tileState.update();
        }

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
