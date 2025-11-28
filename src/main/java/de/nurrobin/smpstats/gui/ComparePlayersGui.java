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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI for comparing two players' statistics side-by-side.
 * Layout: Player 1 on left (columns 0-1), comparison in center (column 4), Player 2 on right (columns 7-8)
 */
public class ComparePlayersGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final Player viewer;
    private final UUID player1Uuid;
    private final UUID player2Uuid;
    private final Inventory inventory;
    
    // Layout constants
    private static final int PLAYER1_HEAD_SLOT = 1;
    private static final int PLAYER2_HEAD_SLOT = 7;
    private static final int BACK_SLOT = 49;
    
    // Stat row slots (left column, label, right column)
    private static final int[] PLAYER1_SLOTS = {9, 18, 27, 36, 10, 19, 28, 37};
    private static final int[] PLAYER2_SLOTS = {17, 26, 35, 44, 16, 25, 34, 43};
    private static final int[] LABEL_SLOTS = {13, 22, 31, 40, 14, 23, 32, 41};

    public ComparePlayersGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                             Player viewer, UUID player1Uuid, UUID player2Uuid) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.viewer = viewer;
        this.player1Uuid = player1Uuid;
        this.player2Uuid = player2Uuid;
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("⚔ ", NamedTextColor.GOLD)
                        .append(Component.text("Player Comparison", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        Optional<StatsRecord> record1Opt = statsService.getStats(player1Uuid);
        Optional<StatsRecord> record2Opt = statsService.getStats(player2Uuid);
        
        if (record1Opt.isEmpty() || record2Opt.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER, 
                    Component.text("Could not load player stats", NamedTextColor.RED)));
            addBackButton();
            return;
        }
        
        StatsRecord record1 = record1Opt.get();
        StatsRecord record2 = record2Opt.get();
        
        SkillProfile skills1 = statsService.getSkillProfile(player1Uuid).orElse(new SkillProfile(0, 0, 0, 0, 0));
        SkillProfile skills2 = statsService.getSkillProfile(player2Uuid).orElse(new SkillProfile(0, 0, 0, 0, 0));
        
        // === Player Heads ===
        inventory.setItem(PLAYER1_HEAD_SLOT, createPlayerHeadByUuid(player1Uuid, record1.getName()));
        inventory.setItem(PLAYER2_HEAD_SLOT, createPlayerHeadByUuid(player2Uuid, record2.getName()));
        
        // Center title
        inventory.setItem(4, createGuiItem(Material.COMPARATOR,
                Component.text("VS", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text(record1.getName(), NamedTextColor.AQUA)
                        .append(Component.text(" vs ", NamedTextColor.GRAY))
                        .append(Component.text(record2.getName(), NamedTextColor.RED))));
        
        // === Row 1: Playtime ===
        addComparisonRow(0, Material.CLOCK, "Playtime",
                formatPlaytime(record1.getPlaytimeMillis()), record1.getPlaytimeMillis(),
                formatPlaytime(record2.getPlaytimeMillis()), record2.getPlaytimeMillis());
        
        // === Row 2: K/D Ratio ===
        double kd1 = calculateKD(record1);
        double kd2 = calculateKD(record2);
        addComparisonRow(1, Material.DIAMOND_SWORD, "K/D Ratio",
                String.format("%.2f", kd1), kd1,
                String.format("%.2f", kd2), kd2);
        
        // === Row 3: Mob Kills ===
        addComparisonRow(2, Material.BONE, "Mob Kills",
                formatNumber(record1.getMobKills()), record1.getMobKills(),
                formatNumber(record2.getMobKills()), record2.getMobKills());
        
        // === Row 4: Blocks ===
        long blocks1 = record1.getBlocksBroken() + record1.getBlocksPlaced();
        long blocks2 = record2.getBlocksBroken() + record2.getBlocksPlaced();
        addComparisonRow(3, Material.GRASS_BLOCK, "Blocks",
                formatNumber(blocks1), blocks1,
                formatNumber(blocks2), blocks2);
        
        // === Row 5: Distance Traveled ===
        double dist1 = record1.getDistanceOverworld() + record1.getDistanceNether() + record1.getDistanceEnd();
        double dist2 = record2.getDistanceOverworld() + record2.getDistanceNether() + record2.getDistanceEnd();
        addComparisonRow(4, Material.LEATHER_BOOTS, "Distance",
                formatDistance(dist1), dist1,
                formatDistance(dist2), dist2);
        
        // === Row 6: Biomes Visited ===
        int biomes1 = record1.getBiomesVisited() != null ? record1.getBiomesVisited().size() : 0;
        int biomes2 = record2.getBiomesVisited() != null ? record2.getBiomesVisited().size() : 0;
        addComparisonRow(5, Material.MAP, "Biomes",
                String.valueOf(biomes1), biomes1,
                String.valueOf(biomes2), biomes2);
        
        // === Row 7: Total Skill Points ===
        addComparisonRow(6, Material.EXPERIENCE_BOTTLE, "Skill Points",
                formatNumber((long) skills1.total()), skills1.total(),
                formatNumber((long) skills2.total()), skills2.total());
        
        // === Row 8: Deaths ===
        // Lower is better for deaths, so flip the comparison
        addComparisonRow(7, Material.SKELETON_SKULL, "Deaths",
                formatNumber(record1.getDeaths()), -record1.getDeaths(),  // negative so less is "better"
                formatNumber(record2.getDeaths()), -record2.getDeaths());
        
        // === Skill Comparison (bottom section) ===
        addSkillComparisonBar(skills1, skills2);
        
        addBackButton();
    }
    
    /**
     * Adds a comparison row with color-coded winner indication.
     */
    private void addComparisonRow(int rowIndex, Material icon, String statName,
                                   String value1Text, double value1, String value2Text, double value2) {
        NamedTextColor color1, color2;
        String indicator1 = "", indicator2 = "";
        
        if (value1 > value2) {
            color1 = NamedTextColor.GREEN;
            color2 = NamedTextColor.RED;
            indicator1 = " ▲";
        } else if (value2 > value1) {
            color1 = NamedTextColor.RED;
            color2 = NamedTextColor.GREEN;
            indicator2 = " ▲";
        } else {
            color1 = NamedTextColor.YELLOW;
            color2 = NamedTextColor.YELLOW;
        }
        
        // Calculate row offsets - 2 rows of 4 stats each
        int col, row;
        if (rowIndex < 4) {
            col = 0;
            row = rowIndex;
        } else {
            col = 1;
            row = rowIndex - 4;
        }
        
        int slot1 = 9 + (row * 9) + col;          // Left side (columns 0-1)
        int slotLabel = 12 + (row * 9) + col;     // Center (columns 3-4)
        int slot2 = 15 + (row * 9) + col + 1;     // Right side (columns 7-8)
        
        // Player 1 stat (left)
        inventory.setItem(slot1, createGuiItem(
                getComparisonBlock(color1),
                Component.text(value1Text + indicator1, color1).decorate(TextDecoration.BOLD),
                Component.text(statName, NamedTextColor.GRAY)));
        
        // Label (center)
        inventory.setItem(slotLabel, createGuiItem(icon,
                Component.text(statName, NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
        
        // Player 2 stat (right)
        inventory.setItem(slot2, createGuiItem(
                getComparisonBlock(color2),
                Component.text(value2Text + indicator2, color2).decorate(TextDecoration.BOLD),
                Component.text(statName, NamedTextColor.GRAY)));
    }
    
    private Material getComparisonBlock(NamedTextColor color) {
        if (color == NamedTextColor.GREEN) {
            return Material.LIME_STAINED_GLASS_PANE;
        } else if (color == NamedTextColor.RED) {
            return Material.RED_STAINED_GLASS_PANE;
        } else {
            return Material.YELLOW_STAINED_GLASS_PANE;
        }
    }
    
    /**
     * Adds a visual skill comparison bar at the bottom.
     */
    private void addSkillComparisonBar(SkillProfile skills1, SkillProfile skills2) {
        // Skill bar in row 6 (slots 45-53)
        String[] skillNames = {"⛏ Mining", "⚔ Combat", "🗺 Explore", "🏗 Build", "🌾 Farm"};
        double[] s1 = {skills1.mining(), skills1.combat(), skills1.exploration(), skills1.builder(), skills1.farmer()};
        double[] s2 = {skills2.mining(), skills2.combat(), skills2.exploration(), skills2.builder(), skills2.farmer()};
        Material[] icons = {Material.DIAMOND_PICKAXE, Material.DIAMOND_SWORD, Material.COMPASS, 
                           Material.BRICKS, Material.WHEAT};
        
        for (int i = 0; i < 5; i++) {
            int slot = 45 + i;
            NamedTextColor color;
            String winner;
            
            if (s1[i] > s2[i]) {
                color = NamedTextColor.AQUA;
                winner = "← Winner";
            } else if (s2[i] > s1[i]) {
                color = NamedTextColor.RED;
                winner = "Winner →";
            } else {
                color = NamedTextColor.YELLOW;
                winner = "Tie!";
            }
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Player 1: ", NamedTextColor.AQUA)
                    .append(Component.text(formatNumber((long) s1[i]), NamedTextColor.WHITE)));
            lore.add(Component.text("Player 2: ", NamedTextColor.RED)
                    .append(Component.text(formatNumber((long) s2[i]), NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text(winner, color).decorate(TextDecoration.ITALIC));
            
            inventory.setItem(slot, createGuiItem(icons[i],
                    Component.text(skillNames[i], color).decorate(TextDecoration.BOLD),
                    lore.toArray(new Component[0])));
        }
    }
    
    private void addBackButton() {
        inventory.setItem(BACK_SLOT, createGuiItem(Material.ARROW, 
                Component.text("« Back", NamedTextColor.RED)));
    }
    
    private ItemStack createPlayerHeadByUuid(UUID uuid, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(offlinePlayer);
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            head.setItemMeta(meta);
        }
        return head;
    }
    
    private double calculateKD(StatsRecord record) {
        long kills = record.getPlayerKills() + record.getMobKills();
        long deaths = record.getDeaths();
        return deaths > 0 ? (double) kills / deaths : kills;
    }
    
    private String formatPlaytime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
    
    private String formatNumber(long value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
    
    private String formatDistance(double blocks) {
        if (blocks >= 1000) {
            return String.format("%.1fkm", blocks / 1000.0);
        }
        return String.format("%.0fm", blocks);
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        
        if (slot == BACK_SLOT) {
            // Go back to the main menu or player stats
            viewer.closeInventory();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
