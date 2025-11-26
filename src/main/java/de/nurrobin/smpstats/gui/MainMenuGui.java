package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.health.ServerHealthService;
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
import java.util.List;

import static de.nurrobin.smpstats.gui.GuiUtils.createGuiItem;

public class MainMenuGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final Inventory inventory;
    private final StatsService statsService;
    private final ServerHealthService healthService;

    public MainMenuGui(SMPStats plugin, GuiManager guiManager, StatsService statsService, ServerHealthService healthService) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.healthService = healthService;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("SMPStats Menu", NamedTextColor.DARK_BLUE));
        initializeItems();
    }

    private void initializeItems() {
        // My Stats
        inventory.setItem(11, createGuiItem(Material.PLAYER_HEAD, Component.text("My Stats", NamedTextColor.GOLD), 
                Component.text("View your personal statistics", NamedTextColor.GRAY)));

        // Server Health
        inventory.setItem(13, createGuiItem(Material.REDSTONE_BLOCK, Component.text("Server Health", NamedTextColor.RED), 
                Component.text("View server performance metrics", NamedTextColor.GRAY),
                Component.text("Requires Permission", NamedTextColor.DARK_RED)));

        // Leaderboards (Placeholder for now)
        inventory.setItem(15, createGuiItem(Material.GOLD_INGOT, Component.text("Leaderboards", NamedTextColor.YELLOW), 
                Component.text("View top players", NamedTextColor.GRAY),
                Component.text("Coming Soon", NamedTextColor.DARK_GRAY)));
        
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
        Player player = (Player) event.getWhoClicked();
        if (event.getSlot() == 11) {
            // Open My Stats
            guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, player));
        } else if (event.getSlot() == 13) {
            // Open Server Health
            if (!player.hasPermission("smpstats.health")) {
                player.sendMessage(Component.text("You do not have permission to view server health.", NamedTextColor.RED));
                return;
            }
             guiManager.openGui(player, new ServerHealthGui(plugin, guiManager, healthService));
        }
    }
}
