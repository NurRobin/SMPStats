package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CraftingListener implements Listener {
    private final SMPStats plugin;
    private final StatsService statsService;

    public CraftingListener(SMPStats plugin, StatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!plugin.getSettings().isTrackCrafting()) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result == null) {
            return;
        }
        HumanEntity crafter = event.getWhoClicked();
        if (!event.isShiftClick()) {
            statsService.addCrafted(crafter.getUniqueId(), result.getAmount());
            return;
        }

        long maxCrafted = (long) result.getAmount() * calculateMaxCrafts(event.getInventory());
        if (maxCrafted <= 0) {
            return;
        }
        // Shift-click stops crafting once the inventory is full, so count what actually
        // arrived in the inventory after the click has been processed.
        ItemStack actualResult = event.getInventory().getResult();
        ItemStack matchItem = actualResult != null ? actualResult : result;
        long before = countSimilar(crafter.getInventory(), matchItem);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            long gained = countSimilar(crafter.getInventory(), matchItem) - before;
            long crafted = Math.min(gained, maxCrafted);
            if (crafted > 0) {
                statsService.addCrafted(crafter.getUniqueId(), crafted);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!plugin.getSettings().isTrackConsumption()) {
            return;
        }
        statsService.addConsumed(event.getPlayer().getUniqueId());
    }

    private long countSimilar(Inventory inventory, ItemStack match) {
        long total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(match)) {
                total += stack.getAmount();
            }
        }
        return total;
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
