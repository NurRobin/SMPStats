package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.EntityAnalysisService;
import de.nurrobin.smpstats.health.EntityAnalysisService.EntityInstance;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
 * GUI showing individual entities of a specific type.
 * Allows teleporting to entities or killing individual entities.
 */
public class EntityDetailGui implements InventoryGui, InventoryHolder {
    
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for entities
    
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final ServerHealthService healthService;
    private final EntityAnalysisService entityService;
    private final EntityType entityType;
    private final Inventory inventory;
    private int currentPage;
    private List<EntityInstance> entities;
    
    // Track pending actions: playerUUID -> slot
    private final Map<UUID, Integer> pendingTeleports = new HashMap<>();
    private final Map<UUID, Integer> pendingKills = new HashMap<>();

    public EntityDetailGui(SMPStats plugin, GuiManager guiManager, ServerHealthService healthService,
                           EntityAnalysisService entityService, EntityType entityType, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.healthService = healthService;
        this.entityService = entityService;
        this.entityType = entityType;
        this.currentPage = page;
        
        String typeName = EntityHeadUtils.formatName(entityType);
        this.inventory = Bukkit.createInventory(this, 54, 
                Component.text(typeName + " Entities", NamedTextColor.RED));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        pendingTeleports.clear();
        pendingKills.clear();
        
        // Get fresh entity data for this type
        entities = entityService.getEntitiesOfType(entityType);
        
        if (entities.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, 
                    Component.text("No " + EntityHeadUtils.formatName(entityType) + " found", NamedTextColor.RED)));
            addNavigationButtons();
            return;
        }
        
        int totalEntities = entities.size();
        int totalPages = (int) Math.ceil((double) totalEntities / ITEMS_PER_PAGE);
        
        // Ensure current page is valid
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalEntities);
        
        Material mat = EntityHeadUtils.getMaterial(entityType);
        
        for (int i = startIndex; i < endIndex; i++) {
            EntityInstance entity = entities.get(i);
            int slot = i - startIndex;
            
            String locationStr = String.format("%.0f, %.0f, %.0f", entity.x(), entity.y(), entity.z());
            
            Component nameComponent;
            if (entity.customName() != null && !entity.customName().isEmpty()) {
                nameComponent = Component.text(entity.customName(), NamedTextColor.AQUA);
            } else {
                nameComponent = Component.text(EntityHeadUtils.formatName(entityType) + " #" + (i + 1), NamedTextColor.WHITE);
            }
            
            inventory.setItem(slot, createGuiItem(mat,
                    nameComponent,
                    Component.text("World: " + entity.worldName(), NamedTextColor.GRAY),
                    Component.text("Location: " + locationStr, NamedTextColor.GRAY),
                    Component.text("", NamedTextColor.GRAY),
                    Component.text("Left-click: Teleport", NamedTextColor.GREEN),
                    Component.text("Shift+Right-click: Kill", NamedTextColor.RED)));
        }
        
        addNavigationButtons();
    }

    private void addNavigationButtons() {
        int totalPages = Math.max(1, (int) Math.ceil((double) entities.size() / ITEMS_PER_PAGE));
        
        // Back button
        inventory.setItem(45, createGuiItem(Material.ARROW, 
                Component.text("Back", NamedTextColor.RED)));
        
        // Previous page
        if (currentPage > 0) {
            inventory.setItem(47, createGuiItem(Material.ARROW, 
                    Component.text("Previous Page", NamedTextColor.YELLOW)));
        }
        
        // Summary/info
        inventory.setItem(49, createGuiItem(EntityHeadUtils.getMaterial(entityType),
                Component.text(EntityHeadUtils.formatName(entityType), NamedTextColor.GOLD),
                Component.text("Total: " + entities.size(), NamedTextColor.WHITE),
                Component.text("Page " + (currentPage + 1) + "/" + totalPages, NamedTextColor.DARK_GRAY)));
        
        // Next page
        if (currentPage < totalPages - 1) {
            inventory.setItem(51, createGuiItem(Material.ARROW, 
                    Component.text("Next Page", NamedTextColor.YELLOW)));
        }
        
        // Refresh button
        inventory.setItem(53, createGuiItem(Material.SUNFLOWER, 
                Component.text("Refresh", NamedTextColor.GREEN),
                Component.text("Click to refresh data", NamedTextColor.GRAY)));
        
        // Kill all button (with confirmation needed)
        inventory.setItem(46, createGuiItem(Material.TNT,
                Component.text("Kill All " + EntityHeadUtils.formatName(entityType), NamedTextColor.DARK_RED),
                Component.text("Entities: " + entities.size(), NamedTextColor.RED),
                Component.text("Shift+Right-click to use", NamedTextColor.GRAY)));
        
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
            // Back to entity breakdown
            guiManager.openGui(player, new EntityBreakdownGui(plugin, guiManager, healthService, 0));
            return;
        } else if (slot == 47 && currentPage > 0) {
            currentPage--;
            initializeItems();
            return;
        } else if (slot == 51) {
            int totalPages = (int) Math.ceil((double) entities.size() / ITEMS_PER_PAGE);
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
        } else if (slot == 46 && event.getClick() == ClickType.SHIFT_RIGHT) {
            // Kill all of this type
            if (!player.hasPermission("smpstats.admin")) {
                playErrorSound(player);
                player.sendMessage(Component.text("You need smpstats.admin permission to kill entities.", NamedTextColor.RED));
                return;
            }
            
            int killed = entityService.killAllOfType(entityType);
            playSuccessSound(player);
            player.sendMessage(Component.text("Killed " + killed + " " + EntityHeadUtils.formatName(entityType) + " entities!", NamedTextColor.GREEN));
            initializeItems();
            return;
        }
        
        // Entity clicks (slots 0-44)
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int index = currentPage * ITEMS_PER_PAGE + slot;
            if (index < entities.size()) {
                EntityInstance entity = entities.get(index);
                
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    // Kill this entity
                    if (!player.hasPermission("smpstats.admin")) {
                        playErrorSound(player);
                        player.sendMessage(Component.text("You need smpstats.admin permission to kill entities.", NamedTextColor.RED));
                        return;
                    }
                    
                    Integer pendingSlot = pendingKills.get(player.getUniqueId());
                    if (pendingSlot != null && pendingSlot == slot) {
                        // Confirmed - kill
                        if (entityService.killEntity(entity.entityId(), entity.worldName())) {
                            playSuccessSound(player);
                            player.sendMessage(Component.text("Entity killed!", NamedTextColor.GREEN));
                        } else {
                            playErrorSound(player);
                            player.sendMessage(Component.text("Entity not found (may have despawned).", NamedTextColor.RED));
                        }
                        pendingKills.remove(player.getUniqueId());
                        initializeItems();
                    } else {
                        // First click - ask for confirmation
                        pendingKills.put(player.getUniqueId(), slot);
                        player.sendMessage(Component.text("Shift+Right-click again to confirm kill!", NamedTextColor.YELLOW));
                        
                        inventory.setItem(slot, createGuiItem(Material.BARRIER,
                                Component.text("CONFIRM KILL", NamedTextColor.DARK_RED),
                                Component.text("Shift+Right-click to confirm", NamedTextColor.YELLOW)));
                        
                        // Reset after 3 seconds
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (pendingKills.remove(player.getUniqueId()) != null) {
                                if (player.getOpenInventory().getTopInventory().getHolder() == this) {
                                    initializeItems();
                                }
                            }
                        }, 60L);
                    }
                } else if (event.getClick().isLeftClick()) {
                    // Teleport to entity
                    if (!player.hasPermission("smpstats.admin")) {
                        playErrorSound(player);
                        player.sendMessage(Component.text("You need smpstats.admin permission to teleport.", NamedTextColor.RED));
                        return;
                    }
                    
                    Integer pendingSlot = pendingTeleports.get(player.getUniqueId());
                    if (pendingSlot != null && pendingSlot == slot) {
                        // Confirmed - teleport
                        Location loc = entity.getLocation();
                        if (loc != null) {
                            player.teleport(loc);
                            playSuccessSound(player);
                            player.closeInventory();
                            player.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
                        } else {
                            playErrorSound(player);
                            player.sendMessage(Component.text("World not found.", NamedTextColor.RED));
                        }
                        pendingTeleports.remove(player.getUniqueId());
                    } else {
                        // First click - ask for confirmation
                        pendingTeleports.put(player.getUniqueId(), slot);
                        String locStr = String.format("%.0f, %.0f, %.0f in %s", 
                                entity.x(), entity.y(), entity.z(), entity.worldName());
                        player.sendMessage(Component.text("Click again to teleport to " + locStr, NamedTextColor.YELLOW));
                        
                        inventory.setItem(slot, createGuiItem(Material.ENDER_PEARL,
                                Component.text("CONFIRM TELEPORT", NamedTextColor.GREEN),
                                Component.text("Click again to confirm", NamedTextColor.YELLOW)));
                        
                        // Reset after 3 seconds
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (pendingTeleports.remove(player.getUniqueId()) != null) {
                                if (player.getOpenInventory().getTopInventory().getHolder() == this) {
                                    initializeItems();
                                }
                            }
                        }, 60L);
                    }
                }
            }
        }
    }
}
