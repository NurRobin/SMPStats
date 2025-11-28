package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * Main menu GUI for SMPStats plugin.
 * Provides access to player stats, server health, and leaderboards.
 */
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
        this.inventory = Bukkit.createInventory(this, 45, 
                Component.text("📊 ", NamedTextColor.GOLD)
                        .append(Component.text("SMPStats Menu", NamedTextColor.WHITE)));
    }

    /**
     * Initializes the inventory items based on the player's permissions.
     * Called by {@link #open(Player)} before displaying the inventory.
     */
    private void initializeItems(Player player) {
        inventory.clear();
        
        // Fill background with decorative pattern
        fillBackground();
        
        // Header info
        addHeaderInfo(player);
        
        // Main menu options
        boolean canViewStats = player.hasPermission("smpstats.gui.stats");
        boolean canViewHealth = player.hasPermission("smpstats.gui.health") || player.hasPermission("smpstats.health");
        boolean canViewLeaderboard = player.hasPermission("smpstats.gui.leaderboard");

        // My Stats - use player's head (slot 20)
        inventory.setItem(20, createPlayerHead(player, 
                Component.text("📈 My Stats", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("View your personal statistics", NamedTextColor.GRAY),
                Component.empty(),
                getQuickStatsSummary(player),
                Component.empty(),
                canViewStats 
                    ? Component.text("▶ Click to view details", NamedTextColor.GREEN)
                    : Component.text("✖ Requires Permission", NamedTextColor.DARK_RED)));

        // Server Health (slot 22)
        Material healthMaterial = canViewHealth ? Material.REDSTONE_BLOCK : Material.BEDROCK;
        inventory.setItem(22, createGuiItem(healthMaterial, 
                Component.text("💓 Server Health", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("View server performance metrics", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Monitor TPS, entities, and chunks", NamedTextColor.DARK_GRAY),
                Component.text("Identify performance issues", NamedTextColor.DARK_GRAY),
                Component.empty(),
                canViewHealth 
                    ? Component.text("▶ Click to view", NamedTextColor.GREEN)
                    : Component.text("✖ Requires Permission", NamedTextColor.DARK_RED)));

        // Leaderboards (slot 24)
        Material leaderboardMaterial = canViewLeaderboard ? Material.GOLD_INGOT : Material.COAL;
        inventory.setItem(24, createGuiItem(leaderboardMaterial, 
                Component.text("🏆 Leaderboards", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                Component.text("View top players", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Compare with other players", NamedTextColor.DARK_GRAY),
                Component.text("Multiple stat categories", NamedTextColor.DARK_GRAY),
                Component.empty(),
                canViewLeaderboard 
                    ? Component.text("▶ Click to view", NamedTextColor.GREEN)
                    : Component.text("✖ Requires Permission", NamedTextColor.DARK_RED)));

        // Admin Player Lookup (slot 31) - only visible to admins
        boolean isAdmin = player.hasPermission("smpstats.admin");
        if (isAdmin) {
            inventory.setItem(31, createGuiItem(Material.COMMAND_BLOCK,
                    Component.text("🔍 Player Lookup", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                    Component.text("Admin: Search any player", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("View stats of any player", NamedTextColor.DARK_GRAY),
                    Component.text("Search and sort players", NamedTextColor.DARK_GRAY),
                    Component.empty(),
                    Component.text("▶ Click to open", NamedTextColor.GREEN)));
        }

        // Quick tips at bottom
        addQuickTips();
        
        // Close button
        inventory.setItem(40, createGuiItem(Material.BARRIER,
                Component.text("✖ Close", NamedTextColor.RED),
                Component.text("Close this menu", NamedTextColor.GRAY)));
    }

    private void fillBackground() {
        ItemStack darkGlass = createBorderItem(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack grayGlass = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack lightGlass = createBorderItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        
        // Create a nice pattern
        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            
            // Top and bottom rows - dark
            if (row == 0 || row == 4) {
                inventory.setItem(i, darkGlass);
            }
            // Edge columns - gray
            else if (col == 0 || col == 8) {
                inventory.setItem(i, grayGlass);
            }
            // Inner area - light
            else {
                inventory.setItem(i, lightGlass);
            }
        }
    }

    private void addHeaderInfo(Player player) {
        // Plugin info (slot 4 - center top)
        inventory.setItem(4, createGuiItem(Material.NETHER_STAR,
                Component.text("SMPStats", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Server statistics & analytics", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Welcome, " + player.getName() + "!", NamedTextColor.WHITE)));
    }

    private Component getQuickStatsSummary(Player player) {
        Optional<StatsRecord> recordOpt = statsService.getStats(player.getUniqueId());
        if (recordOpt.isEmpty()) {
            return Component.text("No stats recorded yet", NamedTextColor.DARK_GRAY);
        }
        
        StatsRecord record = recordOpt.get();
        long hours = TimeUnit.MILLISECONDS.toHours(record.getPlaytimeMillis());
        long totalKills = record.getMobKills() + record.getPlayerKills();
        
        return Component.text("Playtime: " + hours + "h | Kills: " + totalKills + " | Deaths: " + record.getDeaths(), 
                NamedTextColor.AQUA);
    }

    private void addQuickTips() {
        // Tips in the bottom info row
        inventory.setItem(37, createGuiItem(Material.BOOK,
                Component.text("💡 Quick Tips", NamedTextColor.YELLOW),
                Component.text("• Use /stats for quick stats", NamedTextColor.GRAY),
                Component.text("• Stats update in real-time", NamedTextColor.GRAY),
                Component.text("• Check leaderboards daily!", NamedTextColor.GRAY)));
        
        inventory.setItem(43, createGuiItem(Material.COMPASS,
                Component.text("🌐 Web Dashboard", NamedTextColor.AQUA),
                Component.text("Access detailed analytics", NamedTextColor.GRAY),
                Component.text("at your server's web portal", NamedTextColor.GRAY)));
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
        
        if (event.getSlot() == 20) {
            // Open My Stats
            if (!player.hasPermission("smpstats.gui.stats")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You do not have permission to view stats.", NamedTextColor.RED));
                return;
            }
            guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, player));
        } else if (event.getSlot() == 22) {
            // Open Server Health
            if (!player.hasPermission("smpstats.gui.health") && !player.hasPermission("smpstats.health")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You do not have permission to view server health.", NamedTextColor.RED));
                return;
            }
            guiManager.openGui(player, new ServerHealthGui(plugin, guiManager, healthService));
        } else if (event.getSlot() == 24) {
            // Open Leaderboards
            if (!player.hasPermission("smpstats.gui.leaderboard")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You do not have permission to view leaderboards.", NamedTextColor.RED));
                return;
            }
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, 
                    LeaderboardsGui.LeaderboardType.PLAYTIME, 0));
        } else if (event.getSlot() == 31) {
            // Open Admin Player Lookup
            if (!player.hasPermission("smpstats.admin")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You do not have permission to use admin features.", NamedTextColor.RED));
                return;
            }
            plugin.getStatsStorage().ifPresentOrElse(
                    storage -> guiManager.openGui(player, new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, player)),
                    () -> {
                        playErrorSound(player);
                        player.sendMessage(Component.text("Storage is not available.", NamedTextColor.RED));
                    }
            );
        } else if (event.getSlot() == 40) {
            // Close menu
            player.closeInventory();
        }
    }
}
