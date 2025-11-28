package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI for displaying timeline delta comparison between two time periods.
 * Shows "This week vs last week" style stat changes with visual indicators.
 */
public class TimelineDeltaGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final StatsStorage storage;
    private final Player viewer;
    private final UUID targetPlayerUuid;
    private final Inventory inventory;
    
    /** Days for "this week" period */
    private static final int THIS_WEEK_DAYS = 7;
    /** Days for "last week" period (7-14 days ago) */
    private static final int LAST_WEEK_DAYS = 14;
    
    // Layout slots
    private static final int BACK_SLOT = 49;
    private static final int REFRESH_SLOT = 53;
    private static final int THIS_WEEK_HEADER_SLOT = 2;
    private static final int VS_SLOT = 4;
    private static final int LAST_WEEK_HEADER_SLOT = 6;

    public TimelineDeltaGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                            StatsStorage storage, Player viewer, UUID targetPlayerUuid) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.storage = storage;
        this.viewer = viewer;
        this.targetPlayerUuid = targetPlayerUuid;
        
        String targetName = statsService.getStats(targetPlayerUuid)
                .map(r -> r.getName())
                .orElse("Player");
        
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("📈 ", NamedTextColor.GOLD)
                        .append(Component.text(targetName + "'s Progress", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        fillBackground();
        
        // Load timeline data
        Map<String, Object> thisWeek;
        Map<String, Object> lastWeek;
        try {
            thisWeek = storage.loadTimelineRange(targetPlayerUuid, THIS_WEEK_DAYS);
            lastWeek = loadLastWeekData();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load timeline data: " + e.getMessage());
            inventory.setItem(22, createGuiItem(Material.BARRIER,
                    Component.text("Could not load timeline data", NamedTextColor.RED),
                    Component.text("Please try again later", NamedTextColor.GRAY)));
            addNavigationButtons();
            return;
        }
        
        // Headers
        inventory.setItem(THIS_WEEK_HEADER_SLOT, createGuiItem(Material.LIME_BANNER,
                Component.text("This Week", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Component.text("Last 7 days activity", NamedTextColor.GRAY)));
        
        inventory.setItem(VS_SLOT, createGuiItem(Material.COMPARATOR,
                Component.text("VS", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Compare your progress", NamedTextColor.GRAY)));
        
        inventory.setItem(LAST_WEEK_HEADER_SLOT, createGuiItem(Material.YELLOW_BANNER,
                Component.text("Last Week", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                Component.text("Previous 7 days", NamedTextColor.GRAY)));
        
        if (thisWeek.isEmpty() && lastWeek.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.CLOCK,
                    Component.text("No historical data yet", NamedTextColor.YELLOW),
                    Component.text("Keep playing to build up", NamedTextColor.GRAY),
                    Component.text("timeline history!", NamedTextColor.GRAY)));
            addNavigationButtons();
            return;
        }
        
        // Row 2: Playtime comparison
        addComparisonRow(10, Material.CLOCK, "⏱ Playtime",
                getPlaytime(thisWeek), getPlaytime(lastWeek),
                this::formatPlaytime);
        
        // Row 2: Kills comparison
        addComparisonRow(12, Material.DIAMOND_SWORD, "⚔ Total Kills",
                getKills(thisWeek), getKills(lastWeek),
                this::formatNumber);
        
        // Row 2: Deaths comparison
        addComparisonRow(14, Material.SKELETON_SKULL, "💀 Deaths",
                getDeaths(thisWeek), getDeaths(lastWeek),
                this::formatNumber, true); // Lower is better for deaths
        
        // Row 2: Blocks comparison
        addComparisonRow(16, Material.GRASS_BLOCK, "🧱 Blocks",
                getBlocks(thisWeek), getBlocks(lastWeek),
                this::formatNumber);
        
        // Row 3: Distance comparison
        addComparisonRow(19, Material.LEATHER_BOOTS, "🏃 Distance",
                getDistance(thisWeek), getDistance(lastWeek),
                this::formatDistance);
        
        // Row 3: Items crafted
        addComparisonRow(21, Material.CRAFTING_TABLE, "🔨 Crafted",
                getCrafted(thisWeek), getCrafted(lastWeek),
                this::formatNumber);
        
        // Row 3: Damage dealt
        addComparisonRow(23, Material.IRON_SWORD, "💥 Damage Dealt",
                getDamageDealt(thisWeek), getDamageDealt(lastWeek),
                this::formatNumber);
        
        // Row 3: Items consumed
        addComparisonRow(25, Material.COOKED_BEEF, "🍖 Consumed",
                getConsumed(thisWeek), getConsumed(lastWeek),
                this::formatNumber);
        
        // Summary row
        addSummaryPanel(thisWeek, lastWeek);
        
        addNavigationButtons();
    }
    
    /**
     * Loads the data for "last week" (7-14 days ago).
     * This is calculated by getting the 14-day range and subtracting the 7-day range.
     */
    private Map<String, Object> loadLastWeekData() throws SQLException {
        Map<String, Object> twoWeeks = storage.loadTimelineRange(targetPlayerUuid, LAST_WEEK_DAYS);
        Map<String, Object> thisWeek = storage.loadTimelineRange(targetPlayerUuid, THIS_WEEK_DAYS);
        
        if (twoWeeks.isEmpty()) {
            return Map.of();
        }
        
        // Calculate last week by subtracting this week from the 2-week total
        java.util.LinkedHashMap<String, Object> lastWeek = new java.util.LinkedHashMap<>();
        lastWeek.put("playtime_ms", getLong(twoWeeks, "playtime_ms") - getLong(thisWeek, "playtime_ms"));
        lastWeek.put("blocks_broken", getLong(twoWeeks, "blocks_broken") - getLong(thisWeek, "blocks_broken"));
        lastWeek.put("blocks_placed", getLong(twoWeeks, "blocks_placed") - getLong(thisWeek, "blocks_placed"));
        lastWeek.put("player_kills", getLong(twoWeeks, "player_kills") - getLong(thisWeek, "player_kills"));
        lastWeek.put("mob_kills", getLong(twoWeeks, "mob_kills") - getLong(thisWeek, "mob_kills"));
        lastWeek.put("deaths", getLong(twoWeeks, "deaths") - getLong(thisWeek, "deaths"));
        lastWeek.put("distance_overworld", getDouble(twoWeeks, "distance_overworld") - getDouble(thisWeek, "distance_overworld"));
        lastWeek.put("distance_nether", getDouble(twoWeeks, "distance_nether") - getDouble(thisWeek, "distance_nether"));
        lastWeek.put("distance_end", getDouble(twoWeeks, "distance_end") - getDouble(thisWeek, "distance_end"));
        lastWeek.put("damage_dealt", getDouble(twoWeeks, "damage_dealt") - getDouble(thisWeek, "damage_dealt"));
        lastWeek.put("damage_taken", getDouble(twoWeeks, "damage_taken") - getDouble(thisWeek, "damage_taken"));
        lastWeek.put("items_crafted", getLong(twoWeeks, "items_crafted") - getLong(thisWeek, "items_crafted"));
        lastWeek.put("items_consumed", getLong(twoWeeks, "items_consumed") - getLong(thisWeek, "items_consumed"));
        
        return lastWeek;
    }
    
    private void addComparisonRow(int centerSlot, Material icon, String statName,
                                   double thisWeekValue, double lastWeekValue,
                                   java.util.function.Function<Double, String> formatter) {
        addComparisonRow(centerSlot, icon, statName, thisWeekValue, lastWeekValue, formatter, false);
    }
    
    private void addComparisonRow(int centerSlot, Material icon, String statName,
                                   double thisWeekValue, double lastWeekValue,
                                   java.util.function.Function<Double, String> formatter,
                                   boolean lowerIsBetter) {
        int leftSlot = centerSlot - 1;
        int rightSlot = centerSlot + 1;
        
        // Calculate change
        double change = thisWeekValue - lastWeekValue;
        double changePercent = lastWeekValue > 0 ? (change / lastWeekValue) * 100 : (thisWeekValue > 0 ? 100 : 0);
        
        // Determine colors based on improvement
        boolean improved = lowerIsBetter ? (change < 0) : (change > 0);
        boolean declined = lowerIsBetter ? (change > 0) : (change < 0);
        
        NamedTextColor thisWeekColor = improved ? NamedTextColor.GREEN : (declined ? NamedTextColor.RED : NamedTextColor.YELLOW);
        NamedTextColor lastWeekColor = NamedTextColor.GRAY;
        
        // Change indicator
        String changeIndicator = "";
        if (change > 0) {
            changeIndicator = " ↑";
        } else if (change < 0) {
            changeIndicator = " ↓";
        }
        
        // This week value (left of center icon)
        List<Component> thisWeekLore = new ArrayList<>();
        thisWeekLore.add(Component.text("This week", NamedTextColor.GRAY));
        if (change != 0) {
            String percentStr = String.format("%+.1f%%", changePercent);
            NamedTextColor changeColor = improved ? NamedTextColor.GREEN : NamedTextColor.RED;
            thisWeekLore.add(Component.empty());
            thisWeekLore.add(Component.text("Change: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(percentStr, changeColor)));
        }
        
        inventory.setItem(leftSlot, createGuiItem(
                improved ? Material.LIME_STAINED_GLASS_PANE : (declined ? Material.RED_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE),
                Component.text(formatter.apply(thisWeekValue) + changeIndicator, thisWeekColor).decorate(TextDecoration.BOLD),
                thisWeekLore.toArray(new Component[0])));
        
        // Center icon
        inventory.setItem(centerSlot, createGuiItem(icon,
                Component.text(statName, NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
        
        // Last week value (right of center icon)
        inventory.setItem(rightSlot, createGuiItem(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(formatter.apply(lastWeekValue), lastWeekColor).decorate(TextDecoration.BOLD),
                Component.text("Last week", NamedTextColor.GRAY)));
    }
    
    private void addSummaryPanel(Map<String, Object> thisWeek, Map<String, Object> lastWeek) {
        // Count improvements vs declines
        int improvements = 0;
        int declines = 0;
        
        // Playtime
        if (getPlaytime(thisWeek) > getPlaytime(lastWeek)) improvements++; 
        else if (getPlaytime(thisWeek) < getPlaytime(lastWeek)) declines++;
        
        // Kills
        if (getKills(thisWeek) > getKills(lastWeek)) improvements++; 
        else if (getKills(thisWeek) < getKills(lastWeek)) declines++;
        
        // Deaths (lower is better)
        if (getDeaths(thisWeek) < getDeaths(lastWeek)) improvements++; 
        else if (getDeaths(thisWeek) > getDeaths(lastWeek)) declines++;
        
        // Blocks
        if (getBlocks(thisWeek) > getBlocks(lastWeek)) improvements++; 
        else if (getBlocks(thisWeek) < getBlocks(lastWeek)) declines++;
        
        // Distance
        if (getDistance(thisWeek) > getDistance(lastWeek)) improvements++; 
        else if (getDistance(thisWeek) < getDistance(lastWeek)) declines++;
        
        // Crafted
        if (getCrafted(thisWeek) > getCrafted(lastWeek)) improvements++; 
        else if (getCrafted(thisWeek) < getCrafted(lastWeek)) declines++;
        
        // Verdict
        String verdict;
        NamedTextColor verdictColor;
        Material verdictIcon;
        
        if (improvements > declines) {
            verdict = "📈 Great Progress!";
            verdictColor = NamedTextColor.GREEN;
            verdictIcon = Material.EMERALD;
        } else if (declines > improvements) {
            verdict = "📉 Less Active";
            verdictColor = NamedTextColor.YELLOW;
            verdictIcon = Material.GOLD_INGOT;
        } else {
            verdict = "📊 Steady Week";
            verdictColor = NamedTextColor.AQUA;
            verdictIcon = Material.DIAMOND;
        }
        
        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(Component.empty());
        summaryLore.add(Component.text("Categories improved: ", NamedTextColor.GRAY)
                .append(Component.text(improvements, NamedTextColor.GREEN)));
        summaryLore.add(Component.text("Categories declined: ", NamedTextColor.GRAY)
                .append(Component.text(declines, NamedTextColor.RED)));
        summaryLore.add(Component.empty());
        summaryLore.add(Component.text("Keep up the good work!", NamedTextColor.DARK_GRAY));
        
        inventory.setItem(40, createGuiItem(verdictIcon,
                Component.text(verdict, verdictColor).decorate(TextDecoration.BOLD),
                summaryLore.toArray(new Component[0])));
    }
    
    private void fillBackground() {
        ItemStack filler = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack topBorder = createBorderItem(Material.BLACK_STAINED_GLASS_PANE);
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i < 9) {
                inventory.setItem(i, topBorder);
            } else {
                inventory.setItem(i, filler);
            }
        }
    }
    
    private void addNavigationButtons() {
        inventory.setItem(BACK_SLOT, createGuiItem(Material.ARROW,
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to player stats", NamedTextColor.GRAY)));
        
        inventory.setItem(REFRESH_SLOT, createGuiItem(Material.SUNFLOWER,
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Reload timeline data", NamedTextColor.GRAY)));
    }
    
    // Helper methods for extracting data
    private long getLong(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) return 0;
        Object val = data.get(key);
        return val instanceof Number ? ((Number) val).longValue() : 0;
    }
    
    private double getDouble(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) return 0;
        Object val = data.get(key);
        return val instanceof Number ? ((Number) val).doubleValue() : 0;
    }
    
    private double getPlaytime(Map<String, Object> data) {
        return getLong(data, "playtime_ms");
    }
    
    private double getKills(Map<String, Object> data) {
        return getLong(data, "player_kills") + getLong(data, "mob_kills");
    }
    
    private double getDeaths(Map<String, Object> data) {
        return getLong(data, "deaths");
    }
    
    private double getBlocks(Map<String, Object> data) {
        return getLong(data, "blocks_broken") + getLong(data, "blocks_placed");
    }
    
    private double getDistance(Map<String, Object> data) {
        return getDouble(data, "distance_overworld") + getDouble(data, "distance_nether") + getDouble(data, "distance_end");
    }
    
    private double getCrafted(Map<String, Object> data) {
        return getLong(data, "items_crafted");
    }
    
    private double getConsumed(Map<String, Object> data) {
        return getLong(data, "items_consumed");
    }
    
    private double getDamageDealt(Map<String, Object> data) {
        return getDouble(data, "damage_dealt");
    }
    
    // Formatters
    private String formatPlaytime(Double millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis.longValue());
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis.longValue()) % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
    
    private String formatNumber(Double value) {
        long val = value.longValue();
        if (val >= 1_000_000) {
            return String.format("%.1fM", val / 1_000_000.0);
        } else if (val >= 1_000) {
            return String.format("%.1fK", val / 1_000.0);
        }
        return String.valueOf(val);
    }
    
    private String formatDistance(Double blocks) {
        if (blocks >= 1000) {
            return String.format("%.1fkm", blocks / 1000.0);
        }
        return String.format("%.0fm", blocks);
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        playClickSound(player);
        
        int slot = event.getSlot();
        
        if (slot == BACK_SLOT) {
            player.closeInventory();
        } else if (slot == REFRESH_SLOT) {
            playSuccessSound(player);
            initializeItems();
            player.sendMessage(Component.text("Timeline data refreshed!", NamedTextColor.GREEN));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
