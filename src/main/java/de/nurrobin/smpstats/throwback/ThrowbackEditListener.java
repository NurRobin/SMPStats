package de.nurrobin.smpstats.throwback;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Bukkit events of the edit mode: the tool clicks, the locks that keep the tools in the hotbar, and the ways
 * out of a session. Tools are recognised by their tag only.
 */
public final class ThrowbackEditListener implements Listener {
    private final ThrowbackEditor editor;
    private final ThrowbackService service;

    public ThrowbackEditListener(ThrowbackEditor editor, ThrowbackService service) {
        this.editor = editor;
        this.service = service;
    }

    // ------------------------------------------------------------------ using the tools

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !editor.inSession(p.getUniqueId())) {
            return;
        }
        EditTool tool = editor.toolOf(event.getItem());
        if (tool == null) {
            return;
        }
        event.setCancelled(true);
        boolean command = tool == EditTool.UNDO || tool == EditTool.SAVE;
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK -> editor.use(p, tool, event.getClickedBlock(), event.getInteractionPoint());
            case RIGHT_CLICK_AIR -> {
                if (command) {
                    editor.use(p, tool, null, null);
                }
            }
            case LEFT_CLICK_BLOCK -> editor.remove(p, tool, event.getClickedBlock());
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreakWithTool(BlockBreakEvent event) {
        if (editor.inSession(event.getPlayer().getUniqueId()) && editor.isTool(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        editor.blockChanged(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        editor.blockChanged(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        if (editor.inSession(p.getUniqueId()) && editor.isTool(p.getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ keeping the tools where they are

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (editor.inSession(event.getPlayer().getUniqueId()) && editor.isTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !editor.inSession(p.getUniqueId())) {
            return;
        }
        boolean tool = editor.isTool(event.getCurrentItem()) || editor.isTool(event.getCursor());
        if (!tool && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            tool = editor.isTool(p.getInventory().getItem(event.getHotbarButton()));
        }
        if (!tool && event.getClick() == ClickType.SWAP_OFFHAND) {
            tool = editor.isTool(p.getInventory().getItemInOffHand());
        }
        if (tool) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !editor.inSession(p.getUniqueId())) {
            return;
        }
        if (editor.isTool(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(editor::isTool)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (editor.inSession(p.getUniqueId()) && (editor.isTool(event.getMainHandItem()) || editor.isTool(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && editor.inSession(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(service.texts().message("edit_no_container"));
        }
    }

    // ------------------------------------------------------------------ ways out

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        editor.onDeath(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editor.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        editor.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        editor.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        editor.onWorldChanged(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        editor.onTeleported(event.getPlayer());
    }
}
