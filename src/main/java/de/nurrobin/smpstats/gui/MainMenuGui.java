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

import static de.nurrobin.smpstats.gui.GuiUtils.*;

public class MainMenuGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final Inventory inventory;
    private final StatsService statsService;
    private final ServerHealthService healthService;

    /**
     * Creates a new MainMenuGui instance.
     * Note: The inventory is empty until {@link #open(Player)} is called,
     * which initializes items based on the player's permissions.
     */
    public MainMenuGui(SMPStats plugin, GuiManager guiManager, StatsService statsService, ServerHealthService healthService) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.healthService = healthService;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("SMPStats Menu", NamedTextColor.DARK_BLUE));
    }

    /**
     * Initializes the inventory items based on the player's permissions.
     * Called by {@link #open(Player)} before displaying the inventory.
     */
    private void initializeItems(Player player) {
        // My Stats - use player's head (requires smpstats.gui.stats)
        boolean canViewStats = player.hasPermission("smpstats.gui.stats");
        inventory.setItem(11, createPlayerHead(player, Component.text("My Stats", NamedTextColor.GOLD), 
                Component.text("View your personal statistics", NamedTextColor.GRAY),
                canViewStats 
                    ? Component.text("Click to view", NamedTextColor.GREEN)
                    : Component.text("Requires Permission", NamedTextColor.DARK_RED)));

        // Server Health (requires smpstats.gui.health or legacy smpstats.health)
        boolean canViewHealth = player.hasPermission("smpstats.gui.health") || player.hasPermission("smpstats.health");
        inventory.setItem(13, createGuiItem(Material.REDSTONE_BLOCK, Component.text("Server Health", NamedTextColor.RED), 
                Component.text("View server performance metrics", NamedTextColor.GRAY),
                canViewHealth 
                    ? Component.text("Click to view", NamedTextColor.GREEN)
                    : Component.text("Requires Permission", NamedTextColor.DARK_RED)));

        // Leaderboards (requires smpstats.gui.leaderboard)
        boolean canViewLeaderboard = player.hasPermission("smpstats.gui.leaderboard");
        inventory.setItem(15, createGuiItem(Material.GOLD_INGOT, Component.text("Leaderboards", NamedTextColor.YELLOW), 
                Component.text("View top players", NamedTextColor.GRAY),
                canViewLeaderboard 
                    ? Component.text("Click to view", NamedTextColor.GREEN)
                    : Component.text("Requires Permission", NamedTextColor.DARK_RED)));
        
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
        initializeItems(player);
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        playClickSound(player);
        
        if (event.getSlot() == 11) {
            // Open My Stats (requires smpstats.gui.stats)
            if (!player.hasPermission("smpstats.gui.stats")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You do not have permission to view stats.", NamedTextColor.RED));
                return;
            }
            guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, player));
        } else if (event.getSlot() == 13) {
            // Open Server Health (requires smpstats.gui.health or legacy smpstats.health)
            if (!player.hasPermission("smpstats.gui.health") && !player.hasPermission("smpstats.health")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You do not have permission to view server health.", NamedTextColor.RED));
                return;
            }
            guiManager.openGui(player, new ServerHealthGui(plugin, guiManager, healthService));
        } else if (event.getSlot() == 15) {
            // Open Leaderboards (requires smpstats.gui.leaderboard)
            if (!player.hasPermission("smpstats.gui.leaderboard")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You do not have permission to view leaderboards.", NamedTextColor.RED));
                return;
            }
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, 
                    LeaderboardsGui.LeaderboardType.PLAYTIME, 0));
        }
    }
}
