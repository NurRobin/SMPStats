package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
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

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * Admin GUI for searching and viewing any player's stats.
 * Requires smpstats.admin permission to use.
 */
public class AdminPlayerLookupGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final StatsStorage storage;
    private final Player viewer;
    private final Inventory inventory;
    
    private int currentPage = 0;
    private String searchFilter = "";
    private SortMode sortMode = SortMode.PLAYTIME;
    private List<StatsRecord> allPlayers = new ArrayList<>();
    
    private static final int PLAYERS_PER_PAGE = 28; // 4 rows of 7
    private static final int[] PLAYER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    
    public enum SortMode {
        PLAYTIME("⏱ Playtime", NamedTextColor.GOLD),
        NAME("📛 Name", NamedTextColor.GREEN),
        LAST_SEEN("📅 Last Seen", NamedTextColor.AQUA),
        KILLS("⚔ Kills", NamedTextColor.RED),
        DEATHS("💀 Deaths", NamedTextColor.DARK_RED);
        
        private final String displayName;
        private final NamedTextColor color;
        
        SortMode(String displayName, NamedTextColor color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() { return displayName; }
        public NamedTextColor getColor() { return color; }
    }

    public AdminPlayerLookupGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                                 StatsStorage storage, Player viewer) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.storage = storage;
        this.viewer = viewer;
        
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("🔍 ", NamedTextColor.GOLD)
                        .append(Component.text("Admin: Player Lookup", NamedTextColor.WHITE)));
        loadAllPlayers();
        initializeItems();
    }
    
    private void loadAllPlayers() {
        try {
            allPlayers = storage.loadAll();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load player stats for admin lookup: " + e.getMessage());
            allPlayers = new ArrayList<>();
        }
    }

    private void initializeItems() {
        inventory.clear();
        
        // Header with info
        int totalPlayers = allPlayers.size();
        inventory.setItem(4, createGuiItem(Material.COMMAND_BLOCK,
                Component.text("🔍 Player Lookup", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Search and view any player's stats", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Total Players: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(String.valueOf(totalPlayers), NamedTextColor.WHITE)),
                Component.text("Current Filter: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(searchFilter.isEmpty() ? "None" : searchFilter, NamedTextColor.YELLOW))));
        
        // Sort mode button (slot 0)
        addSortModeButton();
        
        // Search button (slot 8)
        inventory.setItem(8, createGuiItem(Material.OAK_SIGN,
                Component.text("🔍 Search", NamedTextColor.YELLOW),
                Component.text("Type in chat to search", NamedTextColor.GRAY),
                Component.text("Current: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(searchFilter.isEmpty() ? "All players" : searchFilter, NamedTextColor.WHITE)),
                Component.empty(),
                Component.text("Click to clear filter", NamedTextColor.RED)));
        
        // Filter and sort players
        List<StatsRecord> filteredPlayers = filterAndSortPlayers();
        
        // Calculate pagination
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredPlayers.size() / PLAYERS_PER_PAGE));
        currentPage = Math.min(currentPage, totalPages - 1);
        
        int startIndex = currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, filteredPlayers.size());
        
        // Display players
        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            if (slotIndex < PLAYER_SLOTS.length) {
                StatsRecord record = filteredPlayers.get(i);
                inventory.setItem(PLAYER_SLOTS[slotIndex], createPlayerItem(record));
            }
        }
        
        addNavigationButtons(filteredPlayers.size(), totalPages);
    }
    
    private void addSortModeButton() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to change sort order", NamedTextColor.GRAY));
        lore.add(Component.empty());
        
        for (SortMode mode : SortMode.values()) {
            String prefix = mode == sortMode ? "▶ " : "  ";
            NamedTextColor color = mode == sortMode ? mode.color : NamedTextColor.DARK_GRAY;
            lore.add(Component.text(prefix + mode.getDisplayName(), color));
        }
        
        inventory.setItem(0, createGuiItem(Material.HOPPER,
                Component.text("📊 Sort By: " + sortMode.getDisplayName(), sortMode.color).decorate(TextDecoration.BOLD),
                lore.toArray(new Component[0])));
    }
    
    private List<StatsRecord> filterAndSortPlayers() {
        List<StatsRecord> filtered = allPlayers.stream()
                .filter(r -> {
                    if (searchFilter.isEmpty()) return true;
                    String name = r.getName();
                    return name != null && name.toLowerCase().contains(searchFilter.toLowerCase());
                })
                .collect(Collectors.toList());
        
        // Sort based on current mode
        Comparator<StatsRecord> comparator = switch (sortMode) {
            case PLAYTIME -> Comparator.comparingLong(StatsRecord::getPlaytimeMillis).reversed();
            case NAME -> Comparator.comparing((StatsRecord r) -> r.getName() != null ? r.getName().toLowerCase() : "");
            case LAST_SEEN -> Comparator.comparingLong(StatsRecord::getLastJoin).reversed();
            case KILLS -> Comparator.comparingLong((StatsRecord r) -> r.getMobKills() + r.getPlayerKills()).reversed();
            case DEATHS -> Comparator.comparingLong(StatsRecord::getDeaths).reversed();
        };
        
        filtered.sort(comparator);
        return filtered;
    }
    
    private ItemStack createPlayerItem(StatsRecord record) {
        String playerName = record.getName() != null ? record.getName() : "Unknown";
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(record.getUuid());
        boolean isOnline = offlinePlayer.isOnline();
        
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            
            NamedTextColor nameColor = isOnline ? NamedTextColor.GREEN : NamedTextColor.WHITE;
            String statusIcon = isOnline ? "🟢 " : "⚫ ";
            meta.displayName(Component.text(statusIcon + playerName, nameColor).decorate(TextDecoration.BOLD));
            
            List<Component> lore = new ArrayList<>();
            
            // Status
            lore.add(Component.text(isOnline ? "Online" : "Offline", isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            lore.add(Component.empty());
            
            // Key stats
            long hours = TimeUnit.MILLISECONDS.toHours(record.getPlaytimeMillis());
            lore.add(Component.text("⏱ Playtime: ", NamedTextColor.GOLD)
                    .append(Component.text(hours + " hours", NamedTextColor.WHITE)));
            
            long totalKills = record.getMobKills() + record.getPlayerKills();
            lore.add(Component.text("⚔ Kills: ", NamedTextColor.RED)
                    .append(Component.text(String.valueOf(totalKills), NamedTextColor.WHITE)));
            
            lore.add(Component.text("💀 Deaths: ", NamedTextColor.DARK_RED)
                    .append(Component.text(String.valueOf(record.getDeaths()), NamedTextColor.WHITE)));
            
            // Last seen
            lore.add(Component.empty());
            if (isOnline) {
                lore.add(Component.text("Currently playing", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("Last seen: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(formatRelativeTime(record.getLastJoin()), NamedTextColor.GRAY)));
            }
            
            lore.add(Component.empty());
            lore.add(Component.text("Click to view full stats", NamedTextColor.YELLOW));
            
            meta.lore(lore);
            skull.setItemMeta(meta);
        }
        
        return skull;
    }
    
    private String formatRelativeTime(long timestamp) {
        if (timestamp <= 0) return "Unknown";
        
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 30) return (days / 30) + " months ago";
        if (days > 0) return days + " days ago";
        if (hours > 0) return hours + " hours ago";
        if (minutes > 0) return minutes + " minutes ago";
        return "Just now";
    }
    
    private void addNavigationButtons(int totalFiltered, int totalPages) {
        // Back button (slot 45)
        inventory.setItem(45, createGuiItem(Material.ARROW,
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to main menu", NamedTextColor.GRAY)));
        
        // Online filter (slot 46)
        long onlineCount = allPlayers.stream()
                .filter(r -> Bukkit.getOfflinePlayer(r.getUuid()).isOnline())
                .count();
        inventory.setItem(46, createGuiItem(Material.ENDER_EYE,
                Component.text("🟢 Online Only", NamedTextColor.GREEN),
                Component.text("Show only online players", NamedTextColor.GRAY),
                Component.text(onlineCount + " players online", NamedTextColor.YELLOW)));
        
        // Previous page (slot 48)
        if (currentPage > 0) {
            inventory.setItem(48, createGuiItem(Material.SPECTRAL_ARROW,
                    Component.text("◀ Previous", NamedTextColor.YELLOW),
                    Component.text("Go to page " + currentPage, NamedTextColor.GRAY)));
        }
        
        // Page indicator (slot 49)
        inventory.setItem(49, createGuiItem(Material.PAPER,
                Component.text("Page " + (currentPage + 1) + "/" + totalPages, NamedTextColor.WHITE),
                Component.text(totalFiltered + " players found", NamedTextColor.GRAY)));
        
        // Next page (slot 50)
        if (currentPage < totalPages - 1) {
            inventory.setItem(50, createGuiItem(Material.SPECTRAL_ARROW,
                    Component.text("Next ▶", NamedTextColor.YELLOW),
                    Component.text("Go to page " + (currentPage + 2), NamedTextColor.GRAY)));
        }
        
        // Refresh (slot 53)
        inventory.setItem(53, createGuiItem(Material.SUNFLOWER,
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Reload player data", NamedTextColor.GRAY)));
        
        // Fill background
        ItemStack filler = createBorderItem(Material.BLACK_STAINED_GLASS_PANE);
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
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        playClickSound(player);
        
        // Back button
        if (slot == 45) {
            plugin.getServerHealthService().ifPresentOrElse(
                    healthService -> guiManager.openGui(player, new MainMenuGui(plugin, guiManager, statsService, healthService)),
                    () -> player.closeInventory()
            );
            return;
        }
        
        // Sort mode button
        if (slot == 0) {
            SortMode[] modes = SortMode.values();
            int nextIndex = (sortMode.ordinal() + 1) % modes.length;
            sortMode = modes[nextIndex];
            currentPage = 0;
            playSuccessSound(player);
            initializeItems();
            return;
        }
        
        // Search button (clear filter)
        if (slot == 8) {
            if (!searchFilter.isEmpty()) {
                searchFilter = "";
                currentPage = 0;
                playSuccessSound(player);
                initializeItems();
            }
            return;
        }
        
        // Online filter
        if (slot == 46) {
            // Toggle to show only online players
            player.sendMessage(Component.text("Use /sstats lookup <name> to search for a player", NamedTextColor.YELLOW));
            return;
        }
        
        // Previous page
        if (slot == 48 && currentPage > 0) {
            currentPage--;
            initializeItems();
            return;
        }
        
        // Next page
        if (slot == 50) {
            currentPage++;
            initializeItems();
            return;
        }
        
        // Refresh
        if (slot == 53) {
            loadAllPlayers();
            playSuccessSound(player);
            initializeItems();
            return;
        }
        
        // Player click - check if it's a player slot
        for (int i = 0; i < PLAYER_SLOTS.length; i++) {
            if (slot == PLAYER_SLOTS[i]) {
                List<StatsRecord> filteredPlayers = filterAndSortPlayers();
                int playerIndex = currentPage * PLAYERS_PER_PAGE + i;
                
                if (playerIndex < filteredPlayers.size()) {
                    StatsRecord clickedRecord = filteredPlayers.get(playerIndex);
                    openPlayerStats(player, clickedRecord);
                }
                return;
            }
        }
    }
    
    private void openPlayerStats(Player viewer, StatsRecord record) {
        // Try to get the player - if online, use the player directly
        Player onlinePlayer = Bukkit.getPlayer(record.getUuid());
        if (onlinePlayer != null) {
            guiManager.openGui(viewer, new PlayerStatsGui(plugin, guiManager, statsService, onlinePlayer));
        } else {
            // For offline players, we need a different approach
            // For now, show a message - in future could create an OfflinePlayerStatsGui
            viewer.sendMessage(Component.text("Viewing offline player: " + record.getName(), NamedTextColor.YELLOW));
            viewer.sendMessage(Component.text("Playtime: ", NamedTextColor.GRAY)
                    .append(Component.text(TimeUnit.MILLISECONDS.toHours(record.getPlaytimeMillis()) + " hours", NamedTextColor.WHITE)));
            viewer.sendMessage(Component.text("Kills: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(record.getMobKills() + record.getPlayerKills()), NamedTextColor.WHITE)));
            viewer.sendMessage(Component.text("Deaths: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(record.getDeaths()), NamedTextColor.WHITE)));
        }
    }
    
    /**
     * Sets the search filter. Called externally when player types in chat.
     * @param filter The search filter to apply
     */
    public void setSearchFilter(String filter) {
        this.searchFilter = filter != null ? filter : "";
        this.currentPage = 0;
        initializeItems();
    }
}
