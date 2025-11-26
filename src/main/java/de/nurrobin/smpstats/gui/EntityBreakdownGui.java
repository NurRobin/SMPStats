package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.EntityAnalysisService;
import de.nurrobin.smpstats.health.EntityAnalysisService.EntityTypeInfo;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI showing breakdown of all entity types with counts.
 * Clicking on an entity type opens detailed view or kills all of that type.
 */
public class EntityBreakdownGui implements InventoryGui, InventoryHolder {
    
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for entities
    
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final ServerHealthService healthService;
    private final EntityAnalysisService entityService;
    private final Inventory inventory;
    private int currentPage;
    private List<EntityTypeInfo> entityTypes;
    
    // Track pending kill confirmations: playerUUID -> entityType
    private final Map<UUID, EntityType> pendingKills = new HashMap<>();

    public EntityBreakdownGui(SMPStats plugin, GuiManager guiManager, ServerHealthService healthService, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.healthService = healthService;
        this.entityService = new EntityAnalysisService();
        this.currentPage = page;
        this.inventory = Bukkit.createInventory(this, 54, 
                Component.text("Entity Breakdown", NamedTextColor.RED));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        pendingKills.clear();
        
        // Get fresh entity data
        entityTypes = entityService.analyzeEntities();
        
        if (entityTypes.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, 
                    Component.text("No entities found", NamedTextColor.RED)));
            addNavigationButtons();
            return;
        }
        
        int totalEntities = entityTypes.stream().mapToInt(EntityTypeInfo::count).sum();
        int totalTypes = entityTypes.size();
        int totalPages = (int) Math.ceil((double) totalTypes / ITEMS_PER_PAGE);
        
        // Ensure current page is valid
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalTypes);
        
        for (int i = startIndex; i < endIndex; i++) {
            EntityTypeInfo info = entityTypes.get(i);
            int slot = i - startIndex;
            
            Material mat = EntityHeadUtils.getMaterial(info.type());
            String name = EntityHeadUtils.formatName(info.type());
            
            // Color based on count
            NamedTextColor countColor;
            if (info.count() > 500) {
                countColor = NamedTextColor.RED;
            } else if (info.count() > 100) {
                countColor = NamedTextColor.YELLOW;
            } else {
                countColor = NamedTextColor.GREEN;
            }
            
            inventory.setItem(slot, createGuiItem(mat,
                    Component.text(name, NamedTextColor.WHITE),
                    Component.text("Count: " + info.count(), countColor),
                    Component.text("", NamedTextColor.GRAY),
                    Component.text("Left-click: View details", NamedTextColor.GRAY),
                    Component.text("Shift+Right-click: Kill all", NamedTextColor.DARK_RED)));
        }
        
        // Summary item in middle of bottom navigation
        inventory.setItem(49, createGuiItem(Material.BOOK,
                Component.text("Summary", NamedTextColor.GOLD),
                Component.text("Total Entities: " + totalEntities, NamedTextColor.WHITE),
                Component.text("Entity Types: " + totalTypes, NamedTextColor.GRAY),
                Component.text("Page " + (currentPage + 1) + "/" + totalPages, NamedTextColor.DARK_GRAY)));
        
        addNavigationButtons();
    }

    private void addNavigationButtons() {
        int totalPages = (int) Math.ceil((double) entityTypes.size() / ITEMS_PER_PAGE);
        
        // Back button
        inventory.setItem(45, createGuiItem(Material.ARROW, 
                Component.text("Back", NamedTextColor.RED)));
        
        // Previous page
        if (currentPage > 0) {
            inventory.setItem(48, createGuiItem(Material.ARROW, 
                    Component.text("Previous Page", NamedTextColor.YELLOW)));
        }
        
        // Next page
        if (currentPage < totalPages - 1) {
            inventory.setItem(50, createGuiItem(Material.ARROW, 
                    Component.text("Next Page", NamedTextColor.YELLOW)));
        }
        
        // Refresh button
        inventory.setItem(53, createGuiItem(Material.SUNFLOWER, 
                Component.text("Refresh", NamedTextColor.GREEN),
                Component.text("Click to refresh data", NamedTextColor.GRAY)));
        
        // Fill background
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 45; i < 54; i++) {
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
        initializeItems();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        playClickSound(player);
        
        // Navigation buttons
        if (slot == 45) {
            // Back to server health
            guiManager.openGui(player, new ServerHealthGui(plugin, guiManager, healthService));
            return;
        } else if (slot == 48 && currentPage > 0) {
            currentPage--;
            initializeItems();
            return;
        } else if (slot == 50) {
            int totalPages = (int) Math.ceil((double) entityTypes.size() / ITEMS_PER_PAGE);
            if (currentPage < totalPages - 1) {
                currentPage++;
                initializeItems();
            }
            return;
        } else if (slot == 53) {
            playSuccessSound(player);
            initializeItems();
            player.sendMessage(Component.text("Entity data refreshed!", NamedTextColor.GREEN));
            return;
        }
        
        // Entity type clicks (slots 0-44)
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int index = currentPage * ITEMS_PER_PAGE + slot;
            if (index < entityTypes.size()) {
                EntityTypeInfo info = entityTypes.get(index);
                
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    // Kill all - check for confirmation
                    if (!player.hasPermission("smpstats.admin")) {
                        playErrorSound(player);
                        player.sendMessage(Component.text("You need smpstats.admin permission to kill entities.", NamedTextColor.RED));
                        return;
                    }
                    
                    EntityType pendingType = pendingKills.get(player.getUniqueId());
                    if (pendingType == info.type()) {
                        // Confirmed - kill all
                        int killed = entityService.killAllOfType(info.type());
                        playSuccessSound(player);
                        player.sendMessage(Component.text("Killed " + killed + " " + EntityHeadUtils.formatName(info.type()) + " entities!", NamedTextColor.GREEN));
                        pendingKills.remove(player.getUniqueId());
                        initializeItems();
                    } else {
                        // First click - ask for confirmation
                        pendingKills.put(player.getUniqueId(), info.type());
                        player.sendMessage(Component.text("Shift+Right-click again to confirm killing all " + info.count() + " " + EntityHeadUtils.formatName(info.type()) + " entities!", NamedTextColor.YELLOW));
                        
                        // Update item to show confirmation state
                        Material mat = EntityHeadUtils.getMaterial(info.type());
                        inventory.setItem(slot, createGuiItem(Material.TNT,
                                Component.text("CONFIRM KILL ALL", NamedTextColor.DARK_RED),
                                Component.text(EntityHeadUtils.formatName(info.type()), NamedTextColor.WHITE),
                                Component.text("Count: " + info.count(), NamedTextColor.RED),
                                Component.text("Shift+Right-click to confirm!", NamedTextColor.YELLOW)));
                        
                        // Reset after 5 seconds
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (pendingKills.remove(player.getUniqueId()) != null) {
                                if (player.getOpenInventory().getTopInventory().getHolder() == this) {
                                    initializeItems();
                                }
                            }
                        }, 100L);
                    }
                } else {
                    // Left click - open detailed view
                    guiManager.openGui(player, new EntityDetailGui(plugin, guiManager, healthService, entityService, info.type(), 0));
                }
            }
        }
    }
}
