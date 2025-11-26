package de.nurrobin.smpstats.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public interface InventoryGui {
    Inventory getInventory();
    void open(Player player);
    void handleClick(InventoryClickEvent event);
}
