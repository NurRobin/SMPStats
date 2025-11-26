package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.HealthSnapshot;
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

import java.util.List;
import java.util.function.Function;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * Displays a primitive chart of health metrics over time using inventory slots.
 * The chart uses 9 columns (width) and 4 rows (height) for the graph area.
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
    }
    
    private static final int CHART_START_ROW = 0;
    private static final int CHART_HEIGHT = 4; // 4 rows for the chart
    private static final int CHART_WIDTH = 9;  // Full width
    
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final ServerHealthService healthService;
    private final MetricType metricType;
    private TimeScale timeScale;
    private final Inventory inventory;

    public HealthChartGui(SMPStats plugin, GuiManager guiManager, ServerHealthService healthService, 
                          MetricType metricType, TimeScale timeScale) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.healthService = healthService;
        this.metricType = metricType;
        this.timeScale = timeScale;
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
        // Extract values
        double[] values = new double[history.size()];
        for (int i = 0; i < history.size(); i++) {
            values[i] = metricType.extractor.apply(history.get(i));
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
        
        // Sample values to fit 9 columns
        double[] sampledValues = sampleToWidth(values, CHART_WIDTH);
        
        // Draw the chart using colored glass panes
        // Row 0 (top) = highest values, Row 3 (bottom of chart) = lowest values
        for (int col = 0; col < CHART_WIDTH; col++) {
            double value = sampledValues[col];
            double normalizedValue = (value - minVal) / (maxVal - minVal);
            int filledRows = (int) Math.round(normalizedValue * CHART_HEIGHT);
            filledRows = Math.max(0, Math.min(CHART_HEIGHT, filledRows));
            
            for (int row = 0; row < CHART_HEIGHT; row++) {
                int slot = (CHART_START_ROW + row) * 9 + col;
                int rowFromBottom = CHART_HEIGHT - 1 - row;
                
                if (rowFromBottom < filledRows) {
                    // This row is filled
                    Material barMaterial = getBarMaterial(normalizedValue);
                    String valueStr = formatValue(value);
                    inventory.setItem(slot, createGuiItem(barMaterial, 
                            Component.text(metricType.displayName + ": " + valueStr, metricType.color)));
                } else {
                    // Empty row
                    inventory.setItem(slot, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, 
                            Component.text(" ")));
                }
            }
        }
        
        // Add current value indicator in info row (row 4)
        HealthSnapshot latest = healthService.getLatest();
        if (latest != null) {
            double currentValue = metricType.extractor.apply(latest);
            inventory.setItem(40, createGuiItem(metricType.icon,
                    Component.text("Current: " + formatValue(currentValue), metricType.color),
                    Component.text("Min: " + formatValue(minVal) + " | Max: " + formatValue(maxVal), NamedTextColor.GRAY),
                    Component.text("Samples: " + history.size(), NamedTextColor.DARK_GRAY)));
        }
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
    
    private Material getBarMaterial(double normalizedValue) {
        if (metricType == MetricType.TPS) {
            // For TPS, higher is better
            if (normalizedValue >= 0.9) return Material.LIME_STAINED_GLASS_PANE;
            if (normalizedValue >= 0.75) return Material.GREEN_STAINED_GLASS_PANE;
            if (normalizedValue >= 0.5) return Material.YELLOW_STAINED_GLASS_PANE;
            if (normalizedValue >= 0.25) return Material.ORANGE_STAINED_GLASS_PANE;
            return Material.RED_STAINED_GLASS_PANE;
        } else {
            // For other metrics, lower is often better (memory, entities, etc.)
            if (normalizedValue <= 0.25) return Material.LIME_STAINED_GLASS_PANE;
            if (normalizedValue <= 0.5) return Material.GREEN_STAINED_GLASS_PANE;
            if (normalizedValue <= 0.7) return Material.YELLOW_STAINED_GLASS_PANE;
            if (normalizedValue <= 0.85) return Material.ORANGE_STAINED_GLASS_PANE;
            return Material.RED_STAINED_GLASS_PANE;
        }
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
