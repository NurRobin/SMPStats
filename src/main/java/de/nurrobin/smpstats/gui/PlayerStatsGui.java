package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
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
        // Player head with name and join dates
        inventory.setItem(4, createPlayerHead(targetPlayer, 
                Component.text(targetPlayer.getName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Player Statistics", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("First Join: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(formatDate(record.getFirstJoin()), NamedTextColor.WHITE)),
                Component.text("Last Seen: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(formatDate(record.getLastJoin()), NamedTextColor.WHITE))));

        // Session Stats (slot 8) - shows activity since login
        addSessionStats(record);

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
            // Player not in active session (shouldn't happen for online player, but handle gracefully)
            inventory.setItem(8, createGuiItem(Material.GRAY_DYE,
                    Component.text("📊 Session Stats", NamedTextColor.GRAY),
                    Component.text("No active session", NamedTextColor.DARK_GRAY)));
            return;
        }
        
        StatsService.SessionDelta delta = deltaOpt.get();
        long sessionMinutes = TimeUnit.MILLISECONDS.toMinutes(delta.durationMillis());
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Since login (" + sessionMinutes + "m ago):", NamedTextColor.GRAY));
        lore.add(Component.empty());
        
        // Only show non-zero deltas
        if (delta.deltaKills() > 0) {
            lore.add(Component.text("  ⚔ Kills: ", NamedTextColor.RED)
                    .append(Component.text("+" + delta.deltaKills(), NamedTextColor.GREEN)));
        }
        if (delta.deltaDeaths() > 0) {
            lore.add(Component.text("  💀 Deaths: ", NamedTextColor.DARK_RED)
                    .append(Component.text("+" + delta.deltaDeaths(), NamedTextColor.RED)));
        }
        if (delta.deltaBlocks() > 0) {
            lore.add(Component.text("  🧱 Blocks: ", NamedTextColor.YELLOW)
                    .append(Component.text("+" + formatNumber(delta.deltaBlocks()), NamedTextColor.GREEN)));
        }
        if (delta.deltaDistance() > 100) { // Only show if moved significant distance
            lore.add(Component.text("  🏃 Distance: ", NamedTextColor.AQUA)
                    .append(Component.text("+" + formatDistance(delta.deltaDistance()), NamedTextColor.GREEN)));
        }
        if (delta.deltaBiomes() > 0) {
            lore.add(Component.text("  🗺 New Biomes: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("+" + delta.deltaBiomes(), NamedTextColor.GREEN)));
        }
        
        // If nothing happened this session
        if (lore.size() == 2) {
            lore.add(Component.text("  No activity yet!", NamedTextColor.DARK_GRAY));
        }
        
        inventory.setItem(8, createGuiItem(Material.LIME_DYE,
                Component.text("📊 Session Stats", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
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
        // Back Button (slot 45)
        inventory.setItem(45, createGuiItem(Material.ARROW, 
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to main menu", NamedTextColor.GRAY)));
        
        // Death Replay Button (slot 46)
        inventory.setItem(46, createGuiItem(Material.SKELETON_SKULL, 
                Component.text("💀 Death History", NamedTextColor.DARK_RED),
                Component.text("View your recent deaths", NamedTextColor.GRAY),
                Component.text("See cause, location, and more", NamedTextColor.DARK_GRAY)));
        
        // Social Partners Button (slot 47)
        inventory.setItem(47, createGuiItem(Material.TOTEM_OF_UNDYING, 
                Component.text("👥 Social Partners", NamedTextColor.YELLOW),
                Component.text("View players you've spent time with", NamedTextColor.GRAY)));
        
        // Compare Button (slot 49)
        inventory.setItem(49, createGuiItem(Material.COMPARATOR, 
                Component.text("⚔ Compare", NamedTextColor.LIGHT_PURPLE),
                Component.text("Compare stats with another player", NamedTextColor.GRAY)));
        
        // Timeline Delta Button (slot 51)
        inventory.setItem(51, createGuiItem(Material.RECOVERY_COMPASS, 
                Component.text("📈 Weekly Progress", NamedTextColor.AQUA),
                Component.text("This week vs last week", NamedTextColor.GRAY),
                Component.text("See how you've improved!", NamedTextColor.DARK_GRAY)));
        
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
        
        if (event.getSlot() == 45) {
            // Back button
            plugin.getServerHealthService().ifPresentOrElse(
                healthService -> guiManager.openGui(player, new MainMenuGui(plugin, guiManager, statsService, healthService)),
                () -> player.closeInventory()
            );
        } else if (event.getSlot() == 46) {
            // Death Replay button
            plugin.getStatsStorage().ifPresentOrElse(
                storage -> guiManager.openGui(player, new DeathReplayGui(plugin, guiManager, statsService, 
                        storage, player, targetPlayer.getUniqueId())),
                () -> player.sendMessage(Component.text("Death history not available", NamedTextColor.RED))
            );
        } else if (event.getSlot() == 47) {
            // Social Partners button
            plugin.getStatsStorage().ifPresentOrElse(
                storage -> guiManager.openGui(player, new SocialPartnersGui(plugin, guiManager, statsService, 
                        storage, player, targetPlayer.getUniqueId())),
                () -> player.sendMessage(Component.text("Social stats not available", NamedTextColor.RED))
            );
        } else if (event.getSlot() == 49) {
            // Compare button - open player selector
            guiManager.openGui(player, new PlayerSelectorGui(plugin, guiManager, statsService, 
                    player, targetPlayer.getUniqueId()));
        } else if (event.getSlot() == 51) {
            // Timeline Delta button - open weekly progress view
            plugin.getStatsStorage().ifPresentOrElse(
                storage -> guiManager.openGui(player, new TimelineDeltaGui(plugin, guiManager, statsService, 
                        storage, player, targetPlayer.getUniqueId())),
                () -> player.sendMessage(Component.text("Timeline stats not available", NamedTextColor.RED))
            );
        } else if (event.getSlot() == 53) {
            // Refresh button
            playSuccessSound(player);
            initializeItems();
            player.sendMessage(Component.text("Stats refreshed!", NamedTextColor.GREEN));
        }
    }
}
