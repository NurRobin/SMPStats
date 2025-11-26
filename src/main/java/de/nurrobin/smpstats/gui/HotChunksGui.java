package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.HealthSnapshot;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class HotChunksGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final ServerHealthService healthService;
    private final Inventory inventory;

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

    private ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getSlot() == 49) {
            guiManager.openGui((Player) event.getWhoClicked(), new ServerHealthGui(plugin, guiManager, healthService));
            return;
        }
        
        HealthSnapshot snapshot = healthService.getLatest();
        if (snapshot != null && snapshot.hotChunks() != null && event.getSlot() < snapshot.hotChunks().size()) {
            HealthSnapshot.HotChunk chunk = snapshot.hotChunks().get(event.getSlot());
            Player player = (Player) event.getWhoClicked();
            org.bukkit.World world = Bukkit.getWorld(chunk.world());
            if (world != null) {
                // Teleport to center of chunk
                player.teleport(world.getBlockAt(chunk.x() * 16 + 8, 100, chunk.z() * 16 + 8).getLocation());
                player.sendMessage(Component.text("Teleported to hot chunk.", NamedTextColor.GREEN));
                player.closeInventory();
            } else {
                player.sendMessage(Component.text("World not found.", NamedTextColor.RED));
            }
        }
    }
}
