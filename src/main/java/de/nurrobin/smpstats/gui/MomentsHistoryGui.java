package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.moments.MomentEntry;
import de.nurrobin.smpstats.moments.MomentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI for viewing a player's achievement badges and moments history.
 * Shows triggered moments/achievements with timestamps and details.
 */
public class MomentsHistoryGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final MomentService momentService;
    private final Player viewer;
    private final UUID targetPlayerUuid;
    private final Inventory inventory;
    private final int page;
    
    /** Maximum moments shown per page */
    private static final int MOMENTS_PER_PAGE = 21;
    /** Maximum moments to load */
    private static final int MAX_MOMENTS = 100;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");
    
    // Layout slots
    private static final int BACK_SLOT = 45;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int STATS_SLOT = 52;
    private static final int REFRESH_SLOT = 53;
    
    // Moment entry slots (7x3 grid)
    private static final int[] MOMENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    public MomentsHistoryGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                             MomentService momentService, Player viewer, UUID targetPlayerUuid) {
        this(plugin, guiManager, statsService, momentService, viewer, targetPlayerUuid, 0);
    }

    public MomentsHistoryGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                             MomentService momentService, Player viewer, UUID targetPlayerUuid, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.momentService = momentService;
        this.viewer = viewer;
        this.targetPlayerUuid = targetPlayerUuid;
        this.page = page;
        
        String targetName = statsService.getStats(targetPlayerUuid)
                .map(r -> r.getName())
                .orElse("Player");
        
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("🏆 ", NamedTextColor.GOLD)
                        .append(Component.text(targetName + "'s Achievements", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        fillBackground();
        
        // Load moments for the player
        List<MomentEntry> moments = momentService.queryMoments(targetPlayerUuid, null, 0, MAX_MOMENTS);
        
        // Header with summary
        String targetName = statsService.getStats(targetPlayerUuid)
                .map(r -> r.getName())
                .orElse("Player");
        
        // Count moment types for stats
        Map<String, Integer> typeCounts = new HashMap<>();
        for (MomentEntry moment : moments) {
            typeCounts.merge(moment.getType(), 1, Integer::sum);
        }
        
        List<Component> headerLore = new ArrayList<>();
        headerLore.add(Component.text(targetName + "'s achievements", NamedTextColor.GRAY));
        headerLore.add(Component.empty());
        headerLore.add(Component.text("Total: " + moments.size() + " achievements", NamedTextColor.DARK_GRAY));
        
        if (!typeCounts.isEmpty()) {
            headerLore.add(Component.empty());
            headerLore.add(Component.text("Categories:", NamedTextColor.YELLOW));
            typeCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(entry -> {
                        Material icon = getMomentTypeIcon(entry.getKey());
                        headerLore.add(Component.text("  • " + formatTypeName(entry.getKey()) + ": " + entry.getValue(), 
                                NamedTextColor.WHITE));
                    });
        }
        
        inventory.setItem(4, createGuiItem(Material.NETHER_STAR,
                Component.text("Achievement Badges", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                headerLore.toArray(new Component[0])));
        
        if (moments.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER,
                    Component.text("No achievements yet!", NamedTextColor.GRAY),
                    Component.text("Complete tasks to earn badges", NamedTextColor.DARK_GRAY),
                    Component.empty(),
                    Component.text("Examples:", NamedTextColor.YELLOW),
                    Component.text("• Mine diamond ore", NamedTextColor.WHITE),
                    Component.text("• Kill a boss", NamedTextColor.WHITE),
                    Component.text("• Survive a big fall", NamedTextColor.WHITE)));
            addNavigationButtons(0, 0);
            return;
        }
        
        // Calculate pagination
        int totalPages = (int) Math.ceil(moments.size() / (double) MOMENTS_PER_PAGE);
        int startIndex = page * MOMENTS_PER_PAGE;
        int endIndex = Math.min(startIndex + MOMENTS_PER_PAGE, moments.size());
        
        // Display moments
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < MOMENT_SLOTS.length; i++) {
            MomentEntry moment = moments.get(i);
            int momentNumber = moments.size() - i; // Reverse numbering
            
            ItemStack momentItem = createMomentItem(moment, momentNumber);
            inventory.setItem(MOMENT_SLOTS[slotIndex], momentItem);
            slotIndex++;
        }
        
        addNavigationButtons(totalPages, moments.size());
    }
    
    private ItemStack createMomentItem(MomentEntry moment, int momentNumber) {
        Material icon = getMomentTypeIcon(moment.getType());
        NamedTextColor titleColor = getMomentTypeColor(moment.getType());
        
        List<Component> lore = new ArrayList<>();
        
        // Timestamp
        lore.add(Component.text(formatTimestamp(moment.getEndedAt()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        
        // Detail/description
        if (moment.getDetail() != null && !moment.getDetail().isEmpty()) {
            lore.add(Component.text("📜 " + moment.getDetail(), NamedTextColor.WHITE));
        }
        
        // Location
        if (moment.getWorld() != null && !moment.getWorld().equals("unknown")) {
            lore.add(Component.empty());
            lore.add(Component.text("📍 Location: ", NamedTextColor.AQUA)
                    .append(Component.text(moment.getWorld() + " (" + moment.getX() + ", " + moment.getY() + ", " + moment.getZ() + ")", NamedTextColor.WHITE)));
        }
        
        // Duration (if moment had a window)
        long duration = moment.getEndedAt() - moment.getStartedAt();
        if (duration > 1000) { // More than 1 second
            lore.add(Component.text("⏱ Duration: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(formatDuration(duration), NamedTextColor.WHITE)));
        }
        
        // Category badge
        lore.add(Component.empty());
        lore.add(Component.text("🏷 Category: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(formatTypeName(moment.getType()), titleColor)));
        
        String title = moment.getTitle() != null ? moment.getTitle() : formatTypeName(moment.getType());
        return createGuiItem(icon,
                Component.text("🏆 " + title, titleColor).decorate(TextDecoration.BOLD),
                lore.toArray(new Component[0]));
    }
    
    private Material getMomentTypeIcon(String type) {
        if (type == null) return Material.PAPER;
        
        return switch (type.toLowerCase()) {
            // Block-related
            case "block_break", "diamond_run" -> Material.DIAMOND;
            case "netherite_gain" -> Material.NETHERITE_INGOT;
            
            // Death-related
            case "first_death" -> Material.SKELETON_SKULL;
            case "death_fall", "big_fall_death" -> Material.FEATHER;
            
            // Combat/Boss
            case "boss_kill", "wither_kill" -> Material.WITHER_SKELETON_SKULL;
            case "mlg_water" -> Material.WATER_BUCKET;
            case "entity_kill" -> Material.DIAMOND_SWORD;
            
            // Item-related
            case "item_gain" -> Material.CHEST;
            case "item_craft" -> Material.CRAFTING_TABLE;
            
            // Exploration
            case "biome_discovery" -> Material.FILLED_MAP;
            case "structure_discovery" -> Material.COMPASS;
            
            // Social
            case "first_join" -> Material.OAK_DOOR;
            case "playtime_milestone" -> Material.CLOCK;
            
            default -> Material.PAPER;
        };
    }
    
    private NamedTextColor getMomentTypeColor(String type) {
        if (type == null) return NamedTextColor.WHITE;
        
        return switch (type.toLowerCase()) {
            case "block_break", "diamond_run" -> NamedTextColor.AQUA;
            case "netherite_gain" -> NamedTextColor.DARK_RED;
            case "first_death", "death_fall", "big_fall_death" -> NamedTextColor.RED;
            case "boss_kill", "wither_kill" -> NamedTextColor.DARK_PURPLE;
            case "mlg_water" -> NamedTextColor.BLUE;
            case "entity_kill" -> NamedTextColor.GOLD;
            case "item_gain", "item_craft" -> NamedTextColor.GREEN;
            case "biome_discovery", "structure_discovery" -> NamedTextColor.YELLOW;
            case "first_join" -> NamedTextColor.LIGHT_PURPLE;
            case "playtime_milestone" -> NamedTextColor.WHITE;
            default -> NamedTextColor.GRAY;
        };
    }
    
    private String formatTypeName(String type) {
        if (type == null) return "Unknown";
        // Convert snake_case to Title Case
        String[] words = type.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
    
    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        // Show relative time for recent moments
        if (diff < TimeUnit.HOURS.toMillis(1)) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            return minutes + " minute" + (minutes != 1 ? "s" : "") + " ago";
        } else if (diff < TimeUnit.DAYS.toMillis(1)) {
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        } else if (diff < TimeUnit.DAYS.toMillis(7)) {
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            return days + " day" + (days != 1 ? "s" : "") + " ago";
        }
        
        // Show absolute date for older moments
        return DATE_FORMAT.format(new Date(timestamp));
    }
    
    private String formatDuration(long durationMs) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs);
        if (seconds < 60) {
            return seconds + " sec";
        } else if (seconds < 3600) {
            return (seconds / 60) + " min " + (seconds % 60) + " sec";
        } else {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            return hours + "h " + mins + "m";
        }
    }
    
    private void fillBackground() {
        ItemStack filler = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack topBorder = createBorderItem(Material.ORANGE_STAINED_GLASS_PANE);
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i < 9) {
                inventory.setItem(i, topBorder);
            } else {
                inventory.setItem(i, filler);
            }
        }
    }
    
    private void addNavigationButtons(int totalPages, int totalMoments) {
        // Back button
        inventory.setItem(BACK_SLOT, createGuiItem(Material.ARROW,
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to player stats", NamedTextColor.GRAY)));
        
        // Previous page
        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, createGuiItem(Material.ARROW,
                    Component.text("◀ Previous Page", NamedTextColor.YELLOW),
                    Component.text("Go to page " + page, NamedTextColor.GRAY)));
        }
        
        // Page info
        int displayPage = page + 1;
        int displayTotalPages = Math.max(1, totalPages);
        int showStart = page * MOMENTS_PER_PAGE + 1;
        int showEnd = Math.min((page + 1) * MOMENTS_PER_PAGE, totalMoments);
        
        inventory.setItem(PAGE_INFO_SLOT, createGuiItem(Material.PAPER,
                Component.text("Page " + displayPage + "/" + displayTotalPages, NamedTextColor.WHITE),
                Component.text(totalMoments > 0 ? "Showing " + showStart + "-" + showEnd + " of " + totalMoments : "No achievements", NamedTextColor.GRAY)));
        
        // Next page
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createGuiItem(Material.ARROW,
                    Component.text("Next Page ▶", NamedTextColor.YELLOW),
                    Component.text("Go to page " + (page + 2), NamedTextColor.GRAY)));
        }
        
        // Stats summary
        inventory.setItem(STATS_SLOT, createGuiItem(Material.BOOK,
                Component.text("Achievement Stats", NamedTextColor.AQUA),
                Component.text("Total: " + totalMoments + " achievements", NamedTextColor.GRAY),
                Component.text("Click for detailed breakdown", NamedTextColor.DARK_GRAY)));
        
        // Refresh button
        inventory.setItem(REFRESH_SLOT, createGuiItem(Material.SUNFLOWER,
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Reload achievements", NamedTextColor.GRAY)));
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
            player.closeInventory();
        } else if (slot == PREV_PAGE_SLOT && page > 0) {
            playPageTurnSound(player);
            guiManager.openGui(player, new MomentsHistoryGui(plugin, guiManager, statsService, 
                    momentService, viewer, targetPlayerUuid, page - 1));
        } else if (slot == NEXT_PAGE_SLOT) {
            // Check if there are more pages
            List<MomentEntry> moments = momentService.queryMoments(targetPlayerUuid, null, 0, MAX_MOMENTS);
            int totalPages = (int) Math.ceil(moments.size() / (double) MOMENTS_PER_PAGE);
            if (page < totalPages - 1) {
                playPageTurnSound(player);
                guiManager.openGui(player, new MomentsHistoryGui(plugin, guiManager, statsService, 
                        momentService, viewer, targetPlayerUuid, page + 1));
            }
        } else if (slot == REFRESH_SLOT) {
            playSuccessSound(player);
            initializeItems();
            player.sendMessage(Component.text("Achievements refreshed!", NamedTextColor.GREEN));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
