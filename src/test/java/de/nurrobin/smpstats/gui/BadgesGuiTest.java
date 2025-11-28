package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the BadgesGui class.
 */
class BadgesGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private StatsService statsService;
    private GuiManager guiManager;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        statsService = mock(StatsService.class);
        guiManager = new GuiManager(plugin);
        player = server.addPlayer("TestPlayer");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private StatsRecord createRecordWithBadges() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), "TestPlayer");
        record.setMobKills(100); // hunter badge
        record.setDeaths(5);
        record.setBlocksBroken(10000);
        record.setBlocksPlaced(5000);
        record.setDistanceOverworld(50000);
        record.setDistanceNether(1000);
        record.setDistanceEnd(500);
        record.setDamageDealt(1000);
        record.setDamageTaken(500);
        record.setFirstJoin(System.currentTimeMillis() - 86400000);
        record.setLastJoin(System.currentTimeMillis());
        record.setLastDeathCause("fell from a high place");
        record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(25)); // 25 hours
        record.setItemsCrafted(1000);
        return record;
    }

    @Test
    void testCreateInventory() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void testHeaderShowsBadgeCount() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        // Header should be at slot 4
        ItemStack header = inventory.getItem(4);
        assertNotNull(header);
        assertEquals(Material.GOLDEN_HELMET, header.getType());
        
        // Should have badge collection title
        Component displayName = header.getItemMeta().displayName();
        assertNotNull(displayName);
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("Badge"));
    }

    @Test
    void testBackButtonExists() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        ItemStack backButton = inventory.getItem(45);
        assertNotNull(backButton);
        assertEquals(Material.ARROW, backButton.getType());
    }

    @Test
    void testToggleUnearnedButtonExists() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        ItemStack toggleButton = inventory.getItem(47);
        assertNotNull(toggleButton);
        // Should be ENDER_PEARL when showing earned only
        assertEquals(Material.ENDER_PEARL, toggleButton.getType());
    }

    @Test
    void testRefreshButtonExists() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        ItemStack refreshButton = inventory.getItem(53);
        assertNotNull(refreshButton);
        assertEquals(Material.SUNFLOWER, refreshButton.getType());
    }

    @Test
    void testCategoryFilterButtons() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        // Slot 9 should be "All" filter
        ItemStack allFilter = inventory.getItem(9);
        assertNotNull(allFilter);
        assertEquals(Material.BOOK, allFilter.getType());
        
        // Slots 10-16 should have category filters
        for (int i = 10; i <= 16; i++) {
            ItemStack filter = inventory.getItem(i);
            assertNotNull(filter, "Category filter at slot " + i + " should exist");
        }
    }

    @Test
    void testEarnedBadgesDisplayed() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        // Badge slots are 10-16, 19-25, 28-34
        // At least one should have a non-background item (earned badge)
        boolean hasBadge = false;
        int[] badgeSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int slot : badgeSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.BLACK_STAINED_GLASS_PANE) {
                hasBadge = true;
                break;
            }
        }
        
        assertTrue(hasBadge, "Should display at least one earned badge");
    }

    @Test
    void testNoStatsShowsBarrier() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.empty());
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        ItemStack barrier = inventory.getItem(22);
        assertNotNull(barrier);
        assertEquals(Material.BARRIER, barrier.getType());
    }

    @Test
    void testHandleRefreshClick() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                53, // Refresh button
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        
        gui.handleClick(event);
        
        // Should have refreshed (called getStats again)
        verify(statsService, atLeast(2)).getStats(player.getUniqueId());
    }

    @Test
    void testHandleToggleClick() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        // Before toggle: should show ENDER_PEARL
        assertEquals(Material.ENDER_PEARL, inventory.getItem(47).getType());
        
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                47, // Toggle button
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        
        gui.handleClick(event);
        
        // After toggle: should show ENDER_EYE
        assertEquals(Material.ENDER_EYE, inventory.getItem(47).getType());
    }

    @Test
    void testPageIndicatorExists() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        Inventory inventory = gui.getInventory();
        
        ItemStack pageIndicator = inventory.getItem(49);
        assertNotNull(pageIndicator);
        assertEquals(Material.PAPER, pageIndicator.getType());
        
        // Should show page info
        Component displayName = pageIndicator.getItemMeta().displayName();
        assertNotNull(displayName);
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("Page"));
    }

    @Test
    void testOpenMethod() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(createRecordWithBadges()));
        
        BadgesGui gui = new BadgesGui(plugin, guiManager, statsService, player, player.getUniqueId());
        gui.open(player);
        
        assertNotNull(player.getOpenInventory());
    }
}
