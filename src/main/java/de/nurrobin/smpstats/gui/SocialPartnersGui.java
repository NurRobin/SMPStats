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
import java.util.stream.Collectors;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * GUI showing a player's friends/social partners - players they've spent the most time near.
 * Enhanced with filtering, online status, and friendship level indicators.
 */
public class SocialPartnersGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final StatsStorage storage;
    private final Player viewer;
    private final UUID targetPlayerUuid;
    private final Inventory inventory;
    
    private static final int MAX_PARTNERS = 21; // 3 rows of 7
    private static final int BACK_SLOT = 45;
    private static final int FILTER_SLOT = 49;
    private static final int INFO_SLOT = 4;
    private static final int[] PARTNER_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    
    // Filter modes
    public enum FilterMode { ALL, ONLINE, TOP_PARTNERS }
    private FilterMode currentFilter = FilterMode.ALL;
    
    // Friendship thresholds (in seconds)
    private static final long ACQUAINTANCE_THRESHOLD = 600;    // 10 minutes
    private static final long FRIEND_THRESHOLD = 3600;         // 1 hour  
    private static final long BEST_FRIEND_THRESHOLD = 18000;   // 5 hours

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
                        .append(Component.text(targetName + "'s Friends", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        // Load social pairs
        List<SocialPairRow> allPairs;
        try {
            allPairs = storage.loadSocialPairsForPlayer(targetPlayerUuid, 50);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load social pairs: " + e.getMessage());
            allPairs = new ArrayList<>();
        }
        
        // Apply filter
        List<SocialPairRow> filteredPairs = applyFilter(allPairs);
        
        // Count statistics
        long onlineCount = allPairs.stream()
                .filter(p -> {
                    UUID partnerUuid = p.uuidA().equals(targetPlayerUuid) ? p.uuidB() : p.uuidA();
                    return Bukkit.getPlayer(partnerUuid) != null;
                }).count();
        long bestFriendCount = allPairs.stream()
                .filter(p -> p.seconds() >= BEST_FRIEND_THRESHOLD)
                .count();
        
        // Header with stats
        inventory.setItem(INFO_SLOT, createGuiItem(Material.PLAYER_HEAD,
                Component.text("Your Friends", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Players you've spent time with", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Total: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(allPairs.size() + " friends", NamedTextColor.WHITE)),
                Component.text("Online: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(onlineCount + " now", NamedTextColor.GREEN)),
                Component.text("Best Friends: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(bestFriendCount + "", NamedTextColor.LIGHT_PURPLE))));
        
        if (filteredPairs.isEmpty()) {
            Material emptyMaterial = currentFilter == FilterMode.ALL ? Material.BARRIER : Material.GRAY_DYE;
            String emptyMessage = switch (currentFilter) {
                case ALL -> "No friends found";
                case ONLINE -> "No friends online";
                case TOP_PARTNERS -> "No top partners yet";
            };
            inventory.setItem(22, createGuiItem(emptyMaterial,
                    Component.text(emptyMessage, NamedTextColor.RED),
                    Component.text("Play near other players to", NamedTextColor.GRAY),
                    Component.text("build your social network!", NamedTextColor.GRAY)));
        } else {
            int slot = 0;
            for (SocialPairRow pair : filteredPairs) {
                if (slot >= PARTNER_SLOTS.length) break;
                
                UUID partnerUuid = pair.uuidA().equals(targetPlayerUuid) ? pair.uuidB() : pair.uuidA();
                ItemStack partnerItem = createPartnerItem(partnerUuid, pair, slot + 1);
                inventory.setItem(PARTNER_SLOTS[slot], partnerItem);
                slot++;
            }
        }
        
        // Stats summary row
        long totalTimeTogether = allPairs.stream().mapToLong(SocialPairRow::seconds).sum();
        long totalSharedKills = allPairs.stream().mapToLong(SocialPairRow::sharedKills).sum();
        
        inventory.setItem(46, createGuiItem(Material.CLOCK,
                Component.text("⏱ Total Time Together", NamedTextColor.AQUA),
                Component.text(formatTime(totalTimeTogether), NamedTextColor.WHITE)));
        
        inventory.setItem(47, createGuiItem(Material.DIAMOND_SWORD,
                Component.text("⚔ Shared Kills", NamedTextColor.RED),
                Component.text(formatNumber(totalSharedKills), NamedTextColor.WHITE)));
        
        // Filter button
        Material filterMaterial = switch (currentFilter) {
            case ALL -> Material.HOPPER;
            case ONLINE -> Material.ENDER_EYE;
            case TOP_PARTNERS -> Material.DIAMOND;
        };
        String filterName = switch (currentFilter) {
            case ALL -> "All Friends";
            case ONLINE -> "Online Only";
            case TOP_PARTNERS -> "Top Partners";
        };
        inventory.setItem(FILTER_SLOT, createGuiItem(filterMaterial,
                Component.text("🔍 Filter: " + filterName, NamedTextColor.YELLOW),
                Component.text("Click to cycle filter", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("▸ All Friends", currentFilter == FilterMode.ALL ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                Component.text("▸ Online Only", currentFilter == FilterMode.ONLINE ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                Component.text("▸ Top Partners (5h+)", currentFilter == FilterMode.TOP_PARTNERS ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        
        // Legend
        inventory.setItem(52, createGuiItem(Material.PAPER,
                Component.text("Friendship Levels", NamedTextColor.WHITE),
                Component.empty(),
                Component.text("🌟 Best Friend: ", NamedTextColor.LIGHT_PURPLE).append(Component.text("5+ hours", NamedTextColor.GRAY)),
                Component.text("💛 Friend: ", NamedTextColor.YELLOW).append(Component.text("1+ hour", NamedTextColor.GRAY)),
                Component.text("🤝 Acquaintance: ", NamedTextColor.GREEN).append(Component.text("10+ min", NamedTextColor.GRAY)),
                Component.text("👋 New: ", NamedTextColor.GRAY).append(Component.text("< 10 min", NamedTextColor.GRAY))));
        
        // Back button
        inventory.setItem(BACK_SLOT, createGuiItem(Material.ARROW,
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to player stats", NamedTextColor.GRAY)));
        
        // Refresh button
        inventory.setItem(53, createGuiItem(Material.SUNFLOWER,
                Component.text("🔄 Refresh", NamedTextColor.GREEN),
                Component.text("Reload friend data", NamedTextColor.GRAY)));
        
        fillBackground();
    }
    
    private List<SocialPairRow> applyFilter(List<SocialPairRow> pairs) {
        return switch (currentFilter) {
            case ALL -> pairs.stream().limit(MAX_PARTNERS).collect(Collectors.toList());
            case ONLINE -> pairs.stream()
                    .filter(p -> {
                        UUID partnerUuid = p.uuidA().equals(targetPlayerUuid) ? p.uuidB() : p.uuidA();
                        return Bukkit.getPlayer(partnerUuid) != null;
                    })
                    .limit(MAX_PARTNERS)
                    .collect(Collectors.toList());
            case TOP_PARTNERS -> pairs.stream()
                    .filter(p -> p.seconds() >= BEST_FRIEND_THRESHOLD)
                    .limit(MAX_PARTNERS)
                    .collect(Collectors.toList());
        };
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
            long seconds = pair.seconds();
            
            // Friendship level
            String friendshipIcon;
            NamedTextColor friendshipColor;
            String friendshipLevel;
            if (seconds >= BEST_FRIEND_THRESHOLD) {
                friendshipIcon = "🌟 ";
                friendshipColor = NamedTextColor.LIGHT_PURPLE;
                friendshipLevel = "Best Friend";
            } else if (seconds >= FRIEND_THRESHOLD) {
                friendshipIcon = "💛 ";
                friendshipColor = NamedTextColor.YELLOW;
                friendshipLevel = "Friend";
            } else if (seconds >= ACQUAINTANCE_THRESHOLD) {
                friendshipIcon = "🤝 ";
                friendshipColor = NamedTextColor.GREEN;
                friendshipLevel = "Acquaintance";
            } else {
                friendshipIcon = "👋 ";
                friendshipColor = NamedTextColor.GRAY;
                friendshipLevel = "New";
            }
            
            // Rank medal
            String medal = switch (rank) {
                case 1 -> "🥇 ";
                case 2 -> "🥈 ";
                case 3 -> "🥉 ";
                default -> "";
            };
            
            NamedTextColor nameColor = isOnline ? NamedTextColor.GREEN : friendshipColor;
            meta.displayName(Component.text(medal + friendshipIcon + name, nameColor).decorate(TextDecoration.BOLD));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(friendshipLevel, friendshipColor));
            lore.add(Component.empty());
            lore.add(Component.text("⏱ Time Together: ", NamedTextColor.AQUA)
                    .append(Component.text(formatTime(pair.seconds()), NamedTextColor.WHITE)));
            lore.add(Component.text("⚔ Shared Kills: ", NamedTextColor.RED)
                    .append(Component.text(formatNumber(pair.sharedKills()), NamedTextColor.WHITE)));
            
            if (pair.sharedMobKills() > 0 || pair.sharedPlayerKills() > 0) {
                lore.add(Component.empty());
                lore.add(Component.text("  Mob: " + formatNumber(pair.sharedMobKills()), NamedTextColor.GRAY));
                lore.add(Component.text("  PvP: " + formatNumber(pair.sharedPlayerKills()), NamedTextColor.GRAY));
            }
            
            lore.add(Component.empty());
            if (isOnline) {
                lore.add(Component.text("🟢 Online now!", NamedTextColor.GREEN));
                lore.add(Component.text("Click to view stats", NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("⚫ Offline", NamedTextColor.GRAY));
            }
            
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        
        return head;
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
            // Go back to PlayerStatsGui
            Player target = Bukkit.getPlayer(targetPlayerUuid);
            if (target != null) {
                guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, target));
            } else {
                player.closeInventory();
            }
        } else if (slot == FILTER_SLOT) {
            // Cycle filter
            currentFilter = switch (currentFilter) {
                case ALL -> FilterMode.ONLINE;
                case ONLINE -> FilterMode.TOP_PARTNERS;
                case TOP_PARTNERS -> FilterMode.ALL;
            };
            playSuccessSound(player);
            initializeItems();
        } else if (slot == 53) {
            // Refresh
            playSuccessSound(player);
            initializeItems();
        } else {
            // Check if clicked on a partner slot
            for (int partnerSlot : PARTNER_SLOTS) {
                if (partnerSlot == slot) {
                    ItemStack item = inventory.getItem(slot);
                    if (item != null && item.getType() == Material.PLAYER_HEAD) {
                        SkullMeta meta = (SkullMeta) item.getItemMeta();
                        if (meta != null && meta.getOwningPlayer() != null) {
                            UUID partnerUuid = meta.getOwningPlayer().getUniqueId();
                            Player partnerPlayer = Bukkit.getPlayer(partnerUuid);
                            if (partnerPlayer != null) {
                                playSuccessSound(player);
                                guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, partnerPlayer));
                            } else {
                                player.sendMessage(Component.text("This player is offline", NamedTextColor.RED));
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
