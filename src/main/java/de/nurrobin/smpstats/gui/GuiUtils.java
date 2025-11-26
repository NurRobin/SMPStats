package de.nurrobin.smpstats.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Utility class for creating GUI items used across all inventory GUIs.
 */
public final class GuiUtils {

    private GuiUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a GUI item with the specified material, display name, and lore.
     *
     * @param material the material for the item
     * @param name     the display name component
     * @param lore     optional lore components
     * @return the configured ItemStack
     */
    public static ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) {
            meta.lore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }
}
