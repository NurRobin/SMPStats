package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeTier;
import de.nurrobin.smpstats.skills.SkillProfile;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

public class PlayerStatsGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final Player targetPlayer;
    private final Inventory inventory;
    
    /** Total known biomes in Minecraft (approximate for progress display) */
    private static final int TOTAL_BIOMES = 64;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    public PlayerStatsGui(SMPStats plugin, GuiManager guiManager, StatsService statsService, Player targetPlayer) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.targetPlayer = targetPlayer;
        this.inventory = Bukkit.createInventory(this, 54, 
                Component.text("📊 ", NamedTextColor.GOLD)
                        .append(Component.text("Stats: " + targetPlayer.getName(), NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        Optional<StatsRecord> recordOpt = statsService.getStats(targetPlayer.getUniqueId());
        if (recordOpt.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, Component.text("No stats found", NamedTextColor.RED)));
            addNavigationButtons();
            return;
        }
        StatsRecord record = recordOpt.get();

        // === ROW 1: Player Info ===
        
        // Session Stats in prominent corner (slot 0) - Live activity indicator
        addSessionStats(record);
        
        // Player head with name and join dates
        inventory.setItem(4, createPlayerHead(targetPlayer, 
                Component.text(targetPlayer.getName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Player Statistics", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("First Join: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(formatDate(record.getFirstJoin()), NamedTextColor.WHITE)),
                Component.text("Last Seen: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(formatDate(record.getLastJoin()), NamedTextColor.WHITE))));

        // Quick tips (slot 8) - helpful info
        inventory.setItem(8, createGuiItem(Material.BOOK,
                Component.text("💡 Quick Info", NamedTextColor.YELLOW),
                Component.text("Session stats shown in top-left", NamedTextColor.GRAY),
                Component.text("Badges = achievements earned", NamedTextColor.GRAY),
                Component.text("More... = additional features", NamedTextColor.GRAY)));

        // === ROW 2: Core Stats ===
        // Playtime (slot 10)
        long hours = TimeUnit.MILLISECONDS.toHours(record.getPlaytimeMillis());
        long days = hours / 24;
        long remainingHours = hours % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(record.getPlaytimeMillis()) % 60;
        String playtimeText = days > 0 ? days + "d " + remainingHours + "h" : hours + "h " + minutes + "m";
        inventory.setItem(10, createGuiItem(Material.CLOCK, 
                Component.text("⏱ Playtime", NamedTextColor.GOLD),
                Component.text(playtimeText, NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Total Hours: " + hours, NamedTextColor.DARK_GRAY)));

        // Kills with K/D ratio (slot 12)
        long totalKills = record.getMobKills() + record.getPlayerKills();
        double kdRatio = record.getDeaths() > 0 ? (double) totalKills / record.getDeaths() : totalKills;
        NamedTextColor kdColor = kdRatio >= 2.0 ? NamedTextColor.GREEN : (kdRatio >= 1.0 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        inventory.setItem(12, createGuiItem(Material.DIAMOND_SWORD, 
                Component.text("⚔ Kills", NamedTextColor.RED),
                Component.text("Mobs: " + record.getMobKills(), NamedTextColor.WHITE),
                Component.text("Players: " + record.getPlayerKills(), NamedTextColor.WHITE),
                Component.text("Total: " + totalKills, NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("K/D Ratio: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(String.format("%.2f", kdRatio), kdColor))));

        // Deaths (slot 14)
        inventory.setItem(14, createGuiItem(Material.SKELETON_SKULL, 
                Component.text("💀 Deaths", NamedTextColor.DARK_RED),
                Component.text(String.valueOf(record.getDeaths()), NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Last Cause: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(record.getLastDeathCause() != null ? record.getLastDeathCause() : "None", NamedTextColor.GRAY))));

        // Blocks (slot 16)
        inventory.setItem(16, createGuiItem(Material.GRASS_BLOCK, 
                Component.text("🧱 Blocks", NamedTextColor.GREEN),
                Component.text("Broken: " + formatNumber(record.getBlocksBroken()), NamedTextColor.WHITE),
                Component.text("Placed: " + formatNumber(record.getBlocksPlaced()), NamedTextColor.WHITE)));

        // === ROW 3: More Stats ===
        // Distance (slot 19)
        double totalDistance = record.getDistanceOverworld() + record.getDistanceNether() + record.getDistanceEnd();
        inventory.setItem(19, createGuiItem(Material.LEATHER_BOOTS, 
                Component.text("🏃 Distance", NamedTextColor.AQUA),
                Component.text("Overworld: " + formatDistance(record.getDistanceOverworld()), NamedTextColor.WHITE),
                Component.text("Nether: " + formatDistance(record.getDistanceNether()), NamedTextColor.WHITE),
                Component.text("End: " + formatDistance(record.getDistanceEnd()), NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Total: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(formatDistance(totalDistance), NamedTextColor.YELLOW))));

        // Combat Damage (slot 21)
        inventory.setItem(21, createGuiItem(Material.IRON_SWORD, 
                Component.text("💥 Combat", NamedTextColor.DARK_PURPLE),
                Component.text("Dealt: " + formatNumber((long) record.getDamageDealt()) + " ❤", NamedTextColor.WHITE),
                Component.text("Taken: " + formatNumber((long) record.getDamageTaken()) + " ❤", NamedTextColor.WHITE)));

        // Items (slot 23)
        inventory.setItem(23, createGuiItem(Material.CRAFTING_TABLE, 
                Component.text("🔨 Items", NamedTextColor.YELLOW),
                Component.text("Crafted: " + formatNumber(record.getItemsCrafted()), NamedTextColor.WHITE),
                Component.text("Consumed: " + formatNumber(record.getItemsConsumed()), NamedTextColor.WHITE)));

        // Biomes Progress (slot 25)
        int biomesVisited = record.getBiomesVisited().size();
        int progressPercent = Math.min(100, (biomesVisited * 100) / TOTAL_BIOMES);
        String progressBar = createProgressBar(progressPercent, 10);
        NamedTextColor biomeColor = progressPercent >= 75 ? NamedTextColor.GREEN : 
                                    (progressPercent >= 50 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        inventory.setItem(25, createGuiItem(Material.FILLED_MAP, 
                Component.text("🗺 Biomes Explored", NamedTextColor.LIGHT_PURPLE),
                Component.text(biomesVisited + " / " + TOTAL_BIOMES + " discovered", NamedTextColor.WHITE),
                Component.empty(),
                Component.text(progressBar + " " + progressPercent + "%", biomeColor)));

        // === ROW 4: Skills Section ===
        addSkillsSection(record);

        addNavigationButtons();
    }

    private void addSkillsSection(StatsRecord record) {
        Optional<SkillProfile> profileOpt = statsService.getSkillProfile(record.getUuid());
        if (profileOpt.isEmpty()) {
            return;
        }
        SkillProfile profile = profileOpt.get();
        
        // Skills header (slot 31)
        inventory.setItem(31, createGuiItem(Material.EXPERIENCE_BOTTLE, 
                Component.text("⭐ Skills Overview", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Your skill levels based on activity", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Total Score: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(String.format("%.1f", profile.total()), NamedTextColor.YELLOW))));

        // Mining Skill (slot 37)
        inventory.setItem(37, createSkillItem(Material.IRON_PICKAXE, "⛏ Mining", profile.mining(), NamedTextColor.AQUA));
        
        // Combat Skill (slot 38)
        inventory.setItem(38, createSkillItem(Material.NETHERITE_SWORD, "⚔ Combat", profile.combat(), NamedTextColor.RED));
        
        // Exploration Skill (slot 39)
        inventory.setItem(39, createSkillItem(Material.COMPASS, "🧭 Exploration", profile.exploration(), NamedTextColor.GREEN));
        
        // Builder Skill (slot 41)
        inventory.setItem(41, createSkillItem(Material.BRICKS, "🏗 Builder", profile.builder(), NamedTextColor.YELLOW));
        
        // Farmer Skill (slot 42)
        inventory.setItem(42, createSkillItem(Material.WHEAT, "🌾 Farmer", profile.farmer(), NamedTextColor.GOLD));
        
        // Total/Best skill indicator (slot 43)
        String bestSkill = getBestSkill(profile);
        inventory.setItem(43, createGuiItem(Material.NETHER_STAR, 
                Component.text("🏆 Best Skill", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                Component.text(bestSkill, NamedTextColor.WHITE)));
    }

    private void addSessionStats(StatsRecord record) {
        Optional<StatsService.SessionDelta> deltaOpt = statsService.getSessionDelta(targetPlayer.getUniqueId());
        
        if (deltaOpt.isEmpty()) {
            // Player not in active session
            inventory.setItem(0, createGuiItem(Material.GRAY_DYE,
                    Component.text("🔴 Offline", NamedTextColor.GRAY),
                    Component.text("No active session", NamedTextColor.DARK_GRAY)));
            return;
        }
        
        StatsService.SessionDelta delta = deltaOpt.get();
        long sessionMinutes = TimeUnit.MILLISECONDS.toMinutes(delta.durationMillis());
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("🟢 LIVE SESSION", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        lore.add(Component.text("Active for " + sessionMinutes + " min", NamedTextColor.GRAY));
        lore.add(Component.empty());
        
        int activityCount = 0;
        
        // Only show non-zero deltas
        if (delta.deltaKills() > 0) {
            lore.add(Component.text("+" + delta.deltaKills() + " kills", NamedTextColor.GREEN));
            activityCount++;
        }
        if (delta.deltaDeaths() > 0) {
            lore.add(Component.text("+" + delta.deltaDeaths() + " deaths", NamedTextColor.RED));
            activityCount++;
        }
        if (delta.deltaBlocks() > 0) {
            lore.add(Component.text("+" + formatNumber(delta.deltaBlocks()) + " blocks", NamedTextColor.YELLOW));
            activityCount++;
        }
        if (delta.deltaDistance() > 100) { // Only show if moved significant distance
            lore.add(Component.text("+" + formatDistance(delta.deltaDistance()) + " traveled", NamedTextColor.AQUA));
            activityCount++;
        }
        if (delta.deltaBiomes() > 0) {
            lore.add(Component.text("+" + delta.deltaBiomes() + " new biomes", NamedTextColor.LIGHT_PURPLE));
            activityCount++;
        }
        
        // If nothing happened this session
        if (activityCount == 0) {
            lore.add(Component.text("Idle", NamedTextColor.GRAY));
        }
        
        inventory.setItem(0, createGuiItem(Material.LIME_CONCRETE,
                Component.text("🟢 LIVE", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                lore.toArray(new Component[0])));
    }

    private ItemStack createSkillItem(Material material, String name, double value, NamedTextColor color) {
        int level = (int) Math.floor(value / 100); // Every 100 points = 1 level
        int progressToNext = (int) (value % 100);
        String progressBar = createProgressBar(progressToNext, 10);
        
        return createGuiItem(material,
                Component.text(name, color).decorate(TextDecoration.BOLD),
                Component.text("Level " + level, NamedTextColor.WHITE),
                Component.text("Score: " + String.format("%.1f", value), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Progress to next:", NamedTextColor.DARK_GRAY),
                Component.text(progressBar + " " + progressToNext + "%", NamedTextColor.GRAY));
    }

    private String getBestSkill(SkillProfile profile) {
        double max = Math.max(profile.mining(), 
                     Math.max(profile.combat(), 
                     Math.max(profile.exploration(), 
                     Math.max(profile.builder(), profile.farmer()))));
        
        if (max == profile.mining()) return "⛏ Mining";
        if (max == profile.combat()) return "⚔ Combat";
        if (max == profile.exploration()) return "🧭 Exploration";
        if (max == profile.builder()) return "🏗 Builder";
        return "🌾 Farmer";
    }

    private String createProgressBar(int percent, int length) {
        int filled = (percent * length) / 100;
        int empty = length - filled;
        return "█".repeat(filled) + "░".repeat(empty);
    }

    private void addNavigationButtons() {
        // === Simplified Navigation Bar ===
        // Core features promoted, secondary features in "More" submenu
        
        // Back Button (slot 45)
        inventory.setItem(45, createGuiItem(Material.ARROW, 
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to main menu", NamedTextColor.GRAY)));
        
        // Badges Button (slot 47) - Promoted: Most engaging feature
        inventory.setItem(47, createBadgesSummaryButton());
        
        // Friends Button (slot 48) - Promoted: Social is important
        inventory.setItem(48, createGuiItem(Material.TOTEM_OF_UNDYING, 
                Component.text("👥 Friends", NamedTextColor.YELLOW),
                Component.text("View your social connections", NamedTextColor.GRAY),
                Component.text("See who you play with most!", NamedTextColor.DARK_GRAY)));
        
        // Compare Button (slot 49) - Promoted: Competitive feature
        inventory.setItem(49, createGuiItem(Material.COMPARATOR, 
                Component.text("⚔ Compare", NamedTextColor.LIGHT_PURPLE),
                Component.text("Compare stats with another player", NamedTextColor.GRAY)));
        
        // More Stats Button (slot 51) - Groups secondary features
        inventory.setItem(51, createGuiItem(Material.BOOK, 
                Component.text("📋 More...", NamedTextColor.AQUA),
                Component.text("Additional stats & tools", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("▸ Death History", NamedTextColor.DARK_GRAY),
                Component.text("▸ Weekly Progress", NamedTextColor.DARK_GRAY),
                Component.text("▸ Activity Heatmap", NamedTextColor.DARK_GRAY),
                Component.text("▸ Moments", NamedTextColor.DARK_GRAY)));
        
        // Refresh Button (slot 53)
        inventory.setItem(53, createGuiItem(Material.SUNFLOWER, 
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Click to refresh stats", NamedTextColor.GRAY)));

        // Fill background
        ItemStack filler = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
    
    private ItemStack createBadgesSummaryButton() {
        Optional<StatsRecord> recordOpt = statsService.getStats(targetPlayer.getUniqueId());
        if (recordOpt.isEmpty()) {
            return createGuiItem(Material.GOLDEN_HELMET,
                    Component.text("🏅 Badges", NamedTextColor.GOLD),
                    Component.text("No stats available", NamedTextColor.GRAY));
        }
        
        List<AchievementBadge> earned = BadgeEvaluator.evaluateBadges(recordOpt.get());
        int total = BadgeEvaluator.getTotalBadgeCount();
        int earnedCount = earned.size();
        int percent = total > 0 ? (earnedCount * 100) / total : 0;
        
        // Count by tier
        long legendary = earned.stream().filter(b -> b.tier() == BadgeTier.LEGENDARY).count();
        long diamond = earned.stream().filter(b -> b.tier() == BadgeTier.DIAMOND).count();
        long gold = earned.stream().filter(b -> b.tier() == BadgeTier.GOLD).count();
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(earnedCount + "/" + total + " badges (" + percent + "%)", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        if (legendary > 0) {
            lore.add(Component.text("🌟 Legendary: " + legendary, NamedTextColor.LIGHT_PURPLE));
        }
        if (diamond > 0) {
            lore.add(Component.text("💎 Diamond: " + diamond, NamedTextColor.AQUA));
        }
        if (gold > 0) {
            lore.add(Component.text("🥇 Gold: " + gold, NamedTextColor.GOLD));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to view all badges!", NamedTextColor.DARK_GRAY));
        
        return createGuiItem(Material.GOLDEN_HELMET,
                Component.text("🏅 Badges", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                lore.toArray(new Component[0]));
    }

    private String formatDistance(double meters) {
        if (meters < 1000) {
            return (int) meters + "m";
        }
        return String.format("%.1fkm", meters / 1000);
    }

    private String formatNumber(long number) {
        if (number < 1000) return String.valueOf(number);
        if (number < 1000000) return String.format("%.1fK", number / 1000.0);
        return String.format("%.1fM", number / 1000000.0);
    }

    private String formatDate(long epochMillis) {
        if (epochMillis <= 0) return "Unknown";
        return DATE_FORMAT.format(new Date(epochMillis));
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
        playClickSound(player);
        
        switch (event.getSlot()) {
            case 45 -> {
                // Back button
                plugin.getServerHealthService().ifPresentOrElse(
                    healthService -> guiManager.openGui(player, new MainMenuGui(plugin, guiManager, statsService, healthService)),
                    () -> player.closeInventory()
                );
            }
            case 47 -> {
                // Badges button - promoted to main nav
                guiManager.openGui(player, new BadgesGui(plugin, guiManager, statsService, 
                        player, targetPlayer.getUniqueId()));
            }
            case 48 -> {
                // Friends button - enhanced social partners
                plugin.getStatsStorage().ifPresentOrElse(
                    storage -> guiManager.openGui(player, new SocialPartnersGui(plugin, guiManager, statsService, 
                            storage, player, targetPlayer.getUniqueId())),
                    () -> player.sendMessage(Component.text("Social stats not available", NamedTextColor.RED))
                );
            }
            case 49 -> {
                // Compare button - open player selector
                guiManager.openGui(player, new PlayerSelectorGui(plugin, guiManager, statsService, 
                        player, targetPlayer.getUniqueId()));
            }
            case 51 -> {
                // More Stats button - opens submenu with secondary features
                guiManager.openGui(player, new MoreStatsGui(plugin, guiManager, statsService, 
                        player, targetPlayer.getUniqueId(), targetPlayer.getName()));
            }
            case 53 -> {
                // Refresh button
                playSuccessSound(player);
                initializeItems();
                player.sendMessage(Component.text("Stats refreshed!", NamedTextColor.GREEN));
            }
        }
    }
}
