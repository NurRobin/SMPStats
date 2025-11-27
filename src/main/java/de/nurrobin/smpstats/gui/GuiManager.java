package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiManager implements Listener {
    private final SMPStats plugin;
    private final Map<UUID, InventoryGui> openGuis = new HashMap<>();

    public GuiManager(SMPStats plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, InventoryGui gui) {
        openGuis.put(player.getUniqueId(), gui);
        gui.open(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        InventoryGui gui = openGuis.get(player.getUniqueId());

        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            event.setCancelled(true); // Default to cancelled for GUIs
            gui.handleClick(event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        InventoryGui gui = openGuis.get(player.getUniqueId());

        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        // Only remove if it's the GUI we are tracking
        InventoryGui gui = openGuis.get(player.getUniqueId());
        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            openGuis.remove(player.getUniqueId());
        }
    }
}
