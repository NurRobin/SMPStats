package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.heatmap.HeatmapBin;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI displaying a personal heatmap preview for a player.
 * Shows activity intensity in a 7x5 grid representing chunks around the player.
 */
public class PersonalHeatmapGui implements InventoryGui, InventoryHolder {
    
    private static final int ROWS = 6;
    private static final int GRID_WIDTH = 7;
    private static final int GRID_HEIGHT = 5;
    private static final int GRID_START_SLOT = 10; // Start at row 2
    
    private static final Material[] INTENSITY_MATERIALS = {
        Material.LIME_STAINED_GLASS_PANE,      // 0-10% - Low
        Material.GREEN_STAINED_GLASS_PANE,     // 10-20%
        Material.YELLOW_STAINED_GLASS_PANE,    // 20-30%
        Material.ORANGE_STAINED_GLASS_PANE,    // 30-40%
        Material.RED_STAINED_GLASS_PANE,       // 40-50%
        Material.PURPLE_STAINED_GLASS_PANE,    // 50-60%
        Material.MAGENTA_STAINED_GLASS_PANE,   // 60-70%
        Material.PINK_STAINED_GLASS_PANE,      // 70-80%
        Material.WHITE_STAINED_GLASS_PANE,     // 80-90%
        Material.LIGHT_BLUE_STAINED_GLASS_PANE // 90-100% - Peak
    };
    
    private static final String[] ACTIVITY_TYPES = {"POSITION", "DEATH", "KILL", "BREAK", "PLACE"};
    private static final String[] ACTIVITY_LABELS = {"Movement", "Deaths", "Kills", "Mining", "Building"};
    
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final Player viewer;
    private final UUID targetUuid;
    private final String targetName;
    private Inventory inventory;
    private int currentActivityIndex = 0;
    private String currentWorld;
    
    public PersonalHeatmapGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                              Player viewer, UUID targetUuid, String targetName) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.currentWorld = viewer.getWorld().getName();
    }
    
    @Override
    public void open(Player player) {
        inventory = Bukkit.createInventory(this, ROWS * 9, 
            Component.text("Heatmap: " + targetName, NamedTextColor.DARK_PURPLE));
        
        renderGrid();
        renderControls();
        player.openInventory(inventory);
    }
    
    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
    
    private void renderGrid() {
        // Clear grid area
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                int slot = GRID_START_SLOT + (row * 9) + col;
                inventory.setItem(slot, null);
            }
        }
        
        // Get player's current chunk position
        Location loc = viewer.getLocation();
        int centerChunkX = loc.getBlockX() >> 4;
        int centerChunkZ = loc.getBlockZ() >> 4;
        
        // Calculate grid bounds (7x5 chunks centered on player)
        int startChunkX = centerChunkX - (GRID_WIDTH / 2);
        int startChunkZ = centerChunkZ - (GRID_HEIGHT / 2);
        
        // Get heatmap data for the activity type
        String activityType = ACTIVITY_TYPES[currentActivityIndex];
        
        // Build a map of chunk -> intensity from the bins
        Map<Long, Double> chunkIntensities = new HashMap<>();
        double maxIntensity = 1.0;
        
        // Query heatmap bins if service is available
        HeatmapService heatmapService = plugin.getHeatmapService().orElse(null);
        if (heatmapService != null) {
            long now = System.currentTimeMillis();
            long weekAgo = now - (7L * 24 * 3600 * 1000);
            List<HeatmapBin> bins = heatmapService.generateHeatmap(activityType, currentWorld, weekAgo, now, 0);
            
            for (HeatmapBin bin : bins) {
                // Check if this bin is within our grid
                int binX = bin.getChunkX();
                int binZ = bin.getChunkZ();
                if (binX >= startChunkX && binX < startChunkX + GRID_WIDTH &&
                    binZ >= startChunkZ && binZ < startChunkZ + GRID_HEIGHT) {
                    long key = ((long) binX << 32) | (binZ & 0xFFFFFFFFL);
                    chunkIntensities.put(key, bin.getCount());
                    if (bin.getCount() > maxIntensity) {
                        maxIntensity = bin.getCount();
                    }
                }
            }
        }
        
        // Render grid with normalized intensities
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                int slot = GRID_START_SLOT + (row * 9) + col;
                int chunkX = startChunkX + col;
                int chunkZ = startChunkZ + row;
                long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                
                double intensity = chunkIntensities.getOrDefault(chunkKey, 0.0);
                int normalizedLevel = (int) ((intensity / maxIntensity) * 9);
                normalizedLevel = Math.min(9, Math.max(0, normalizedLevel));
                
                Material material = intensity > 0 ? INTENSITY_MATERIALS[normalizedLevel] : Material.GRAY_STAINED_GLASS_PANE;
                
                // Mark player's current chunk specially
                boolean isPlayerChunk = (chunkX == centerChunkX && chunkZ == centerChunkZ);
                if (isPlayerChunk) {
                    material = Material.PLAYER_HEAD;
                }
                
                ItemStack item = new ItemStack(material);
                var meta = item.getItemMeta();
                
                // Title shows chunk coordinates
                NamedTextColor color = isPlayerChunk ? NamedTextColor.GOLD : NamedTextColor.GRAY;
                String prefix = isPlayerChunk ? "★ You are here! " : "";
                meta.displayName(Component.text(prefix + "Chunk (" + chunkX + ", " + chunkZ + ")", color)
                    .decoration(TextDecoration.ITALIC, false));
                
                // Lore shows activity level
                String intensityLabel = intensity == 0 ? "No activity" : 
                    String.format("Activity: %.1f events", intensity);
                meta.lore(List.of(
                    Component.text(intensityLabel, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Type: " + ACTIVITY_LABELS[currentActivityIndex], NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
                ));
                
                item.setItemMeta(meta);
                inventory.setItem(slot, item);
            }
        }
    }
    
    private void renderControls() {
        // Activity type selector (slot 0)
        ItemStack activityItem = new ItemStack(Material.COMPASS);
        var activityMeta = activityItem.getItemMeta();
        activityMeta.displayName(Component.text("Activity Type", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        activityMeta.lore(List.of(
            Component.text("Current: " + ACTIVITY_LABELS[currentActivityIndex], NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Click to cycle", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        activityItem.setItemMeta(activityMeta);
        inventory.setItem(0, activityItem);
        
        // World selector (slot 8)
        ItemStack worldItem = new ItemStack(Material.GRASS_BLOCK);
        var worldMeta = worldItem.getItemMeta();
        worldMeta.displayName(Component.text("World", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        worldMeta.lore(List.of(
            Component.text("Current: " + currentWorld, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Click to change", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        worldItem.setItemMeta(worldMeta);
        inventory.setItem(8, worldItem);
        
        // Legend (bottom row)
        renderLegend();
        
        // Back button (slot 49)
        ItemStack backItem = new ItemStack(Material.ARROW);
        var backMeta = backItem.getItemMeta();
        backMeta.displayName(Component.text("Back", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        inventory.setItem(49, backItem);
    }
    
    private void renderLegend() {
        // Show intensity legend in bottom row (slots 46-53 for 8 levels)
        int[] legendSlots = {46, 47, 48, 50, 51, 52, 53};
        String[] legendLabels = {"Low", "Low+", "Medium-", "Medium", "Medium+", "High", "Peak"};
        int[] materialIndices = {0, 1, 3, 4, 6, 8, 9};
        
        for (int i = 0; i < legendSlots.length; i++) {
            ItemStack item = new ItemStack(INTENSITY_MATERIALS[materialIndices[i]]);
            var meta = item.getItemMeta();
            meta.displayName(Component.text(legendLabels[i], NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            inventory.setItem(legendSlots[i], item);
        }
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        
        int slot = event.getRawSlot();
        
        if (slot == 0) {
            // Cycle activity type
            currentActivityIndex = (currentActivityIndex + 1) % ACTIVITY_TYPES.length;
            renderGrid();
            renderControls();
        } else if (slot == 8) {
            // Cycle world
            cycleWorld();
            renderGrid();
            renderControls();
        } else if (slot == 49) {
            // Back to player stats - find the target player online or close the GUI
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, targetPlayer));
            } else {
                // If target player is offline, just close the GUI
                player.closeInventory();
            }
        }
    }
    
    private void cycleWorld() {
        List<World> worlds = Bukkit.getWorlds();
        int currentIndex = -1;
        for (int i = 0; i < worlds.size(); i++) {
            if (worlds.get(i).getName().equals(currentWorld)) {
                currentIndex = i;
                break;
            }
        }
        
        if (currentIndex >= 0) {
            currentIndex = (currentIndex + 1) % worlds.size();
            currentWorld = worlds.get(currentIndex).getName();
        } else if (!worlds.isEmpty()) {
            currentWorld = worlds.get(0).getName();
        }
    }
}

