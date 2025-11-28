package de.nurrobin.smpstats.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.UUID;

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
     * Creates a ranked player head for leaderboards with the rank shown as item amount.
     * The item amount represents the player's rank (1-64, capped at 64 for Minecraft limits).
     * Includes visual rank indicators (gold/silver/bronze for top 3).
     *
     * @param owner the player whose head skin to use
     * @param rank  the rank position (1-based)
     * @param name  the display name component
     * @param lore  optional lore components
     * @return the configured ItemStack with player head and rank amount
     */
    public static ItemStack createRankedPlayerHead(OfflinePlayer owner, int rank, Component name, Component... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, Math.min(rank, 64));
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        
        // Add rank prefix with appropriate color
        Component rankPrefix = getRankPrefix(rank);
        meta.displayName(rankPrefix.append(Component.text(" ")).append(name));
        
        if (lore.length > 0) {
            meta.lore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a ranked player head by UUID for players who may be offline.
     * Falls back to a generic head if the player cannot be resolved.
     *
     * @param uuid        the UUID of the player
     * @param playerName  the player's name for display
     * @param rank        the rank position (1-based)
     * @param valueLine   the stat value line to display
     * @param statColor   the color for the stat value
     * @return the configured ItemStack with player head and rank amount
     */
    public static ItemStack createRankedPlayerHeadByUuid(UUID uuid, String playerName, int rank, 
                                                          String valueLine, NamedTextColor statColor) {
        OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        
        Component nameComponent = Component.text(playerName, NamedTextColor.WHITE);
        Component loreLine = Component.text(valueLine, statColor);
        Component rankInfo = Component.text("Rank #" + rank, NamedTextColor.DARK_GRAY);
        
        return createRankedPlayerHead(offlinePlayer, rank, nameComponent, loreLine, rankInfo);
    }

    /**
     * Gets the rank prefix component with appropriate styling.
     *
     * @param rank the rank position (1-based)
     * @return a styled component showing the rank
     */
    public static Component getRankPrefix(int rank) {
        return switch (rank) {
            case 1 -> Component.text("🥇 #1", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
            case 2 -> Component.text("🥈 #2", NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
            case 3 -> Component.text("🥉 #3", NamedTextColor.RED).decorate(TextDecoration.BOLD);
            default -> Component.text("#" + rank, NamedTextColor.WHITE);
        };
    }

    /**
     * Gets the appropriate color for a given rank.
     *
     * @param rank the rank position (1-based)
     * @return the color for the rank
     */
    public static NamedTextColor getRankColor(int rank) {
        return switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
    }

    /**
     * Creates a decorative border item for GUI backgrounds.
     *
     * @param color the color of the glass pane (e.g., Material.GRAY_STAINED_GLASS_PANE)
     * @return the configured ItemStack
     */
    public static ItemStack createBorderItem(Material color) {
        return createGuiItem(color, Component.text(" "));
    }

    /**
     * Creates an info item that displays helpful information to the player.
     *
     * @param title the title of the info item
     * @param lines the information lines to display
     * @return the configured ItemStack
     */
    public static ItemStack createInfoItem(String title, String... lines) {
        Component[] lore = new Component[lines.length];
        for (int i = 0; i < lines.length; i++) {
            lore[i] = Component.text(lines[i], NamedTextColor.GRAY);
        }
        return createGuiItem(Material.BOOK, 
                Component.text(title, NamedTextColor.YELLOW).decorate(TextDecoration.BOLD), 
                lore);
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

    /**
     * Plays a page turn sound for pagination feedback.
     *
     * @param player the player to play the sound for
     */
    public static void playPageTurnSound(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
    }
}
