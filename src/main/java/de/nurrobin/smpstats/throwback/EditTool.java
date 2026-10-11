package de.nurrobin.smpstats.throwback;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The hotbar of the edit mode, in slot order. A tool is recognised by the tag in its item data alone, never by
 * its name or material, so renamed or look-alike items do nothing.
 */
enum EditTool {
    // not the plain compass: WorldEdit's navigation wand is a compass and teleports through walls on every click
    START("start", Material.RECOVERY_COMPASS, EditKind.START),
    STAGE("stage", Material.ARMOR_STAND, EditKind.STAGE),
    // paper, because a writable book opens its editor on the client before the server can stop the click
    RULES("rules", Material.PAPER, EditKind.RULES),
    PRIVACY("privacy", Material.BOOK, EditKind.PRIVACY),
    PORTAL_1("portal1", Material.ENDER_PEARL, EditKind.PORTAL_1),
    PORTAL_2("portal2", Material.ENDER_EYE, EditKind.PORTAL_2),
    // a name tag does nothing on a block, so the click reaches the edit mode alone
    TEXT("text", Material.NAME_TAG, EditKind.TEXT),
    UNDO("undo", Material.RED_DYE, null),
    SAVE("save", Material.LIME_DYE, null);

    private final String id;
    private final Material material;
    private final EditKind kind;

    EditTool(String id, Material material, EditKind kind) {
        this.id = id;
        this.material = material;
        this.kind = kind;
    }

    String id() {
        return id;
    }

    Material material() {
        return material;
    }

    /** The marker kind this tool places and removes; null for undo and save. */
    EditKind kind() {
        return kind;
    }

    static NamespacedKey key(org.bukkit.plugin.Plugin plugin) {
        return new NamespacedKey(plugin, "throwback_edit_tool");
    }

    /** The tool an item stands for, or null if it carries no tool tag. */
    static EditTool of(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        for (EditTool tool : values()) {
            if (tool.id.equals(id)) {
                return tool;
            }
        }
        return null;
    }
}
