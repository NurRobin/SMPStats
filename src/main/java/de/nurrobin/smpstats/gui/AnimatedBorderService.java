package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static de.nurrobin.smpstats.gui.GuiUtils.createBorderItem;

/**
 * Service for creating animated borders in GUIs.
 * Provides cycling color effects for highlighted items.
 */
public class AnimatedBorderService {
    private final SMPStats plugin;
    private final Map<UUID, AnimatedInventory> animatedInventories = new ConcurrentHashMap<>();
    private BukkitTask animationTask;
    private int currentColorIndex = 0;
    
    // Animation speed in ticks (20 ticks = 1 second)
    private static final int ANIMATION_INTERVAL = 5; // 4 updates per second
    
    /**
     * Rainbow color sequence for border animation.
     */
    public static final Material[] RAINBOW_COLORS = {
            Material.RED_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE
    };
    
    /**
     * Gold pulse colors for important items.
     */
    public static final Material[] GOLD_PULSE = {
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.WHITE_STAINED_GLASS_PANE
    };
    
    /**
     * Green success pulse.
     */
    public static final Material[] SUCCESS_PULSE = {
            Material.LIME_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.WHITE_STAINED_GLASS_PANE
    };
    
    /**
     * Red warning pulse.
     */
    public static final Material[] WARNING_PULSE = {
            Material.RED_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE
    };
    
    /**
     * Blue info pulse.
     */
    public static final Material[] INFO_PULSE = {
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.WHITE_STAINED_GLASS_PANE
    };
    
    /**
     * Animation preset types.
     */
    public enum AnimationPreset {
        RAINBOW(RAINBOW_COLORS),
        GOLD_PULSE(AnimatedBorderService.GOLD_PULSE),
        SUCCESS(SUCCESS_PULSE),
        WARNING(WARNING_PULSE),
        INFO(INFO_PULSE);
        
        private final Material[] colors;
        
        AnimationPreset(Material[] colors) {
            this.colors = colors;
        }
        
        public Material[] getColors() {
            return colors;
        }
    }
    
    /**
     * Internal class to track animated inventories.
     */
    private static class AnimatedInventory {
        final Inventory inventory;
        final int[] animatedSlots;
        final Material[] colors;
        
        AnimatedInventory(Inventory inventory, int[] slots, Material[] colors) {
            this.inventory = inventory;
            this.animatedSlots = slots;
            this.colors = colors;
        }
    }

    public AnimatedBorderService(SMPStats plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Starts the animation task. Should be called when plugin enables.
     */
    public void start() {
        if (animationTask != null) {
            animationTask.cancel();
        }
        
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAnimations, 
                ANIMATION_INTERVAL, ANIMATION_INTERVAL);
    }
    
    /**
     * Stops the animation task. Should be called when plugin disables.
     */
    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        animatedInventories.clear();
    }
    
    /**
     * Registers an inventory for border animation.
     * 
     * @param player The player viewing the inventory
     * @param inventory The inventory to animate
     * @param slots The slots to animate
     * @param colors The color sequence to cycle through
     */
    public void registerAnimation(Player player, Inventory inventory, int[] slots, Material[] colors) {
        animatedInventories.put(player.getUniqueId(), new AnimatedInventory(inventory, slots, colors));
    }
    
    /**
     * Registers an inventory for border animation using a preset.
     * 
     * @param player The player viewing the inventory
     * @param inventory The inventory to animate
     * @param slots The slots to animate
     * @param preset The animation preset to use
     */
    public void registerAnimation(Player player, Inventory inventory, int[] slots, AnimationPreset preset) {
        registerAnimation(player, inventory, slots, preset.getColors());
    }
    
    /**
     * Unregisters an inventory from animation.
     * 
     * @param player The player whose inventory should stop animating
     */
    public void unregisterAnimation(Player player) {
        animatedInventories.remove(player.getUniqueId());
    }
    
    /**
     * Checks if a player has an active animation.
     * 
     * @param player The player to check
     * @return true if the player has an active animation
     */
    public boolean hasAnimation(Player player) {
        return animatedInventories.containsKey(player.getUniqueId());
    }
    
    /**
     * Updates all active animations.
     */
    private void updateAnimations() {
        currentColorIndex++;
        
        Iterator<Map.Entry<UUID, AnimatedInventory>> iterator = animatedInventories.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AnimatedInventory> entry = iterator.next();
            UUID playerId = entry.getKey();
            AnimatedInventory anim = entry.getValue();
            
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            
            // Check if player still has this inventory open
            if (!player.getOpenInventory().getTopInventory().equals(anim.inventory)) {
                iterator.remove();
                continue;
            }
            
            // Update animated slots
            int colorIdx = currentColorIndex % anim.colors.length;
            ItemStack borderItem = createBorderItem(anim.colors[colorIdx]);
            
            for (int slot : anim.animatedSlots) {
                if (slot >= 0 && slot < anim.inventory.getSize()) {
                    anim.inventory.setItem(slot, borderItem);
                }
            }
        }
    }
    
    /**
     * Gets the standard border slots for a 54-slot (6-row) inventory.
     * Returns all edge slots.
     * 
     * @return Array of border slot indices
     */
    public static int[] getBorderSlots54() {
        return new int[]{
                0, 1, 2, 3, 4, 5, 6, 7, 8,  // Top row
                9, 17,                       // Second row edges
                18, 26,                      // Third row edges
                27, 35,                      // Fourth row edges
                36, 44,                      // Fifth row edges
                45, 46, 47, 48, 49, 50, 51, 52, 53  // Bottom row
        };
    }
    
    /**
     * Gets the standard border slots for a 45-slot (5-row) inventory.
     * Returns all edge slots.
     * 
     * @return Array of border slot indices
     */
    public static int[] getBorderSlots45() {
        return new int[]{
                0, 1, 2, 3, 4, 5, 6, 7, 8,  // Top row
                9, 17,                       // Second row edges
                18, 26,                      // Third row edges
                27, 35,                      // Fourth row edges
                36, 37, 38, 39, 40, 41, 42, 43, 44  // Bottom row
        };
    }
    
    /**
     * Gets the top row slots for any inventory.
     * 
     * @return Array of top row slot indices (0-8)
     */
    public static int[] getTopRowSlots() {
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }
    
    /**
     * Gets the bottom row slots for a 54-slot inventory.
     * 
     * @return Array of bottom row slot indices
     */
    public static int[] getBottomRowSlots54() {
        return new int[]{45, 46, 47, 48, 49, 50, 51, 52, 53};
    }
    
    /**
     * Gets the bottom row slots for a 45-slot inventory.
     * 
     * @return Array of bottom row slot indices
     */
    public static int[] getBottomRowSlots45() {
        return new int[]{36, 37, 38, 39, 40, 41, 42, 43, 44};
    }
    
    /**
     * Combines multiple slot arrays into one.
     * 
     * @param arrays The slot arrays to combine
     * @return Combined array of all slots
     */
    public static int[] combineSlots(int[]... arrays) {
        int totalLength = 0;
        for (int[] arr : arrays) {
            totalLength += arr.length;
        }
        
        int[] result = new int[totalLength];
        int index = 0;
        for (int[] arr : arrays) {
            for (int slot : arr) {
                result[index++] = slot;
            }
        }
        return result;
    }
    
    /**
     * Creates a highlight around a specific slot.
     * Returns adjacent slots that can be animated.
     * 
     * @param centerSlot The slot to highlight around
     * @param inventoryWidth The width of the inventory (typically 9)
     * @param inventorySize The total size of the inventory
     * @return Array of adjacent slot indices
     */
    public static int[] getHighlightSlots(int centerSlot, int inventoryWidth, int inventorySize) {
        List<Integer> slots = new ArrayList<>();
        
        int row = centerSlot / inventoryWidth;
        int col = centerSlot % inventoryWidth;
        
        // Check all 8 adjacent positions
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue; // Skip center
                
                int newRow = row + dr;
                int newCol = col + dc;
                
                if (newRow >= 0 && newRow < inventorySize / inventoryWidth &&
                    newCol >= 0 && newCol < inventoryWidth) {
                    slots.add(newRow * inventoryWidth + newCol);
                }
            }
        }
        
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
