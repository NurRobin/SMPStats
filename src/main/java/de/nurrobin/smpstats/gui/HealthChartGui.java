package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.health.HealthSnapshot;
import de.nurrobin.smpstats.health.HealthThresholds;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * Displays a primitive chart of health metrics over time using inventory slots.
 * The chart uses 9 columns (width) and 4 rows (height) for the graph area.
 * Each column shows a timestamp in the lore indicating when that data point was recorded.
 */
public class HealthChartGui implements InventoryGui, InventoryHolder {
    
    public enum MetricType {
        TPS("TPS", Material.CLOCK, NamedTextColor.GOLD, s -> s.tps(), 0, 20),
        MEMORY("Memory %", Material.ENDER_CHEST, NamedTextColor.AQUA, 
                s -> s.memoryMax() > 0 ? (s.memoryUsed() * 100.0 / s.memoryMax()) : 0, 0, 100),
        CHUNKS("Chunks", Material.GRASS_BLOCK, NamedTextColor.GREEN, s -> (double) s.chunks(), 0, -1),
        ENTITIES("Entities", Material.CREEPER_HEAD, NamedTextColor.RED, s -> (double) s.entities(), 0, -1),
        HOPPERS("Hoppers", Material.HOPPER, NamedTextColor.GRAY, s -> (double) s.hoppers(), 0, -1),
        REDSTONE("Redstone", Material.REDSTONE, NamedTextColor.DARK_RED, s -> (double) s.redstone(), 0, -1),
        COST_INDEX("Cost Index", Material.EMERALD, NamedTextColor.DARK_GREEN, s -> s.costIndex(), 0, 100);
        
        private final String displayName;
        private final Material icon;
        private final NamedTextColor color;
        private final Function<HealthSnapshot, Double> extractor;
        private final double minValue;
        private final double maxValue; // -1 means auto-scale
        
        MetricType(String displayName, Material icon, NamedTextColor color, 
                   Function<HealthSnapshot, Double> extractor, double minValue, double maxValue) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
            this.extractor = extractor;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }
        
        /**
         * Gets the appropriate threshold for this metric type.
         */
        public HealthThresholds.MetricThreshold getThreshold(HealthThresholds thresholds) {
            return switch (this) {
                case TPS -> thresholds.getTps();
                case MEMORY -> thresholds.getMemory();
                case CHUNKS -> thresholds.getChunks();
                case ENTITIES -> thresholds.getEntities();
                case HOPPERS -> thresholds.getHoppers();
                case REDSTONE -> thresholds.getRedstone();
                case COST_INDEX -> thresholds.getCostIndex();
            };
        }
    }
    
    public enum TimeScale {
        ONE_MINUTE(1, "1m"),
        FIVE_MINUTES(5, "5m"),
        TEN_MINUTES(10, "10m"),
        ONE_HOUR(60, "1h");
        
        private final int minutes;
        private final String label;
        
        TimeScale(int minutes, String label) {
            this.minutes = minutes;
            this.label = label;
        }
        
        public int getMinutes() {
            return minutes;
        }
    }
    
    private static final int CHART_START_ROW = 0;
    private static final int CHART_HEIGHT = 4; // 4 rows for the chart
    private static final int CHART_WIDTH = 9;  // Full width
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final ServerHealthService healthService;
    private final MetricType metricType;
    private final HealthThresholds thresholds;
    private TimeScale timeScale;
    private final Inventory inventory;

    public HealthChartGui(SMPStats plugin, GuiManager guiManager, ServerHealthService healthService, 
                          MetricType metricType, TimeScale timeScale) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.healthService = healthService;
        this.metricType = metricType;
        this.timeScale = timeScale;
        this.thresholds = plugin.getSettings().getHealthThresholds();
        this.inventory = Bukkit.createInventory(this, 54, 
                Component.text(metricType.displayName + " History", metricType.color));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        List<HealthSnapshot> history = healthService.getHistory(timeScale.minutes);
        
        if (history.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, 
                    Component.text("No historical data", NamedTextColor.RED),
                    Component.text("Data is collected every sample interval", NamedTextColor.GRAY)));
        } else {
            drawChart(history);
        }
        
        addNavigationButtons();
    }
    
    private void drawChart(List<HealthSnapshot> history) {
        // Extract values and timestamps
        double[] values = new double[history.size()];
        long[] timestamps = new long[history.size()];
        for (int i = 0; i < history.size(); i++) {
            values[i] = metricType.extractor.apply(history.get(i));
            timestamps[i] = history.get(i).timestamp();
        }
        
        // Determine scale
        double minVal = metricType.minValue;
        double maxVal = metricType.maxValue;
        
        if (maxVal < 0) {
            // Auto-scale: find max value in data
            maxVal = 0;
            for (double v : values) {
                if (v > maxVal) maxVal = v;
            }
            if (maxVal == 0) maxVal = 1; // Prevent division by zero
            maxVal = maxVal * 1.1; // Add 10% headroom
        }
        
        // Sample values and timestamps to fit 9 columns
        double[] sampledValues = sampleToWidth(values, CHART_WIDTH);
        long[] sampledTimestamps = sampleTimestampsToWidth(timestamps, CHART_WIDTH);
        
        // Get threshold for color coding
        HealthThresholds.MetricThreshold threshold = metricType.getThreshold(thresholds);
        
        // Draw the chart using colored glass panes
        // Row 0 (top) = highest values, Row 3 (bottom of chart) = lowest values
        for (int col = 0; col < CHART_WIDTH; col++) {
            double value = sampledValues[col];
            double normalizedValue = (value - minVal) / (maxVal - minVal);
            int filledRows = (int) Math.round(normalizedValue * CHART_HEIGHT);
            filledRows = Math.max(0, Math.min(CHART_HEIGHT, filledRows));
            
            // Format timestamp for this column
            String timeStr = formatTimestamp(sampledTimestamps[col]);
            String status = threshold.getStatus(value);
            
            for (int row = 0; row < CHART_HEIGHT; row++) {
                int slot = (CHART_START_ROW + row) * 9 + col;
                int rowFromBottom = CHART_HEIGHT - 1 - row;
                
                if (rowFromBottom < filledRows) {
                    // This row is filled - use threshold-based coloring
                    Material barMaterial = getBarMaterialFromThreshold(value, threshold);
                    String valueStr = formatValue(value);
                    inventory.setItem(slot, createGuiItem(barMaterial, 
                            Component.text(metricType.displayName + ": " + valueStr, metricType.color),
                            Component.text("Status: " + status, getStatusColor(status)),
                            Component.text("Time: " + timeStr, NamedTextColor.GRAY)));
                } else {
                    // Empty row - still show timestamp on hover
                    inventory.setItem(slot, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, 
                            Component.text(" "),
                            Component.text("Time: " + timeStr, NamedTextColor.DARK_GRAY)));
                }
            }
        }
        
        // Add current value indicator in info row (row 4)
        HealthSnapshot latest = healthService.getLatest();
        if (latest != null) {
            double currentValue = metricType.extractor.apply(latest);
            String status = threshold.getStatus(currentValue);
            inventory.setItem(40, createGuiItem(metricType.icon,
                    Component.text("Current: " + formatValue(currentValue), metricType.color),
                    Component.text("Status: " + status, getStatusColor(status)),
                    Component.text("Scale: " + timeScale.label + " | Samples: " + history.size(), NamedTextColor.GRAY),
                    Component.text("Thresholds - Good: " + formatValue(threshold.good()) 
                            + " | Warn: " + formatValue(threshold.warning()), NamedTextColor.DARK_GRAY)));
        }
    }
    
    private long[] sampleTimestampsToWidth(long[] timestamps, int width) {
        long[] result = new long[width];
        if (timestamps.length == 0) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < width; i++) {
                result[i] = now;
            }
            return result;
        }
        
        if (timestamps.length <= width) {
            for (int col = 0; col < width; col++) {
                int idx = (int) ((col * (timestamps.length - 1)) / (double) Math.max(1, width - 1));
                idx = Math.min(idx, timestamps.length - 1);
                result[col] = timestamps[idx];
            }
        } else {
            double bucketSize = (double) timestamps.length / width;
            for (int col = 0; col < width; col++) {
                int idx = (int) ((col + 0.5) * bucketSize);
                idx = Math.min(idx, timestamps.length - 1);
                result[col] = timestamps[idx];
            }
        }
        return result;
    }
    
    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) return "N/A";
        return TIME_FORMAT.format(new Date(timestamp));
    }
    
    private NamedTextColor getStatusColor(String status) {
        return switch (status) {
            case "Good" -> NamedTextColor.GREEN;
            case "Acceptable" -> NamedTextColor.DARK_GREEN;
            case "Warning" -> NamedTextColor.YELLOW;
            case "Bad" -> NamedTextColor.GOLD;
            default -> NamedTextColor.RED; // Critical
        };
    }
    
    private Material getBarMaterialFromThreshold(double value, HealthThresholds.MetricThreshold threshold) {
        double quality = threshold.getQuality(value);
        if (quality >= 1.0) return Material.LIME_STAINED_GLASS_PANE;     // Good
        if (quality >= 0.75) return Material.GREEN_STAINED_GLASS_PANE;   // Acceptable
        if (quality >= 0.5) return Material.YELLOW_STAINED_GLASS_PANE;   // Warning
        if (quality >= 0.25) return Material.ORANGE_STAINED_GLASS_PANE;  // Bad
        return Material.RED_STAINED_GLASS_PANE;                          // Critical
    }
    
    private double[] sampleToWidth(double[] values, int width) {
        double[] result = new double[width];
        if (values.length == 0) {
            return result;
        }
        
        if (values.length <= width) {
            // Spread values across available columns
            for (int col = 0; col < width; col++) {
                int idx = (int) ((col * (values.length - 1)) / (double) (width - 1));
                idx = Math.min(idx, values.length - 1);
                result[col] = values[idx];
            }
        } else {
            // Average values into buckets
            double bucketSize = (double) values.length / width;
            for (int col = 0; col < width; col++) {
                int startIdx = (int) (col * bucketSize);
                int endIdx = (int) ((col + 1) * bucketSize);
                endIdx = Math.min(endIdx, values.length);
                
                double sum = 0;
                int count = 0;
                for (int i = startIdx; i < endIdx; i++) {
                    sum += values[i];
                    count++;
                }
                result[col] = count > 0 ? sum / count : 0;
            }
        }
        return result;
    }
    
    private String formatValue(double value) {
        if (value >= 1000000) {
            return String.format("%.1fM", value / 1000000);
        } else if (value >= 1000) {
            return String.format("%.1fK", value / 1000);
        } else if (value == (int) value) {
            return String.valueOf((int) value);
        } else {
            return String.format("%.1f", value);
        }
    }

    private void addNavigationButtons() {
        // Row 5 (slots 45-53): Navigation buttons
        // Layout: [Back] [empty] [1m] [5m] [10m] [1h] [empty] [empty] [Refresh]
        
        // Back button
        inventory.setItem(45, createGuiItem(Material.ARROW, 
                Component.text("Back", NamedTextColor.RED)));
        
        // Time scale buttons
        for (int i = 0; i < TimeScale.values().length; i++) {
            TimeScale scale = TimeScale.values()[i];
            boolean isSelected = scale == this.timeScale;
            Material mat = isSelected ? Material.LIME_DYE : Material.GRAY_DYE;
            NamedTextColor color = isSelected ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            
            inventory.setItem(47 + i, createGuiItem(mat,
                    Component.text(scale.label, color),
                    isSelected ? Component.text("Selected", NamedTextColor.DARK_GREEN) 
                               : Component.text("Click to select", NamedTextColor.DARK_GRAY)));
        }
        
        // Refresh button
        inventory.setItem(53, createGuiItem(Material.SUNFLOWER, 
                Component.text("Refresh", NamedTextColor.GREEN),
                Component.text("Sample now & refresh", NamedTextColor.GRAY)));
        
        // Fill remaining slots in navigation row
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 45; i < 54; i++) {
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
        initializeItems();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        playClickSound(player);
        
        if (slot == 45) {
            // Back
            guiManager.openGui(player, new ServerHealthGui(plugin, guiManager, healthService));
        } else if (slot >= 47 && slot <= 50) {
            // Time scale selection
            int scaleIndex = slot - 47;
            if (scaleIndex < TimeScale.values().length) {
                this.timeScale = TimeScale.values()[scaleIndex];
                playSuccessSound(player);
                initializeItems();
            }
        } else if (slot == 53) {
            // Refresh
            healthService.sampleNow();
            playSuccessSound(player);
            initializeItems();
            player.sendMessage(Component.text("Data refreshed!", NamedTextColor.GREEN));
        }
    }
}
