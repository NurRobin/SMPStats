package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.HealthSnapshot;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

public class HotChunksGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final ServerHealthService healthService;
    private final Inventory inventory;
    
    // Track pending teleport confirmations: playerUUID -> chunk slot (instance variable for thread safety)
    private final Map<UUID, Integer> pendingTeleports = new HashMap<>();

    public HotChunksGui(SMPStats plugin, GuiManager guiManager, ServerHealthService healthService) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.healthService = healthService;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Hot Chunks", NamedTextColor.DARK_RED));
        initializeItems();
    }

    private void initializeItems() {
        HealthSnapshot snapshot = healthService.getLatest();
        if (snapshot == null || snapshot.hotChunks() == null || snapshot.hotChunks().isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, Component.text("No hot chunks found", NamedTextColor.RED)));
            inventory.setItem(49, createGuiItem(Material.ARROW, Component.text("Back", NamedTextColor.RED)));
            return;
        }

        List<HealthSnapshot.HotChunk> chunks = snapshot.hotChunks();
        for (int i = 0; i < chunks.size() && i < 45; i++) {
            HealthSnapshot.HotChunk chunk = chunks.get(i);
            inventory.setItem(i, createGuiItem(Material.MAGMA_BLOCK, 
                    Component.text("Chunk: " + chunk.x() + ", " + chunk.z(), NamedTextColor.GOLD),
                    Component.text("World: " + chunk.world(), NamedTextColor.GRAY),
                    Component.text("Entities: " + chunk.entityCount(), NamedTextColor.RED),
                    Component.text("Tile Entities: " + chunk.tileEntityCount(), NamedTextColor.YELLOW),
                    Component.text("Top Owner: " + chunk.topOwner(), NamedTextColor.AQUA),
                    Component.text("Click to Teleport", NamedTextColor.DARK_GRAY)
            ));
        }

        // Back Button
        inventory.setItem(49, createGuiItem(Material.ARROW, Component.text("Back", NamedTextColor.RED)));

        // Fill background
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inventory.getSize(); i++) {
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
        // Clear any pending teleport confirmation when opening
        pendingTeleports.remove(player.getUniqueId());
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        playClickSound(player);
        
        if (event.getSlot() == 49) {
            guiManager.openGui(player, new ServerHealthGui(plugin, guiManager, healthService));
            return;
        }
        
        HealthSnapshot snapshot = healthService.getLatest();
        if (snapshot != null && snapshot.hotChunks() != null && event.getSlot() < snapshot.hotChunks().size()) {
            int slot = event.getSlot();
            
            // Check if this is a confirmation click
            Integer pendingSlot = pendingTeleports.get(player.getUniqueId());
            if (pendingSlot != null && pendingSlot == slot) {
                // Confirmed - perform teleport
                performTeleport(player, snapshot.hotChunks().get(slot));
                pendingTeleports.remove(player.getUniqueId());
            } else {
                // First click - ask for confirmation
                HealthSnapshot.HotChunk chunk = snapshot.hotChunks().get(slot);
                pendingTeleports.put(player.getUniqueId(), slot);
                player.sendMessage(Component.text("Click again to teleport to chunk ", NamedTextColor.YELLOW)
                        .append(Component.text(chunk.x() + ", " + chunk.z(), NamedTextColor.GOLD))
                        .append(Component.text(" in " + chunk.world(), NamedTextColor.YELLOW)));
                
                // Update the item to show confirmation state
                inventory.setItem(slot, createGuiItem(Material.LIME_CONCRETE,
                        Component.text("Confirm Teleport", NamedTextColor.GREEN),
                        Component.text("Chunk: " + chunk.x() + ", " + chunk.z(), NamedTextColor.GOLD),
                        Component.text("Click again to confirm!", NamedTextColor.YELLOW)));
                
                // Schedule reset after 5 seconds
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pendingTeleports.remove(player.getUniqueId()) != null) {
                        // Only reset if the player is still viewing this GUI
                        if (player.getOpenInventory() != null
                                && player.getOpenInventory().getTopInventory() != null
                                && player.getOpenInventory().getTopInventory().getHolder() == this) {
                            // Fetch fresh snapshot data to avoid displaying stale information
                            HealthSnapshot latestSnapshot = healthService.getLatest();
                            if (latestSnapshot != null && latestSnapshot.hotChunks() != null 
                                    && slot < latestSnapshot.hotChunks().size()) {
                                HealthSnapshot.HotChunk latestChunk = latestSnapshot.hotChunks().get(slot);
                                inventory.setItem(slot, createGuiItem(Material.MAGMA_BLOCK,
                                        Component.text("Chunk: " + latestChunk.x() + ", " + latestChunk.z(), NamedTextColor.GOLD),
                                        Component.text("World: " + latestChunk.world(), NamedTextColor.GRAY),
                                        Component.text("Entities: " + latestChunk.entityCount(), NamedTextColor.RED),
                                        Component.text("Tile Entities: " + latestChunk.tileEntityCount(), NamedTextColor.YELLOW),
                                        Component.text("Top Owner: " + latestChunk.topOwner(), NamedTextColor.AQUA),
                                        Component.text("Click to Teleport", NamedTextColor.DARK_GRAY)));
                            } else {
                                // Chunk no longer in hot chunks list, show placeholder
                                inventory.setItem(slot, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
                            }
                        }
                    }
                }, 100L); // 5 seconds
            }
        }
    }
    
    private void performTeleport(Player player, HealthSnapshot.HotChunk chunk) {
        org.bukkit.World world = Bukkit.getWorld(chunk.world());
        if (world != null) {
            // Calculate safe teleport location at center of chunk
            int centerX = chunk.x() * 16 + 8;
            int centerZ = chunk.z() * 16 + 8;
            int safeY = world.getHighestBlockYAt(centerX, centerZ) + 1;
            
            Location teleportLocation = new Location(world, centerX + 0.5, safeY, centerZ + 0.5);
            player.teleport(teleportLocation);
            playSuccessSound(player);
            player.sendMessage(Component.text("Teleported to hot chunk.", NamedTextColor.GREEN));
            player.closeInventory();
        } else {
            playErrorSound(player);
            player.sendMessage(Component.text("World not found.", NamedTextColor.RED));
        }
    }
}
