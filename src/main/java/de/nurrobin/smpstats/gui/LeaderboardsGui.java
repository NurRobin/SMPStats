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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI for displaying player leaderboards sorted by various statistics.
 * Features player skulls with rank indicators and pagination support for top 50 players.
 */
public class LeaderboardsGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final ServerHealthService healthService;
    private final Inventory inventory;
    private final LeaderboardType currentType;
    private final int page;
    
    /** Maximum players shown per page (reduced to 14 due to larger category row) */
    private static final int PLAYERS_PER_PAGE = 14;
    /** Maximum rank to display (top 50) */
    private static final int MAX_RANK = 50;
    /** First slot for player entries (row 2, starting at slot 10) */
    private static final int FIRST_PLAYER_SLOT = 10;
    /** Slot for "Find My Rank" button */
    private static final int FIND_MY_RANK_SLOT = 46;

    /**
     * Available leaderboard sorting types.
     */
    public enum LeaderboardType {
        PLAYTIME("Playtime", Material.CLOCK, NamedTextColor.GOLD, 
                r -> (double) r.getPlaytimeMillis(), r -> formatPlaytime(r.getPlaytimeMillis())),
        KILLS("Total Kills", Material.DIAMOND_SWORD, NamedTextColor.RED,
                r -> (double) (r.getPlayerKills() + r.getMobKills()), r -> String.valueOf(r.getPlayerKills() + r.getMobKills())),
        PLAYER_KILLS("Player Kills", Material.IRON_SWORD, NamedTextColor.DARK_RED,
                r -> (double) r.getPlayerKills(), r -> String.valueOf(r.getPlayerKills())),
        DEATHS("Deaths", Material.SKELETON_SKULL, NamedTextColor.GRAY,
                r -> (double) r.getDeaths(), r -> String.valueOf(r.getDeaths())),
        BLOCKS_BROKEN("Blocks Broken", Material.IRON_PICKAXE, NamedTextColor.AQUA,
                r -> (double) r.getBlocksBroken(), r -> String.valueOf(r.getBlocksBroken())),
        BLOCKS_PLACED("Blocks Placed", Material.BRICKS, NamedTextColor.GREEN,
                r -> (double) r.getBlocksPlaced(), r -> String.valueOf(r.getBlocksPlaced())),
        DISTANCE("Distance Traveled", Material.LEATHER_BOOTS, NamedTextColor.LIGHT_PURPLE,
                r -> r.getDistanceOverworld() + r.getDistanceNether() + r.getDistanceEnd(), 
                r -> formatDistance(r.getDistanceOverworld() + r.getDistanceNether() + r.getDistanceEnd()));

        private final String displayName;
        private final Material icon;
        private final NamedTextColor color;
        private final Function<StatsRecord, Double> valueExtractor;
        private final Function<StatsRecord, String> valueFormatter;

        LeaderboardType(String displayName, Material icon, NamedTextColor color,
                        Function<StatsRecord, Double> valueExtractor,
                        Function<StatsRecord, String> valueFormatter) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
            this.valueExtractor = valueExtractor;
            this.valueFormatter = valueFormatter;
        }

        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
        public NamedTextColor getColor() { return color; }
        public Double getValue(StatsRecord record) { return valueExtractor.apply(record); }
        public String formatValue(StatsRecord record) { return valueFormatter.apply(record); }
    }

    public LeaderboardsGui(SMPStats plugin, GuiManager guiManager, StatsService statsService, 
                           ServerHealthService healthService, LeaderboardType type, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.healthService = healthService;
        this.currentType = type;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54, 
                Component.text("🏆 ", NamedTextColor.GOLD)
                        .append(Component.text(type.getDisplayName() + " Leaderboard", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        // Fill background first
        fillBackground();
        
        // Category selection row (top row, slots 1-7)
        initializeCategoryButtons();

        // Get sorted player list (limited to top MAX_RANK)
        List<StatsRecord> allStats = statsService.getAllStats();
        allStats.sort(Comparator.comparing(currentType::getValue).reversed());
        
        // Limit to top MAX_RANK players
        int totalPlayers = Math.min(allStats.size(), MAX_RANK);
        int totalPages = (int) Math.ceil(totalPlayers / (double) PLAYERS_PER_PAGE);
        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, totalPlayers);

        // Display players in a 7x3 grid layout (slots 10-16, 19-25, 28-34)
        displayPlayers(allStats, startIndex, endIndex);

        // Info item showing current category stats
        addInfoPanel(allStats, totalPlayers);

        // Navigation row (bottom)
        addNavigationButtons(totalPages, totalPlayers);
    }

    private void fillBackground() {
        ItemStack filler = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack topBorder = createBorderItem(Material.BLACK_STAINED_GLASS_PANE);
        
        for (int i = 0; i < inventory.getSize(); i++) {
            // Top row uses black glass for contrast
            if (i < 9) {
                inventory.setItem(i, topBorder);
            } else {
                inventory.setItem(i, filler);
            }
        }
    }

    private void initializeCategoryButtons() {
        // Use row 2 (slots 10-16) for larger, more visible category buttons
        int[] categorySlots = {10, 11, 12, 13, 14, 15, 16};
        
        for (int i = 0; i < LeaderboardType.values().length && i < categorySlots.length; i++) {
            LeaderboardType type = LeaderboardType.values()[i];
            boolean isSelected = type == currentType;
            Material material = type.getIcon();
            
            ItemStack item;
            if (isSelected) {
                // Selected: Use glowing enchanted version
                item = createGuiItem(Material.ENCHANTED_BOOK,
                        Component.text("⭐ " + type.getDisplayName(), NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD),
                        Component.text("▶ Currently viewing", NamedTextColor.GREEN),
                        Component.empty(),
                        Component.text("Top players by this stat", NamedTextColor.GRAY));
            } else {
                // Unselected: Show category icon
                item = createGuiItem(material,
                        Component.text(type.getDisplayName(), type.getColor()).decorate(TextDecoration.BOLD),
                        Component.empty(),
                        Component.text("▶ Click to switch", NamedTextColor.DARK_GRAY));
            }
            inventory.setItem(categorySlots[i], item);
        }
        
        // Add category header in top row
        inventory.setItem(4, createGuiItem(Material.WRITABLE_BOOK,
                Component.text("🏆 Categories", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Choose a leaderboard type", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Current: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(currentType.getDisplayName(), currentType.getColor()))));
    }

    private void displayPlayers(List<StatsRecord> allStats, int startIndex, int endIndex) {
        // Grid slots for 7x2 layout (reduced to fit new category row)
        int[] playerSlots = {
            19, 20, 21, 22, 23, 24, 25,  // Row 3
            28, 29, 30, 31, 32, 33, 34   // Row 4
        };

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < playerSlots.length; i++) {
            StatsRecord record = allStats.get(i);
            int rank = i + 1;
            
            // Create player head with rank as item amount
            ItemStack playerItem = createRankedPlayerHeadByUuid(
                    record.getUuid(),
                    record.getName(),
                    rank,
                    currentType.getDisplayName() + ": " + currentType.formatValue(record),
                    currentType.getColor()
            );
            
            inventory.setItem(playerSlots[slotIndex], playerItem);
            slotIndex++;
        }
    }

    private void addInfoPanel(List<StatsRecord> allStats, int totalPlayers) {
        // Stats summary in slot 8 (top right)
        String[] infoLines;
        if (allStats.isEmpty()) {
            infoLines = new String[]{"No player data yet", "Start playing to see stats!"};
        } else {
            // Calculate some interesting stats
            double total = allStats.stream()
                    .limit(MAX_RANK)
                    .mapToDouble(currentType::getValue)
                    .sum();
            double average = allStats.isEmpty() ? 0 : total / Math.min(allStats.size(), MAX_RANK);
            
            infoLines = new String[]{
                "Showing top " + totalPlayers + " players",
                "Page " + (page + 1) + " of " + Math.max(1, (int) Math.ceil(totalPlayers / (double) PLAYERS_PER_PAGE)),
                "",
                "Hover over heads to see stats",
                "Item count = Player rank"
            };
        }
        inventory.setItem(8, createInfoItem("ℹ Leaderboard Info", infoLines));
    }

    private void addNavigationButtons(int totalPages, int totalPlayers) {
        // Previous page (slot 45)
        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, 
                    Component.text("◀ Previous Page", NamedTextColor.YELLOW),
                    Component.text("Go to page " + page, NamedTextColor.GRAY)));
        } else {
            inventory.setItem(45, createBorderItem(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Find My Rank button (slot 46)
        inventory.setItem(FIND_MY_RANK_SLOT, createGuiItem(Material.ENDER_EYE, 
                Component.text("🔍 Find My Rank", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                Component.text("Jump to your position", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Click to find yourself", NamedTextColor.DARK_GRAY)));

        // Back button (slot 48)
        inventory.setItem(48, createGuiItem(Material.BARRIER, 
                Component.text("✖ Back to Menu", NamedTextColor.RED),
                Component.text("Return to main menu", NamedTextColor.GRAY)));

        // Page indicator (slot 49)
        int displayPage = page + 1;
        int displayTotalPages = Math.max(1, totalPages);
        inventory.setItem(49, createGuiItem(Material.PAPER,
                Component.text("Page " + displayPage + "/" + displayTotalPages, NamedTextColor.WHITE),
                Component.text("Showing ranks " + (page * PLAYERS_PER_PAGE + 1) + "-" + 
                        Math.min((page + 1) * PLAYERS_PER_PAGE, totalPlayers), NamedTextColor.GRAY)));

        // Refresh button (slot 50)
        inventory.setItem(50, createGuiItem(Material.SUNFLOWER, 
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Reload leaderboard data", NamedTextColor.GRAY)));

        // Next page (slot 53)
        if (page < totalPages - 1) {
            inventory.setItem(53, createGuiItem(Material.ARROW,
                    Component.text("Next Page ▶", NamedTextColor.YELLOW),
                    Component.text("Go to page " + (page + 2), NamedTextColor.GRAY)));
        } else {
            inventory.setItem(53, createBorderItem(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    /**
     * Finds the rank and page for a given player UUID.
     * @param playerUuid The UUID of the player to find
     * @return An array containing [rank, page] or null if not found in top MAX_RANK
     */
    private int[] findPlayerRank(UUID playerUuid) {
        List<StatsRecord> allStats = statsService.getAllStats();
        allStats.sort(Comparator.comparing(currentType::getValue).reversed());
        
        for (int i = 0; i < Math.min(allStats.size(), MAX_RANK); i++) {
            if (allStats.get(i).getUuid().equals(playerUuid)) {
                int rank = i + 1;
                int targetPage = i / PLAYERS_PER_PAGE;
                return new int[]{rank, targetPage};
            }
        }
        return null; // Not in top MAX_RANK
    }

    private static String formatPlaytime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) {
            return hours + "h";
        }
        long days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }

    private static String formatDistance(double meters) {
        if (meters < 1000) {
            return (int) meters + "m";
        }
        return String.format("%.1fkm", meters / 1000);
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
        int slot = event.getSlot();
        playClickSound(player);

        // Category selection (slots 10-16 in row 2)
        if (slot >= 10 && slot <= 16) {
            int categoryIndex = slot - 10;
            if (categoryIndex < LeaderboardType.values().length) {
                LeaderboardType selectedType = LeaderboardType.values()[categoryIndex];
                if (selectedType != currentType) {
                    guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, selectedType, 0));
                }
            }
            return;
        }

        // Previous page (slot 45)
        if (slot == 45 && page > 0) {
            playPageTurnSound(player);
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, currentType, page - 1));
            return;
        }

        // Find My Rank (slot 46)
        if (slot == FIND_MY_RANK_SLOT) {
            int[] result = findPlayerRank(player.getUniqueId());
            if (result != null) {
                int rank = result[0];
                int targetPage = result[1];
                playSuccessSound(player);
                player.sendMessage(Component.text("You are ranked #" + rank + " in " + currentType.getDisplayName() + "!", NamedTextColor.GREEN));
                if (targetPage != page) {
                    guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, currentType, targetPage));
                }
            } else {
                playErrorSound(player);
                player.sendMessage(Component.text("You are not in the top " + MAX_RANK + " for " + currentType.getDisplayName() + ".", NamedTextColor.YELLOW));
            }
            return;
        }

        // Next page (slot 53)
        List<StatsRecord> allStats = statsService.getAllStats();
        int totalPlayers = Math.min(allStats.size(), MAX_RANK);
        int totalPages = (int) Math.ceil(totalPlayers / (double) PLAYERS_PER_PAGE);
        if (slot == 53 && page < totalPages - 1) {
            playPageTurnSound(player);
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, currentType, page + 1));
            return;
        }

        // Back to menu (slot 48)
        if (slot == 48) {
            guiManager.openGui(player, new MainMenuGui(plugin, guiManager, statsService, healthService));
            return;
        }

        // Refresh (slot 50)
        if (slot == 50) {
            playSuccessSound(player);
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, currentType, page));
        }
    }
}
