package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeCategory;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeTier;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI displaying achievement badges earned by a player.
 * Shows earned badges with visual indicators and progress toward unearned badges.
 */
public class BadgesGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final Player viewer;
    private final UUID targetPlayerId;
    private final Inventory inventory;
    
    private int currentPage = 0;
    private BadgeCategory filterCategory = null; // null = show all
    private boolean showUnearned = false;
    
    private static final int BADGES_PER_PAGE = 21; // 3 rows of 7
    private static final int[] BADGE_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,  // Row 3
            28, 29, 30, 31, 32, 33, 34,  // Row 4
            37, 38, 39, 40, 41, 42, 43   // Row 5
    };

    public BadgesGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                     Player viewer, UUID targetPlayerId) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.viewer = viewer;
        this.targetPlayerId = targetPlayerId;
        
        String playerName = Bukkit.getOfflinePlayer(targetPlayerId).getName();
        if (playerName == null) playerName = "Unknown";
        
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("🏅 ", NamedTextColor.GOLD)
                        .append(Component.text("Badges: " + playerName, NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        Optional<StatsRecord> recordOpt = statsService.getStats(targetPlayerId);
        if (recordOpt.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, 
                    Component.text("No stats found", NamedTextColor.RED)));
            addNavigationButtons(new ArrayList<>(), 0);
            return;
        }
        
        StatsRecord record = recordOpt.get();
        List<AchievementBadge> earnedBadges = BadgeEvaluator.evaluateBadges(record);
        
        // Build display list based on filter and show mode
        List<DisplayBadge> displayBadges = buildDisplayList(earnedBadges, record);
        
        // Add header with summary
        addHeader(earnedBadges);
        
        // Add category filter buttons
        addCategoryFilters(earnedBadges);
        
        // Paginate and display badges
        int totalPages = Math.max(1, (int) Math.ceil((double) displayBadges.size() / BADGES_PER_PAGE));
        currentPage = Math.min(currentPage, totalPages - 1);
        
        int startIndex = currentPage * BADGES_PER_PAGE;
        int endIndex = Math.min(startIndex + BADGES_PER_PAGE, displayBadges.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            if (slotIndex < BADGE_SLOTS.length) {
                DisplayBadge db = displayBadges.get(i);
                inventory.setItem(BADGE_SLOTS[slotIndex], createBadgeItem(db));
            }
        }
        
        addNavigationButtons(displayBadges, totalPages);
    }
    
    private List<DisplayBadge> buildDisplayList(List<AchievementBadge> earnedBadges, StatsRecord record) {
        List<DisplayBadge> result = new ArrayList<>();
        
        if (showUnearned) {
            // Show all badges, marking earned ones
            for (BadgeEvaluator.BadgeDefinition def : BadgeEvaluator.getAllBadges()) {
                if (filterCategory != null && def.category() != filterCategory) {
                    continue;
                }
                
                boolean earned = earnedBadges.stream()
                        .anyMatch(b -> b.id().equals(def.id()));
                
                AchievementBadge badge = new AchievementBadge(
                        def.id(), def.name(), def.description(),
                        def.icon(), def.category(), def.tier()
                );
                result.add(new DisplayBadge(badge, earned));
            }
        } else {
            // Show only earned badges
            for (AchievementBadge badge : earnedBadges) {
                if (filterCategory != null && badge.category() != filterCategory) {
                    continue;
                }
                result.add(new DisplayBadge(badge, true));
            }
        }
        
        return result;
    }
    
    private void addHeader(List<AchievementBadge> earnedBadges) {
        int totalBadges = BadgeEvaluator.getTotalBadgeCount();
        int earnedCount = earnedBadges.size();
        int progressPercent = totalBadges > 0 ? (earnedCount * 100) / totalBadges : 0;
        String progressBar = createProgressBar(progressPercent, 10);
        
        // Count by tier
        long legendary = earnedBadges.stream().filter(b -> b.tier() == BadgeTier.LEGENDARY).count();
        long diamond = earnedBadges.stream().filter(b -> b.tier() == BadgeTier.DIAMOND).count();
        long gold = earnedBadges.stream().filter(b -> b.tier() == BadgeTier.GOLD).count();
        long silver = earnedBadges.stream().filter(b -> b.tier() == BadgeTier.SILVER).count();
        long bronze = earnedBadges.stream().filter(b -> b.tier() == BadgeTier.BRONZE).count();
        
        String playerName = Bukkit.getOfflinePlayer(targetPlayerId).getName();
        if (playerName == null) playerName = "Unknown";
        
        inventory.setItem(4, createGuiItem(Material.GOLDEN_HELMET,
                Component.text("🏅 Badge Collection", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text(playerName + "'s Achievements", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Progress: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(earnedCount + "/" + totalBadges, NamedTextColor.YELLOW)),
                Component.text(progressBar + " " + progressPercent + "%", NamedTextColor.GREEN),
                Component.empty(),
                Component.text("🌟 Legendary: " + legendary, NamedTextColor.LIGHT_PURPLE),
                Component.text("💎 Diamond: " + diamond, NamedTextColor.AQUA),
                Component.text("🥇 Gold: " + gold, NamedTextColor.GOLD),
                Component.text("🥈 Silver: " + silver, NamedTextColor.GRAY),
                Component.text("🥉 Bronze: " + bronze, NamedTextColor.RED)));
    }
    
    private void addCategoryFilters(List<AchievementBadge> earnedBadges) {
        // Category filter buttons (row 2: slots 9-17)
        // Slot 9: All categories
        inventory.setItem(9, createFilterItem(null, filterCategory == null, earnedBadges));
        
        // Slots 10-16 for the 7 categories
        BadgeCategory[] categories = BadgeCategory.values();
        for (int i = 0; i < Math.min(7, categories.length); i++) {
            inventory.setItem(10 + i, createFilterItem(categories[i], filterCategory == categories[i], earnedBadges));
        }
    }
    
    private ItemStack createFilterItem(BadgeCategory category, boolean selected, List<AchievementBadge> earnedBadges) {
        Material material;
        String name;
        int count;
        
        if (category == null) {
            material = Material.BOOK;
            name = "📚 All Badges";
            count = earnedBadges.size();
        } else {
            material = getCategoryMaterial(category);
            name = category.getDisplayName();
            count = BadgeEvaluator.countBadgesInCategory(earnedBadges, category);
        }
        
        NamedTextColor color = selected ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(category != null ? category.getDescription() : "Show all categories", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Earned: " + count, NamedTextColor.YELLOW));
        if (selected) {
            lore.add(Component.empty());
            lore.add(Component.text("✓ Currently selected", NamedTextColor.GREEN));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("Click to filter", NamedTextColor.DARK_GRAY));
        }
        
        Component title = Component.text(name, color);
        if (selected) {
            title = title.decorate(TextDecoration.BOLD);
        }
        
        return createGuiItem(material, title, lore.toArray(new Component[0]));
    }
    
    private Material getCategoryMaterial(BadgeCategory category) {
        return switch (category) {
            case COMBAT -> Material.IRON_SWORD;
            case EXPLORATION -> Material.COMPASS;
            case MINING -> Material.IRON_PICKAXE;
            case BUILDING -> Material.BRICKS;
            case SOCIAL -> Material.PLAYER_HEAD;
            case SURVIVAL -> Material.GOLDEN_APPLE;
            case DEDICATION -> Material.CLOCK;
        };
    }
    
    private ItemStack createBadgeItem(DisplayBadge displayBadge) {
        AchievementBadge badge = displayBadge.badge();
        boolean earned = displayBadge.earned();
        
        Material material = earned ? badge.icon() : Material.GRAY_DYE;
        NamedTextColor nameColor = earned ? getTierColor(badge.tier()) : NamedTextColor.DARK_GRAY;
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(badge.tier().getDisplayName(), getTierColor(badge.tier())));
        lore.add(Component.text(badge.category().getDisplayName(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(badge.description(), NamedTextColor.WHITE));
        lore.add(Component.empty());
        
        if (earned) {
            lore.add(Component.text("✓ Earned!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        } else {
            lore.add(Component.text("✗ Not yet earned", NamedTextColor.RED));
            lore.add(Component.text("Keep playing to unlock!", NamedTextColor.DARK_GRAY));
        }
        
        String displayName = (earned ? "✓ " : "✗ ") + badge.name();
        Component title = Component.text(displayName, nameColor);
        if (earned) {
            title = title.decorate(TextDecoration.BOLD);
        }
        
        return createGuiItem(material, title, lore.toArray(new Component[0]));
    }
    
    private NamedTextColor getTierColor(BadgeTier tier) {
        return switch (tier) {
            case BRONZE -> NamedTextColor.RED;
            case SILVER -> NamedTextColor.GRAY;
            case GOLD -> NamedTextColor.GOLD;
            case DIAMOND -> NamedTextColor.AQUA;
            case LEGENDARY -> NamedTextColor.LIGHT_PURPLE;
        };
    }
    
    private String createProgressBar(int percent, int length) {
        int filled = (percent * length) / 100;
        int empty = length - filled;
        return "█".repeat(filled) + "░".repeat(empty);
    }
    
    private void addNavigationButtons(List<DisplayBadge> displayBadges, int totalPages) {
        // Back Button (slot 45)
        inventory.setItem(45, createGuiItem(Material.ARROW, 
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to player stats", NamedTextColor.GRAY)));
        
        // Toggle unearned button (slot 47)
        inventory.setItem(47, createGuiItem(
                showUnearned ? Material.ENDER_EYE : Material.ENDER_PEARL,
                Component.text(showUnearned ? "👁 Showing All" : "👁 Showing Earned", 
                        showUnearned ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                Component.text("Click to toggle", NamedTextColor.GRAY),
                Component.text(showUnearned ? "Currently showing all badges" : "Currently showing earned only", 
                        NamedTextColor.DARK_GRAY)));
        
        // Page indicator (slot 49)
        inventory.setItem(49, createGuiItem(Material.PAPER,
                Component.text("Page " + (currentPage + 1) + "/" + totalPages, NamedTextColor.WHITE),
                Component.text(displayBadges.size() + " badges shown", NamedTextColor.GRAY)));
        
        // Previous page (slot 48)
        if (currentPage > 0) {
            inventory.setItem(48, createGuiItem(Material.SPECTRAL_ARROW,
                    Component.text("◀ Previous", NamedTextColor.YELLOW),
                    Component.text("Go to page " + currentPage, NamedTextColor.GRAY)));
        }
        
        // Next page (slot 50)
        if (currentPage < totalPages - 1) {
            inventory.setItem(50, createGuiItem(Material.SPECTRAL_ARROW,
                    Component.text("Next ▶", NamedTextColor.YELLOW),
                    Component.text("Go to page " + (currentPage + 2), NamedTextColor.GRAY)));
        }
        
        // Refresh (slot 53)
        inventory.setItem(53, createGuiItem(Material.SUNFLOWER,
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Click to refresh", NamedTextColor.GRAY)));
        
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
            Player targetPlayer = Bukkit.getPlayer(targetPlayerId);
            if (targetPlayer != null) {
                guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, targetPlayer));
            } else {
                player.closeInventory();
            }
            return;
        }
        
        // Toggle unearned
        if (slot == 47) {
            showUnearned = !showUnearned;
            currentPage = 0;
            playSuccessSound(player);
            initializeItems();
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
            playSuccessSound(player);
            initializeItems();
            return;
        }
        
        // Category filter buttons (slots 9-16)
        if (slot >= 9 && slot <= 16) {
            if (slot == 9) {
                filterCategory = null;
            } else {
                BadgeCategory[] categories = BadgeCategory.values();
                int catIndex = slot - 10;
                if (catIndex < categories.length) {
                    filterCategory = categories[catIndex];
                }
            }
            currentPage = 0;
            playSuccessSound(player);
            initializeItems();
        }
    }
    
    /**
     * Internal record to track display state of a badge.
     */
    private record DisplayBadge(AchievementBadge badge, boolean earned) {}
}
