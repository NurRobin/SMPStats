package de.nurrobin.smpstats.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

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

    /**
     * Creates a player head GUI item with the specified player's skin.
     *
     * @param owner the player whose head skin to use
     * @param name  the display name component
     * @param lore  optional lore components
     * @return the configured ItemStack with player head
     */
    public static ItemStack createPlayerHead(OfflinePlayer owner, Component name, Component... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.displayName(name);
        if (lore.length > 0) {
            meta.lore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Plays a click sound for GUI feedback.
     *
     * @param player the player to play the sound for
     */
    public static void playClickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Plays a success sound for GUI feedback.
     *
     * @param player the player to play the sound for
     */
    public static void playSuccessSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
    }

    /**
     * Plays an error sound for GUI feedback.
     *
     * @param player the player to play the sound for
     */
    public static void playErrorSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }
}
