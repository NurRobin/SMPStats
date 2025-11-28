package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

import static de.nurrobin.smpstats.gui.GuiUtils.*;

/**
 * Secondary menu containing less frequently used features.
 * Helps declutter the main PlayerStatsGui while keeping features accessible.
 */
public class MoreStatsGui implements InventoryGui, InventoryHolder {
    private final SMPStats plugin;
    private final GuiManager guiManager;
    private final StatsService statsService;
    private final Player viewer;
    private final UUID targetUuid;
    private final String targetName;
    private final Inventory inventory;

    public MoreStatsGui(SMPStats plugin, GuiManager guiManager, StatsService statsService,
                        Player viewer, UUID targetUuid, String targetName) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.statsService = statsService;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.inventory = Bukkit.createInventory(this, 27,
                Component.text("📋 ", NamedTextColor.GOLD)
                        .append(Component.text("More Stats", NamedTextColor.WHITE)));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();
        
        // Fill background
        var filler = createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        
        // Header
        inventory.setItem(4, createGuiItem(Material.BOOK,
                Component.text("Additional Stats & Tools", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("Less common but useful features", NamedTextColor.GRAY)));
        
        // === Row 2: Features ===
        
        // Death History (slot 10)
        inventory.setItem(10, createGuiItem(Material.SKELETON_SKULL,
                Component.text("💀 Death History", NamedTextColor.DARK_RED),
                Component.text("Review your recent deaths", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("See cause, location, and items lost", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("▶ Click to view", NamedTextColor.GREEN)));
        
        // Weekly Progress / Timeline Delta (slot 12)
        inventory.setItem(12, createGuiItem(Material.RECOVERY_COMPASS,
                Component.text("📈 Weekly Progress", NamedTextColor.AQUA),
                Component.text("This week vs last week", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Track your improvement over time", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("▶ Click to view", NamedTextColor.GREEN)));
        
        // Activity Heatmap (slot 14)
        inventory.setItem(14, createGuiItem(Material.FILLED_MAP,
                Component.text("🗺 Activity Heatmap", NamedTextColor.RED),
                Component.text("Visual activity around you", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("See where players are active", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("▶ Click to view", NamedTextColor.GREEN)));
        
        // Moments / Achievements (slot 16)
        inventory.setItem(16, createGuiItem(Material.NETHER_STAR,
                Component.text("🏆 Moments", NamedTextColor.GOLD),
                Component.text("Your triggered achievements", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Diamond runs, boss kills, etc.", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("▶ Click to view", NamedTextColor.GREEN)));
        
        // Back button (slot 22)
        inventory.setItem(22, createGuiItem(Material.ARROW,
                Component.text("◀ Back", NamedTextColor.RED),
                Component.text("Return to player stats", NamedTextColor.GRAY)));
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
        
        switch (slot) {
            case 10 -> { // Death History
                plugin.getStatsStorage().ifPresentOrElse(
                    storage -> guiManager.openGui(player, new DeathReplayGui(plugin, guiManager, statsService,
                            storage, player, targetUuid)),
                    () -> player.sendMessage(Component.text("Death history not available", NamedTextColor.RED))
                );
            }
            case 12 -> { // Weekly Progress
                plugin.getStatsStorage().ifPresentOrElse(
                    storage -> guiManager.openGui(player, new TimelineDeltaGui(plugin, guiManager, statsService,
                            storage, player, targetUuid)),
                    () -> player.sendMessage(Component.text("Timeline stats not available", NamedTextColor.RED))
                );
            }
            case 14 -> { // Heatmap
                guiManager.openGui(player, new PersonalHeatmapGui(plugin, guiManager, statsService, player,
                        targetUuid, targetName));
            }
            case 16 -> { // Moments
                plugin.getMomentService().ifPresentOrElse(
                    momentService -> guiManager.openGui(player, new MomentsHistoryGui(plugin, guiManager, statsService,
                            momentService, player, targetUuid)),
                    () -> player.sendMessage(Component.text("Achievements not available", NamedTextColor.RED))
                );
            }
            case 22 -> { // Back
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null) {
                    guiManager.openGui(player, new PlayerStatsGui(plugin, guiManager, statsService, target));
                } else {
                    player.closeInventory();
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
