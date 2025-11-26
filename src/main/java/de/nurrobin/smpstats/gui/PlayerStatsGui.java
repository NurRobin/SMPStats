package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static de.nurrobin.smpstats.gui.GuiUtils.createGuiItem;

public class PlayerStatsGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final Player targetPlayer;
    private final Inventory inventory;

    public PlayerStatsGui(SMPStats plugin, GuiManager guiManager, StatsService statsService, Player targetPlayer) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.targetPlayer = targetPlayer;
        this.inventory = Bukkit.createInventory(this, 45, Component.text("Stats: " + targetPlayer.getName(), NamedTextColor.DARK_BLUE));
        initializeItems();
    }

    private void initializeItems() {
        Optional<StatsRecord> recordOpt = statsService.getStats(targetPlayer.getUniqueId());
        if (recordOpt.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, Component.text("No stats found", NamedTextColor.RED)));
            return;
        }
        StatsRecord record = recordOpt.get();

        // Playtime
        long hours = TimeUnit.MILLISECONDS.toHours(record.getPlaytimeMillis());
        inventory.setItem(10, createGuiItem(Material.CLOCK, Component.text("Playtime", NamedTextColor.GOLD),
                Component.text(hours + " hours", NamedTextColor.WHITE)));

        // Kills
        inventory.setItem(12, createGuiItem(Material.DIAMOND_SWORD, Component.text("Kills", NamedTextColor.RED),
                Component.text("Mobs: " + record.getMobKills(), NamedTextColor.WHITE),
                Component.text("Players: " + record.getPlayerKills(), NamedTextColor.WHITE)));

        // Deaths
        inventory.setItem(14, createGuiItem(Material.SKELETON_SKULL, Component.text("Deaths", NamedTextColor.DARK_RED),
                Component.text(String.valueOf(record.getDeaths()), NamedTextColor.WHITE),
                Component.text("Last Cause: " + (record.getLastDeathCause() != null ? record.getLastDeathCause() : "None"), NamedTextColor.GRAY)));

        // Blocks
        inventory.setItem(16, createGuiItem(Material.GRASS_BLOCK, Component.text("Blocks", NamedTextColor.GREEN),
                Component.text("Broken: " + record.getBlocksBroken(), NamedTextColor.WHITE),
                Component.text("Placed: " + record.getBlocksPlaced(), NamedTextColor.WHITE)));

        // Distance
        inventory.setItem(20, createGuiItem(Material.LEATHER_BOOTS, Component.text("Distance Travelled", NamedTextColor.AQUA),
                Component.text("Overworld: " + (int)record.getDistanceOverworld() + "m", NamedTextColor.WHITE),
                Component.text("Nether: " + (int)record.getDistanceNether() + "m", NamedTextColor.WHITE),
                Component.text("End: " + (int)record.getDistanceEnd() + "m", NamedTextColor.WHITE)));

        // Damage
        inventory.setItem(22, createGuiItem(Material.IRON_SWORD, Component.text("Combat", NamedTextColor.DARK_PURPLE),
                Component.text("Dealt: " + (int)record.getDamageDealt(), NamedTextColor.WHITE),
                Component.text("Taken: " + (int)record.getDamageTaken(), NamedTextColor.WHITE)));

        // Crafting
        inventory.setItem(24, createGuiItem(Material.CRAFTING_TABLE, Component.text("Items", NamedTextColor.YELLOW),
                Component.text("Crafted: " + record.getItemsCrafted(), NamedTextColor.WHITE),
                Component.text("Consumed: " + record.getItemsConsumed(), NamedTextColor.WHITE)));

        // Back Button
        inventory.setItem(40, createGuiItem(Material.ARROW, Component.text("Back", NamedTextColor.RED)));

        // Fill background
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == 40) {
            Player player = (Player) event.getWhoClicked();
            plugin.getServerHealthService().ifPresentOrElse(
                healthService -> guiManager.openGui(player, new MainMenuGui(plugin, guiManager, statsService, healthService)),
                () -> player.closeInventory()
            );
        }
    }
}
