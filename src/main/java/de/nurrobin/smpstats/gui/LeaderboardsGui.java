package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
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

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI for displaying player leaderboards sorted by various statistics.
 */
public class LeaderboardsGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final ServerHealthService healthService;
    private final Inventory inventory;
    private final LeaderboardType currentType;
    private final int page;
    private static final int PLAYERS_PER_PAGE = 10;

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
                Component.text("Leaderboard: " + type.getDisplayName(), NamedTextColor.GOLD));
        initializeItems();
    }

    private void initializeItems() {
        // Category selection row (top row)
        int categorySlot = 0;
        for (LeaderboardType type : LeaderboardType.values()) {
            boolean isSelected = type == currentType;
            Material material = isSelected ? Material.ENCHANTED_BOOK : type.getIcon();
            inventory.setItem(categorySlot, createGuiItem(material,
                    Component.text(type.getDisplayName(), isSelected ? NamedTextColor.GREEN : type.getColor()),
                    isSelected ? Component.text("Currently viewing", NamedTextColor.GRAY) 
                               : Component.text("Click to view", NamedTextColor.DARK_GRAY)));
            categorySlot++;
        }

        // Get sorted player list
        List<StatsRecord> allStats = statsService.getAllStats();
        allStats.sort(Comparator.comparing(currentType::getValue).reversed());
        
        int totalPages = (int) Math.ceil(allStats.size() / (double) PLAYERS_PER_PAGE);
        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, allStats.size());

        // Display players (slots 18-27 for top 10)
        int displaySlot = 18;
        for (int i = startIndex; i < endIndex && displaySlot < 28; i++) {
            StatsRecord record = allStats.get(i);
            int rank = i + 1;
            Material headMaterial = getRankMaterial(rank);
            
            inventory.setItem(displaySlot, createGuiItem(headMaterial,
                    Component.text("#" + rank + " ", getRankColor(rank))
                            .append(Component.text(record.getName(), NamedTextColor.WHITE)),
                    Component.text(currentType.getDisplayName() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(currentType.formatValue(record), currentType.getColor()))));
            displaySlot++;
        }

        // Navigation row (bottom)
        // Previous page
        if (page > 0) {
            inventory.setItem(45, createGuiItem(Material.ARROW, 
                    Component.text("Previous Page", NamedTextColor.YELLOW),
                    Component.text("Page " + page + "/" + totalPages, NamedTextColor.GRAY)));
        }

        // Page indicator
        inventory.setItem(49, createGuiItem(Material.PAPER,
                Component.text("Page " + (page + 1) + "/" + Math.max(1, totalPages), NamedTextColor.WHITE)));

        // Next page
        if (page < totalPages - 1) {
            inventory.setItem(53, createGuiItem(Material.ARROW,
                    Component.text("Next Page", NamedTextColor.YELLOW),
                    Component.text("Page " + (page + 2) + "/" + totalPages, NamedTextColor.GRAY)));
        }

        // Back button
        inventory.setItem(48, createGuiItem(Material.BARRIER, Component.text("Back to Menu", NamedTextColor.RED)));

        // Refresh button
        inventory.setItem(50, createGuiItem(Material.SUNFLOWER, Component.text("Refresh", NamedTextColor.GREEN)));

        // Fill background
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private Material getRankMaterial(int rank) {
        return switch (rank) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.PLAYER_HEAD;
        };
    }

    private NamedTextColor getRankColor(int rank) {
        return switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
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

        // Category selection (slots 0-6)
        if (slot >= 0 && slot < LeaderboardType.values().length) {
            LeaderboardType selectedType = LeaderboardType.values()[slot];
            if (selectedType != currentType) {
                guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, selectedType, 0));
            }
            return;
        }

        // Previous page
        if (slot == 45 && page > 0) {
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, currentType, page - 1));
            return;
        }

        // Next page
        List<StatsRecord> allStats = statsService.getAllStats();
        int totalPages = (int) Math.ceil(allStats.size() / (double) PLAYERS_PER_PAGE);
        if (slot == 53 && page < totalPages - 1) {
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, currentType, page + 1));
            return;
        }

        // Back to menu
        if (slot == 48) {
            guiManager.openGui(player, new MainMenuGui(plugin, guiManager, statsService, healthService));
            return;
        }

        // Refresh
        if (slot == 50) {
            playSuccessSound(player);
            guiManager.openGui(player, new LeaderboardsGui(plugin, guiManager, statsService, healthService, currentType, page));
        }
    }
}
