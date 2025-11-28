package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI for selecting a player to compare against.
 * Shows online players first, then recently active offline players.
 */
public class PlayerSelectorGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final Player viewer;
    private final UUID compareWithPlayer;
    private final int page;
    private final Inventory inventory;
    
    private static final int PLAYERS_PER_PAGE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    
    private List<UUID> availablePlayers;

    public PlayerSelectorGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                             Player viewer, UUID compareWithPlayer) {
        this(plugin, guiManager, statsService, viewer, compareWithPlayer, 0);
    }

    public PlayerSelectorGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                             Player viewer, UUID compareWithPlayer, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.viewer = viewer;
        this.compareWithPlayer = compareWithPlayer;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("🎯 ", NamedTextColor.GOLD)
                        .append(Component.text("Select Player to Compare", NamedTextColor.WHITE)));
        loadPlayers();
        initializeItems();
    }
    
    private void loadPlayers() {
        // Get all players with stats, sorted by online first, then by last join time
        availablePlayers = statsService.getAllStats().stream()
                .map(record -> record.getUuid())
                .filter(uuid -> !uuid.equals(compareWithPlayer)) // Exclude the player we're comparing from
                .sorted((a, b) -> {
                    // Online players first
                    Player playerA = Bukkit.getPlayer(a);
                    Player playerB = Bukkit.getPlayer(b);
                    if (playerA != null && playerB == null) return -1;
                    if (playerA == null && playerB != null) return 1;
                    
                    // Then sort by name
                    String nameA = statsService.getStats(a).map(r -> r.getName()).orElse("Unknown");
                    String nameB = statsService.getStats(b).map(r -> r.getName()).orElse("Unknown");
                    return nameA.compareToIgnoreCase(nameB);
                })
                .collect(Collectors.toList());
    }

    private void initializeItems() {
        inventory.clear();
        
        if (availablePlayers.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER,
                    Component.text("No other players found", NamedTextColor.RED),
                    Component.text("There are no other players to compare with", NamedTextColor.GRAY)));
            addNavigationButtons();
            return;
        }
        
        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, availablePlayers.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            UUID playerUuid = availablePlayers.get(i);
            
            ItemStack head = createPlayerHeadItem(playerUuid);
            inventory.setItem(slot, head);
        }
        
        addNavigationButtons();
    }
    
    private ItemStack createPlayerHeadItem(UUID uuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(offlinePlayer);
            
            String name = statsService.getStats(uuid)
                    .map(r -> r.getName())
                    .orElse(offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
            
            boolean isOnline = Bukkit.getPlayer(uuid) != null;
            NamedTextColor nameColor = isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            
            meta.displayName(Component.text(name, nameColor).decorate(TextDecoration.BOLD));
            
            if (isOnline) {
                meta.lore(List.of(
                        Component.text("🟢 Online", NamedTextColor.GREEN),
                        Component.empty(),
                        Component.text("Click to compare!", NamedTextColor.YELLOW)));
            } else {
                meta.lore(List.of(
                        Component.text("⚫ Offline", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to compare!", NamedTextColor.YELLOW)));
            }
            
            head.setItemMeta(meta);
        }
        return head;
    }
    
    private void addNavigationButtons() {
        // Back button
        inventory.setItem(BACK_SLOT, createGuiItem(Material.ARROW,
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to previous screen", NamedTextColor.GRAY)));
        
        int totalPages = (int) Math.ceil((double) availablePlayers.size() / PLAYERS_PER_PAGE);
        
        // Previous page
        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, createGuiItem(Material.ARROW,
                    Component.text("◀ Previous Page", NamedTextColor.YELLOW)));
        }
        
        // Page info
        inventory.setItem(PAGE_INFO_SLOT, createGuiItem(Material.PAPER,
                Component.text("Page " + (page + 1) + "/" + Math.max(1, totalPages), NamedTextColor.WHITE),
                Component.text(availablePlayers.size() + " players available", NamedTextColor.GRAY)));
        
        // Next page
        if ((page + 1) * PLAYERS_PER_PAGE < availablePlayers.size()) {
            inventory.setItem(NEXT_PAGE_SLOT, createGuiItem(Material.ARROW,
                    Component.text("Next Page ▶", NamedTextColor.YELLOW)));
        }
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        playClickSound(player);
        
        int slot = event.getSlot();
        
        if (slot == BACK_SLOT) {
            // Go back to player stats
            player.closeInventory();
        } else if (slot == PREV_PAGE_SLOT && page > 0) {
            guiManager.openGui(player, new PlayerSelectorGui(plugin, guiManager, statsService,
                    viewer, compareWithPlayer, page - 1));
        } else if (slot == NEXT_PAGE_SLOT && (page + 1) * PLAYERS_PER_PAGE < availablePlayers.size()) {
            guiManager.openGui(player, new PlayerSelectorGui(plugin, guiManager, statsService,
                    viewer, compareWithPlayer, page + 1));
        } else if (slot < PLAYERS_PER_PAGE) {
            // Player head clicked
            int playerIndex = page * PLAYERS_PER_PAGE + slot;
            if (playerIndex < availablePlayers.size()) {
                UUID selectedPlayer = availablePlayers.get(playerIndex);
                playSuccessSound(player);
                guiManager.openGui(player, new ComparePlayersGui(plugin, guiManager, statsService,
                        viewer, compareWithPlayer, selectedPlayer));
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
