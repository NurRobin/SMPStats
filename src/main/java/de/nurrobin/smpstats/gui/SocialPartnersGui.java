package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.social.SocialPairRow;
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

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI showing a player's top social partners - players they've spent the most time near.
 */
public class SocialPartnersGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final StatsStorage storage;
    private final Player viewer;
    private final UUID targetPlayerUuid;
    private final Inventory inventory;
    
    private static final int PARTNER_SLOTS_START = 10;
    private static final int MAX_PARTNERS = 28; // 4 rows of 7
    private static final int BACK_SLOT = 49;
    private static final int[] PARTNER_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    public SocialPartnersGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                             StatsStorage storage, Player viewer, UUID targetPlayerUuid) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.storage = storage;
        this.viewer = viewer;
        this.targetPlayerUuid = targetPlayerUuid;
        
        String targetName = statsService.getStats(targetPlayerUuid)
                .map(r -> r.getName())
                .orElse("Player");
        
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("👥 ", NamedTextColor.GOLD)
                        .append(Component.text(targetName + "'s Partners", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        // Header
        inventory.setItem(4, createGuiItem(Material.PLAYER_HEAD,
                Component.text("Social Partners", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Players you've spent time near", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Sorted by time together", NamedTextColor.DARK_GRAY)));
        
        // Load social pairs
        List<SocialPairRow> pairs;
        try {
            pairs = storage.loadSocialPairsForPlayer(targetPlayerUuid, MAX_PARTNERS);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load social pairs: " + e.getMessage());
            pairs = new ArrayList<>();
        }
        
        if (pairs.isEmpty()) {
            inventory.setItem(22, createGuiItem(Material.BARRIER,
                    Component.text("No partners found", NamedTextColor.RED),
                    Component.text("Play near other players to", NamedTextColor.GRAY),
                    Component.text("build your social network!", NamedTextColor.GRAY)));
        } else {
            int slot = 0;
            for (SocialPairRow pair : pairs) {
                if (slot >= PARTNER_SLOTS.length) break;
                
                // Get the partner's UUID (the one that isn't our target)
                UUID partnerUuid = pair.uuidA().equals(targetPlayerUuid) ? pair.uuidB() : pair.uuidA();
                
                ItemStack partnerItem = createPartnerItem(partnerUuid, pair, slot + 1);
                inventory.setItem(PARTNER_SLOTS[slot], partnerItem);
                slot++;
            }
        }
        
        // Stats summary
        long totalTimeTogether = pairs.stream().mapToLong(SocialPairRow::seconds).sum();
        long totalSharedKills = pairs.stream().mapToLong(SocialPairRow::sharedKills).sum();
        
        inventory.setItem(48, createGuiItem(Material.CLOCK,
                Component.text("Total Time Together", NamedTextColor.AQUA),
                Component.text(formatTime(totalTimeTogether), NamedTextColor.WHITE)));
        
        inventory.setItem(50, createGuiItem(Material.DIAMOND_SWORD,
                Component.text("Total Shared Kills", NamedTextColor.RED),
                Component.text(formatNumber(totalSharedKills), NamedTextColor.WHITE)));
        
        addNavigationButtons();
        fillBackground();
    }
    
    private ItemStack createPartnerItem(UUID partnerUuid, SocialPairRow pair, int rank) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(partnerUuid);
            meta.setOwningPlayer(offlinePlayer);
            
            String name = statsService.getStats(partnerUuid)
                    .map(r -> r.getName())
                    .orElse(offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown");
            
            boolean isOnline = Bukkit.getPlayer(partnerUuid) != null;
            
            // Rank medal based on position
            String medal = switch (rank) {
                case 1 -> "🥇 ";
                case 2 -> "🥈 ";
                case 3 -> "🥉 ";
                default -> "#" + rank + " ";
            };
            
            NamedTextColor nameColor = isOnline ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            meta.displayName(Component.text(medal + name, nameColor).decorate(TextDecoration.BOLD));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("⏱ Time Together: ", NamedTextColor.AQUA)
                    .append(Component.text(formatTime(pair.seconds()), NamedTextColor.WHITE)));
            lore.add(Component.text("⚔ Shared Kills: ", NamedTextColor.RED)
                    .append(Component.text(formatNumber(pair.sharedKills()), NamedTextColor.WHITE)));
            
            if (pair.sharedMobKills() > 0 || pair.sharedPlayerKills() > 0) {
                lore.add(Component.empty());
                lore.add(Component.text("  Mob Kills: " + formatNumber(pair.sharedMobKills()), NamedTextColor.GRAY));
                lore.add(Component.text("  PvP Kills: " + formatNumber(pair.sharedPlayerKills()), NamedTextColor.GRAY));
            }
            
            lore.add(Component.empty());
            if (isOnline) {
                lore.add(Component.text("🟢 Online now!", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("⚫ Offline", NamedTextColor.GRAY));
            }
            lore.add(Component.text("Click to view stats", NamedTextColor.YELLOW));
            
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        
        return head;
    }
    
    private void addNavigationButtons() {
        inventory.setItem(BACK_SLOT, createGuiItem(Material.ARROW,
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to player stats", NamedTextColor.GRAY)));
    }
    
    private void fillBackground() {
        ItemStack filler = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long hours = TimeUnit.SECONDS.toHours(seconds);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
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
        } else {
            // Check if clicked on a partner slot
            for (int i = 0; i < PARTNER_SLOTS.length; i++) {
                if (PARTNER_SLOTS[i] == slot) {
                    ItemStack item = inventory.getItem(slot);
                    if (item != null && item.getType() == Material.PLAYER_HEAD) {
                        SkullMeta meta = (SkullMeta) item.getItemMeta();
                        if (meta != null && meta.getOwningPlayer() != null) {
                            // Open that player's stats
                            UUID partnerUuid = meta.getOwningPlayer().getUniqueId();
                            Player partnerPlayer = Bukkit.getPlayer(partnerUuid);
                            if (partnerPlayer != null) {
                                playSuccessSound(player);
                                guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, partnerPlayer));
                            } else {
                                player.sendMessage(Component.text("This player is offline - can't view live stats", NamedTextColor.RED));
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
