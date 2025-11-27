package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
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

public class ServerHealthGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final ServerHealthService healthService;
    private final Inventory inventory;

    public ServerHealthGui(SMPStats plugin, GuiManager guiManager, ServerHealthService healthService) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.healthService = healthService;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Server Health", NamedTextColor.DARK_BLUE));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        var snapshot = healthService.getLatest();
        if (snapshot == null) {
            inventory.setItem(13, createGuiItem(Material.BARRIER, Component.text("No data yet", NamedTextColor.RED)));
            addNavigationButtons();
            return;
        }

        // TPS
        double tps = Math.min(20.0, Math.round(snapshot.tps() * 100.0) / 100.0);
        NamedTextColor tpsColor = tps >= 18.0 ? NamedTextColor.GREEN : (tps >= 15.0 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        inventory.setItem(10, createGuiItem(Material.CLOCK, Component.text("TPS", NamedTextColor.GOLD),
                Component.text(String.valueOf(tps), tpsColor),
                Component.text(getTpsStatus(tps), tpsColor),
                Component.text("Click for history", NamedTextColor.DARK_GRAY)));

        // Memory
        long usedMb = snapshot.memoryUsed() / 1024 / 1024;
        long maxMb = snapshot.memoryMax() / 1024 / 1024;
        if (maxMb == 0) maxMb = 1; // Prevent division by zero
        int memoryPercent = (int) ((usedMb * 100) / maxMb);
        NamedTextColor memColor = memoryPercent < 70 ? NamedTextColor.GREEN : (memoryPercent < 85 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        inventory.setItem(11, createGuiItem(Material.ENDER_CHEST, Component.text("Memory", NamedTextColor.AQUA),
                Component.text(usedMb + "MB / " + maxMb + "MB", memColor),
                Component.text(memoryPercent + "% used", NamedTextColor.GRAY),
                Component.text("Click for history", NamedTextColor.DARK_GRAY)));

        inventory.setItem(12, createGuiItem(Material.GRASS_BLOCK, Component.text("Chunks", NamedTextColor.GREEN),
                Component.text(String.valueOf(snapshot.chunks()), NamedTextColor.WHITE),
                Component.text("Click for history", NamedTextColor.DARK_GRAY)));

        inventory.setItem(13, createGuiItem(Material.CREEPER_HEAD, Component.text("Entities", NamedTextColor.RED),
                Component.text(String.valueOf(snapshot.entities()), NamedTextColor.WHITE),
                Component.text("Left-click: History chart", NamedTextColor.DARK_GRAY),
                Component.text("Right-click: Entity breakdown", NamedTextColor.GRAY)));

        inventory.setItem(14, createGuiItem(Material.HOPPER, Component.text("Hoppers", NamedTextColor.GRAY),
                Component.text(String.valueOf(snapshot.hoppers()), NamedTextColor.WHITE),
                Component.text("Click for history", NamedTextColor.DARK_GRAY)));

        inventory.setItem(15, createGuiItem(Material.REDSTONE, Component.text("Redstone", NamedTextColor.DARK_RED),
                Component.text(String.valueOf(snapshot.redstone()), NamedTextColor.WHITE),
                Component.text("Click for history", NamedTextColor.DARK_GRAY)));
        
        double costIndex = snapshot.costIndex();
        NamedTextColor costColor = costIndex < 50 ? NamedTextColor.GREEN : (costIndex < 100 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        inventory.setItem(16, createGuiItem(Material.EMERALD, Component.text("Cost Index", NamedTextColor.DARK_GREEN),
                Component.text(String.format("%.1f", costIndex), costColor),
                Component.text(getCostStatus(costIndex), NamedTextColor.GRAY),
                Component.text("Click for history", NamedTextColor.DARK_GRAY)));

        addNavigationButtons();
    }

    private void addNavigationButtons() {
        // Hot Chunks Button
        inventory.setItem(18, createGuiItem(Material.MAGMA_CREAM, Component.text("Hot Chunks", NamedTextColor.GOLD),
                Component.text("View chunks with high load", NamedTextColor.GRAY)));

        // Back Button
        inventory.setItem(22, createGuiItem(Material.ARROW, Component.text("Back", NamedTextColor.RED)));

        // Refresh Button  
        inventory.setItem(26, createGuiItem(Material.SUNFLOWER, Component.text("Refresh", NamedTextColor.GREEN),
                Component.text("Click to refresh data", NamedTextColor.GRAY)));

        // Fill background
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private String getTpsStatus(double tps) {
        if (tps >= 19.5) return "Excellent";
        if (tps >= 18.0) return "Good";
        if (tps >= 15.0) return "Fair";
        return "Poor";
    }

    private String getCostStatus(double cost) {
        if (cost < 30) return "Low load";
        if (cost < 50) return "Moderate load";
        if (cost < 100) return "High load";
        return "Critical load";
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void open(Player player) {
        // Refresh data when opening to ensure we show current values
        initializeItems();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        playClickSound(player);
        
        // Stat item clicks - open chart view
        switch (slot) {
            case 10 -> guiManager.openGui(player, new HealthChartGui(plugin, guiManager, healthService, 
                    HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES));
            case 11 -> guiManager.openGui(player, new HealthChartGui(plugin, guiManager, healthService, 
                    HealthChartGui.MetricType.MEMORY, HealthChartGui.TimeScale.FIVE_MINUTES));
            case 12 -> guiManager.openGui(player, new HealthChartGui(plugin, guiManager, healthService, 
                    HealthChartGui.MetricType.CHUNKS, HealthChartGui.TimeScale.FIVE_MINUTES));
            case 13 -> {
                if (event.getClick().isRightClick()) {
                    guiManager.openGui(player, new EntityBreakdownGui(plugin, guiManager, healthService, 0));
                } else {
                    guiManager.openGui(player, new HealthChartGui(plugin, guiManager, healthService, 
                            HealthChartGui.MetricType.ENTITIES, HealthChartGui.TimeScale.FIVE_MINUTES));
                }
            }
            case 14 -> guiManager.openGui(player, new HealthChartGui(plugin, guiManager, healthService, 
                    HealthChartGui.MetricType.HOPPERS, HealthChartGui.TimeScale.FIVE_MINUTES));
            case 15 -> guiManager.openGui(player, new HealthChartGui(plugin, guiManager, healthService, 
                    HealthChartGui.MetricType.REDSTONE, HealthChartGui.TimeScale.FIVE_MINUTES));
            case 16 -> guiManager.openGui(player, new HealthChartGui(plugin, guiManager, healthService, 
                    HealthChartGui.MetricType.COST_INDEX, HealthChartGui.TimeScale.FIVE_MINUTES));
            case 18 -> guiManager.openGui(player, new HotChunksGui(plugin, guiManager, healthService));
            case 22 -> guiManager.openGui(player, new MainMenuGui(plugin, guiManager, plugin.getStatsService(), healthService));
            case 26 -> {
                // Refresh - trigger immediate sample and refresh display
                healthService.sampleNow();
                playSuccessSound(player);
                initializeItems();
                player.sendMessage(Component.text("Server health refreshed!", NamedTextColor.GREEN));
            }
        }
    }
}
