package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.timeline.DeathReplayEntry;
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

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI for viewing a player's death history with detailed replay information.
 * Shows cause of death, location, nearby entities, and inventory at time of death.
 */
public class DeathReplayGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final StatsStorage storage;
    private final Player viewer;
    private final UUID targetPlayerUuid;
    private final Inventory inventory;
    private final int page;
    
    /** Maximum deaths shown per page */
    private static final int DEATHS_PER_PAGE = 21;
    /** Maximum deaths to load from database */
    private static final int MAX_DEATHS = 100;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");
    
    // Layout slots
    private static final int BACK_SLOT = 45;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int REFRESH_SLOT = 53;
    
    // Death entry slots (7x3 grid)
    private static final int[] DEATH_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    public DeathReplayGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                          StatsStorage storage, Player viewer, UUID targetPlayerUuid) {
        this(plugin, guiManager, statsService, storage, viewer, targetPlayerUuid, 0);
    }

    public DeathReplayGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                          StatsStorage storage, Player viewer, UUID targetPlayerUuid, int page) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.storage = storage;
        this.viewer = viewer;
        this.targetPlayerUuid = targetPlayerUuid;
        this.page = page;
        
        String targetName = statsService.getStats(targetPlayerUuid)
                .map(r -> r.getName())
                .orElse("Player");
        
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("💀 ", NamedTextColor.DARK_RED)
                        .append(Component.text(targetName + "'s Deaths", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        fillBackground();
        
        // Load death replays
        List<DeathReplayEntry> deaths;
        try {
            deaths = storage.loadDeathReplaysForPlayer(targetPlayerUuid, MAX_DEATHS);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load death replays: " + e.getMessage());
            inventory.setItem(22, createGuiItem(Material.BARRIER,
                    Component.text("Could not load death data", NamedTextColor.RED),
                    Component.text("Please try again later", NamedTextColor.GRAY)));
            addNavigationButtons(0, 0);
            return;
        }
        
        // Header
        String targetName = statsService.getStats(targetPlayerUuid)
                .map(r -> r.getName())
                .orElse("Player");
        inventory.setItem(4, createGuiItem(Material.SKELETON_SKULL,
                Component.text("Death History", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                Component.text(targetName + "'s deaths", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Total: " + deaths.size() + " recorded deaths", NamedTextColor.DARK_GRAY)));
        
        if (deaths.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.TOTEM_OF_UNDYING,
                    Component.text("No deaths recorded!", NamedTextColor.GREEN),
                    Component.text("This player hasn't died yet", NamedTextColor.GRAY),
                    Component.text("(or death tracking wasn't enabled)", NamedTextColor.DARK_GRAY)));
            addNavigationButtons(0, 0);
            return;
        }
        
        // Calculate pagination
        int totalPages = (int) Math.ceil(deaths.size() / (double) DEATHS_PER_PAGE);
        int startIndex = page * DEATHS_PER_PAGE;
        int endIndex = Math.min(startIndex + DEATHS_PER_PAGE, deaths.size());
        
        // Display deaths
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < DEATH_SLOTS.length; i++) {
            DeathReplayEntry death = deaths.get(i);
            int deathNumber = deaths.size() - i; // Reverse numbering (most recent = highest)
            
            ItemStack deathItem = createDeathItem(death, deathNumber);
            inventory.setItem(DEATH_SLOTS[slotIndex], deathItem);
            slotIndex++;
        }
        
        addNavigationButtons(totalPages, deaths.size());
    }
    
    private ItemStack createDeathItem(DeathReplayEntry death, int deathNumber) {
        Material deathIcon = getDeathCauseIcon(death.cause());
        
        List<Component> lore = new ArrayList<>();
        
        // Timestamp
        lore.add(Component.text(formatTimestamp(death.timestamp()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        
        // Death cause
        lore.add(Component.text("⚔ Cause: ", NamedTextColor.YELLOW)
                .append(Component.text(formatCause(death.cause()), NamedTextColor.WHITE)));
        
        // Location
        lore.add(Component.text("📍 Location: ", NamedTextColor.AQUA)
                .append(Component.text(death.world() + " (" + death.x() + ", " + death.y() + ", " + death.z() + ")", NamedTextColor.WHITE)));
        
        // Health at death
        lore.add(Component.text("❤ Health: ", NamedTextColor.RED)
                .append(Component.text(String.format("%.1f", death.health()), NamedTextColor.WHITE)));
        
        // Fall distance if relevant
        if (death.fallDistance() > 0) {
            lore.add(Component.text("📉 Fall Distance: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(String.format("%.1f blocks", death.fallDistance()), NamedTextColor.WHITE)));
        }
        
        // Nearby players
        if (death.nearbyPlayers() != null && !death.nearbyPlayers().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("👥 Nearby Players:", NamedTextColor.GOLD));
            for (String player : death.nearbyPlayers()) {
                lore.add(Component.text("  • " + player, NamedTextColor.WHITE));
            }
        }
        
        // Nearby mobs
        if (death.nearbyMobs() != null && !death.nearbyMobs().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("🐾 Nearby Mobs:", NamedTextColor.GREEN));
            int mobCount = Math.min(death.nearbyMobs().size(), 5);
            for (int i = 0; i < mobCount; i++) {
                lore.add(Component.text("  • " + formatMobName(death.nearbyMobs().get(i)), NamedTextColor.WHITE));
            }
            if (death.nearbyMobs().size() > 5) {
                lore.add(Component.text("  ... and " + (death.nearbyMobs().size() - 5) + " more", NamedTextColor.DARK_GRAY));
            }
        }
        
        // Inventory hint
        if (death.inventory() != null && !death.inventory().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("📦 Had " + death.inventory().size() + " item(s)", NamedTextColor.DARK_GRAY));
        }
        
        return createGuiItem(deathIcon,
                Component.text("Death #" + deathNumber, NamedTextColor.RED).decorate(TextDecoration.BOLD),
                lore.toArray(new Component[0]));
    }
    
    private Material getDeathCauseIcon(String cause) {
        if (cause == null) return Material.SKELETON_SKULL;
        
        String lowerCause = cause.toLowerCase();
        
        // Mob deaths - check these first before generic "killed by"
        if (lowerCause.contains("zombie")) return Material.ZOMBIE_HEAD;
        if (lowerCause.contains("skeleton")) return Material.SKELETON_SKULL;
        if (lowerCause.contains("creeper")) return Material.CREEPER_HEAD;
        if (lowerCause.contains("spider")) return Material.SPIDER_EYE;
        if (lowerCause.contains("enderman")) return Material.ENDER_PEARL;
        if (lowerCause.contains("blaze")) return Material.BLAZE_ROD;
        if (lowerCause.contains("wither") && lowerCause.contains("skeleton")) return Material.WITHER_SKELETON_SKULL;
        if (lowerCause.contains("dragon")) return Material.DRAGON_HEAD;
        if (lowerCause.contains("piglin") || lowerCause.contains("hoglin")) return Material.PIGLIN_HEAD;
        
        // Player kills - after mob checks
        if (lowerCause.contains("slain by") || lowerCause.contains("killed by")) {
            return Material.DIAMOND_SWORD;
        }
        
        // Environmental deaths - check void before fall since void deaths say "fell into the void"
        if (lowerCause.contains("void") || lowerCause.contains("out of the world")) return Material.END_PORTAL_FRAME;
        if (lowerCause.contains("fall") || lowerCause.contains("fell") || lowerCause.contains("ground")) return Material.FEATHER;
        if (lowerCause.contains("lava") || lowerCause.contains("fire") || lowerCause.contains("burn") || lowerCause.contains("flames")) return Material.LAVA_BUCKET;
        if (lowerCause.contains("drown") || lowerCause.contains("water")) return Material.WATER_BUCKET;
        if (lowerCause.contains("suffocate") || lowerCause.contains("wall")) return Material.SAND;
        if (lowerCause.contains("lightning")) return Material.LIGHTNING_ROD;
        if (lowerCause.contains("explosion") || lowerCause.contains("tnt")) return Material.TNT;
        if (lowerCause.contains("cactus")) return Material.CACTUS;
        if (lowerCause.contains("anvil")) return Material.ANVIL;
        if (lowerCause.contains("starv") || lowerCause.contains("hunger")) return Material.ROTTEN_FLESH;
        if (lowerCause.contains("wither")) return Material.WITHER_ROSE;
        if (lowerCause.contains("magic") || lowerCause.contains("potion")) return Material.SPLASH_POTION;
        if (lowerCause.contains("thorns")) return Material.ROSE_BUSH;
        if (lowerCause.contains("freeze") || lowerCause.contains("powder snow")) return Material.POWDER_SNOW_BUCKET;
        
        return Material.SKELETON_SKULL;
    }
    
    private String formatCause(String cause) {
        if (cause == null || cause.isEmpty()) return "Unknown";
        // Capitalize first letter of each word
        String[] words = cause.split(" ");
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
    
    private String formatMobName(String mobName) {
        if (mobName == null) return "Unknown";
        // Convert ZOMBIE_VILLAGER to Zombie Villager
        String[] words = mobName.replace("_", " ").toLowerCase().split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
    
    private String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        // Show relative time for recent deaths
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
        
        // Show absolute date for older deaths
        return DATE_FORMAT.format(new Date(timestamp));
    }
    
    private void fillBackground() {
        ItemStack filler = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack topBorder = createBorderItem(Material.BLACK_STAINED_GLASS_PANE);
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i < 9) {
                inventory.setItem(i, topBorder);
            } else {
                inventory.setItem(i, filler);
            }
        }
    }
    
    private void addNavigationButtons(int totalPages, int totalDeaths) {
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
        inventory.setItem(PAGE_INFO_SLOT, createGuiItem(Material.PAPER,
                Component.text("Page " + displayPage + "/" + displayTotalPages, NamedTextColor.WHITE),
                Component.text("Showing deaths " + (page * DEATHS_PER_PAGE + 1) + "-" + 
                        Math.min((page + 1) * DEATHS_PER_PAGE, totalDeaths), NamedTextColor.GRAY)));
        
        // Next page
        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createGuiItem(Material.ARROW,
                    Component.text("Next Page ▶", NamedTextColor.YELLOW),
                    Component.text("Go to page " + (page + 2), NamedTextColor.GRAY)));
        }
        
        // Refresh button
        inventory.setItem(REFRESH_SLOT, createGuiItem(Material.SUNFLOWER,
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Reload death history", NamedTextColor.GRAY)));
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
            guiManager.openGui(player, new DeathReplayGui(plugin, guiManager, statsService, 
                    storage, viewer, targetPlayerUuid, page - 1));
        } else if (slot == NEXT_PAGE_SLOT) {
            // Check if there are more pages
            try {
                List<DeathReplayEntry> deaths = storage.loadDeathReplaysForPlayer(targetPlayerUuid, MAX_DEATHS);
                int totalPages = (int) Math.ceil(deaths.size() / (double) DEATHS_PER_PAGE);
                if (page < totalPages - 1) {
                    playPageTurnSound(player);
                    guiManager.openGui(player, new DeathReplayGui(plugin, guiManager, statsService, 
                            storage, viewer, targetPlayerUuid, page + 1));
                }
            } catch (SQLException e) {
                player.sendMessage(Component.text("Could not load more deaths", NamedTextColor.RED));
            }
        } else if (slot == REFRESH_SLOT) {
            playSuccessSound(player);
            initializeItems();
            player.sendMessage(Component.text("Death history refreshed!", NamedTextColor.GREEN));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
