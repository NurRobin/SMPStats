package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {
    private final StatsService statsService;
    private final Settings settings;

    public CraftingListener(StatsService statsService, Settings settings) {
        this.statsService = statsService;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!settings.isTrackCrafting()) {
            return;
        }
        long crafted = estimateCraftAmount(event);
        if (crafted > 0) {
            statsService.addCrafted(event.getWhoClicked().getUniqueId(), crafted);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!settings.isTrackConsumption()) {
            return;
        }
        statsService.addConsumed(event.getPlayer().getUniqueId());
    }

    private long estimateCraftAmount(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result == null) {
            return 0;
        }
        int resultAmount = result.getAmount();
        if (event.isShiftClick()) {
            return (long) resultAmount * calculateMaxCrafts(event.getInventory());
        }
        return resultAmount;
    }

    private int calculateMaxCrafts(CraftingInventory inventory) {
        int maxCrafts = Integer.MAX_VALUE;
        for (ItemStack stack : inventory.getMatrix()) {
            if (stack == null || stack.getAmount() == 0) {
                continue;
            }
            maxCrafts = Math.min(maxCrafts, stack.getAmount());
        }
        return maxCrafts == Integer.MAX_VALUE ? 0 : maxCrafts;
    }
}
