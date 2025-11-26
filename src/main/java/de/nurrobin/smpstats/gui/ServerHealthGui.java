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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;

import static de.nurrobin.smpstats.gui.GuiUtils.createGuiItem;

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
        var snapshot = healthService.getLatest();
        if (snapshot == null) {
            inventory.setItem(13, createGuiItem(Material.BARRIER, Component.text("No data yet", NamedTextColor.RED)));
            // Back Button
            inventory.setItem(22, createGuiItem(Material.ARROW, Component.text("Back", NamedTextColor.RED)));
            return;
        }

        // TPS
        double tps = Math.min(20.0, Math.round(snapshot.tps() * 100.0) / 100.0);
        NamedTextColor tpsColor = tps >= 18.0 ? NamedTextColor.GREEN : (tps >= 15.0 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        inventory.setItem(10, createGuiItem(Material.CLOCK, Component.text("TPS", NamedTextColor.GOLD),
                Component.text(String.valueOf(tps), tpsColor)));

        // Memory
        long usedMb = snapshot.memoryUsed() / 1024 / 1024;
        long maxMb = snapshot.memoryMax() / 1024 / 1024;
        inventory.setItem(11, createGuiItem(Material.ENDER_CHEST, Component.text("Memory", NamedTextColor.AQUA),
                Component.text(usedMb + "MB / " + maxMb + "MB", NamedTextColor.WHITE)));

        inventory.setItem(12, createGuiItem(Material.GRASS_BLOCK, Component.text("Chunks", NamedTextColor.GREEN),
                Component.text(String.valueOf(snapshot.chunks()), NamedTextColor.WHITE)));

        inventory.setItem(13, createGuiItem(Material.CREEPER_HEAD, Component.text("Entities", NamedTextColor.RED),
                Component.text(String.valueOf(snapshot.entities()), NamedTextColor.WHITE)));

        inventory.setItem(14, createGuiItem(Material.HOPPER, Component.text("Hoppers", NamedTextColor.GRAY),
                Component.text(String.valueOf(snapshot.hoppers()), NamedTextColor.WHITE)));

        inventory.setItem(15, createGuiItem(Material.REDSTONE, Component.text("Redstone", NamedTextColor.DARK_RED),
                Component.text(String.valueOf(snapshot.redstone()), NamedTextColor.WHITE)));
        
        inventory.setItem(16, createGuiItem(Material.EMERALD, Component.text("Cost Index", NamedTextColor.DARK_GREEN),
                Component.text(String.valueOf(snapshot.costIndex()), NamedTextColor.WHITE)));

        // Hot Chunks Button
        inventory.setItem(18, createGuiItem(Material.MAGMA_CREAM, Component.text("Hot Chunks", NamedTextColor.GOLD),
                Component.text("View chunks with high load", NamedTextColor.GRAY)));

        // Back Button
        inventory.setItem(22, createGuiItem(Material.ARROW, Component.text("Back", NamedTextColor.RED)));

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
        if (event.getSlot() == 18) {
            guiManager.openGui(player, new HotChunksGui(plugin, guiManager, healthService));
        } else if (event.getSlot() == 22) {
            guiManager.openGui(player, new MainMenuGui(plugin, guiManager, plugin.getStatsService(), healthService));
        }
    }
}
