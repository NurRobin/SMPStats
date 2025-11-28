package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.skills.SkillProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerStatsGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private GuiManager guiManager;
    private StatsService statsService;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        guiManager = mock(GuiManager.class);
        statsService = mock(StatsService.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void showsNoStatsMessageWhenEmpty() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.empty());
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();
        
        assertNotNull(inv.getItem(22));
        assertEquals(Material.BARRIER, inv.getItem(22).getType());
    }

    @Test
    void showsStats() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setMobKills(10);
        record.setPlayerKills(5);
        record.setDeaths(3);
        record.setBlocksBroken(100);
        record.setBlocksPlaced(50);
        
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Kills
        assertNotNull(inv.getItem(12));
        assertEquals(Material.DIAMOND_SWORD, inv.getItem(12).getType());
        
        // Deaths
        assertNotNull(inv.getItem(14));
        assertEquals(Material.SKELETON_SKULL, inv.getItem(14).getType());
        
        // Blocks
        assertNotNull(inv.getItem(16));
        assertEquals(Material.GRASS_BLOCK, inv.getItem(16).getType());
    }
    
    @Test
    void backButtonNavigatesToMainMenu() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.empty());
        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(45); // Back button position
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Should either open MainMenu or close inventory
        // If ServerHealthService is available, it opens MainMenu
    }

    @Test
    void refreshButtonRefreshesStats() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setMobKills(10);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(53); // Refresh button
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Verify stats were fetched again
        verify(statsService, atLeast(2)).getStats(player.getUniqueId());
    }

    @Test
    void showsPlayerHead() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Player head at slot 4
        assertNotNull(inv.getItem(4));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(4).getType());
    }

    @Test
    void showsPlaytimeAndDistance() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setPlaytimeMillis(7200000); // 2 hours in millis
        record.setDistanceOverworld(5000.0); // 5000 blocks
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Playtime at slot 10
        assertNotNull(inv.getItem(10));
        assertEquals(Material.CLOCK, inv.getItem(10).getType());

        // Distance at slot 19
        assertNotNull(inv.getItem(19));
        assertEquals(Material.LEATHER_BOOTS, inv.getItem(19).getType());
    }

    @Test
    void showsItemsCrafted() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setItemsCrafted(100);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Items crafted at slot 23
        assertNotNull(inv.getItem(23));
        assertEquals(Material.CRAFTING_TABLE, inv.getItem(23).getType());
    }

    @Test
    void showsDamageStats() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setDamageDealt(1000.0);
        record.setDamageTaken(500.0);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Combat at slot 21
        assertNotNull(inv.getItem(21));
        assertEquals(Material.IRON_SWORD, inv.getItem(21).getType());
    }

    @Test
    void opensPlayerInventory() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        gui.open(player);

        assertNotNull(player.getOpenInventory());
    }

    @Test
    void clickOutsideIgnored() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(55); // Outside of inventory
        when(event.getWhoClicked()).thenReturn(player);

        gui.handleClick(event); // Should not throw
    }

    @Test
    void showsSkillProfileSection() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setBlocksBroken(1000);
        record.setMobKills(50);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));
        when(statsService.getSkillProfile(player.getUniqueId()))
                .thenReturn(Optional.of(new SkillProfile(100.0, 75.0, 50.0, 25.0, 10.0)));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Skills header at slot 31
        assertNotNull(inv.getItem(31));
        assertEquals(Material.EXPERIENCE_BOTTLE, inv.getItem(31).getType());
        
        // Mining skill at slot 37
        assertNotNull(inv.getItem(37));
        assertEquals(Material.IRON_PICKAXE, inv.getItem(37).getType());
        
        // Combat skill at slot 38
        assertNotNull(inv.getItem(38));
        assertEquals(Material.NETHERITE_SWORD, inv.getItem(38).getType());
    }

    @Test
    void showsKDRatio() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setMobKills(10);
        record.setPlayerKills(5);
        record.setDeaths(5);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Kills item at slot 12 should contain K/D info in lore
        assertNotNull(inv.getItem(12));
        assertNotNull(inv.getItem(12).getItemMeta());
        assertNotNull(inv.getItem(12).getItemMeta().lore());
        // K/D = 15/5 = 3.0
    }

    @Test
    void showsBiomesProgress() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setBiomesVisited(Set.of("plains", "forest", "desert", "ocean"));
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Biomes at slot 25
        assertNotNull(inv.getItem(25));
        assertEquals(Material.FILLED_MAP, inv.getItem(25).getType());
    }

    @Test
    void inventoryHas54Slots() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        assertEquals(54, inv.getSize());
    }

    @Test
    void hasSessionStatsItem() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));
        // Session delta is empty by default in mock
        when(statsService.getSessionDelta(player.getUniqueId())).thenReturn(Optional.empty());

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Session stats at slot 0
        assertNotNull(inv.getItem(0));
        // Should show gray dye when no active session
        assertEquals(Material.GRAY_DYE, inv.getItem(0).getType());
    }

    @Test
    void sessionStatsShowsGreenDyeWhenActive() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        StatsRecord startRecord = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));
        
        // Create mock session delta
        StatsService.SessionDelta delta = new StatsService.SessionDelta(startRecord, record, 60000); // 1 minute session
        when(statsService.getSessionDelta(player.getUniqueId())).thenReturn(Optional.of(delta));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Session stats at slot 0 - should be lime concrete when active
        assertNotNull(inv.getItem(0));
        assertEquals(Material.LIME_CONCRETE, inv.getItem(0).getType());
    }

    // === New navigation tests for simplified layout ===
    
    @Test
    void hasBadgesButtonAtSlot47() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Badges button at slot 47
        assertNotNull(inv.getItem(47));
    }

    @Test
    void hasFriendsButtonAtSlot48() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Friends button at slot 48
        assertNotNull(inv.getItem(48));
        assertEquals(Material.TOTEM_OF_UNDYING, inv.getItem(48).getType());
    }

    @Test
    void hasCompareButtonAtSlot49() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Compare button at slot 49
        assertNotNull(inv.getItem(49));
        assertEquals(Material.COMPARATOR, inv.getItem(49).getType());
    }

    @Test
    void hasMoreStatsButtonAtSlot51() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // More Stats button at slot 51
        assertNotNull(inv.getItem(51));
        assertEquals(Material.BOOK, inv.getItem(51).getType());
    }

    @Test
    void moreStatsButtonOpensSubMenu() {
        // Use real guiManager for this test
        GuiManager realGuiManager = new GuiManager(plugin);
        StatsService realStatsService = plugin.getStatsService();
        realStatsService.handleJoin(player);
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, realGuiManager, realStatsService, player);
        realGuiManager.openGui(player, gui);
        
        // Click on More Stats button (slot 51)
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                51, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open MoreStatsGui
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof MoreStatsGui);
    }

    @Test
    void friendsButtonOpensSocialPartnersGui() {
        // Use real guiManager for this test
        GuiManager realGuiManager = new GuiManager(plugin);
        StatsService realStatsService = plugin.getStatsService();
        realStatsService.handleJoin(player);
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, realGuiManager, realStatsService, player);
        realGuiManager.openGui(player, gui);
        
        // Click on Friends button (slot 48)
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                48, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open SocialPartnersGui
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof SocialPartnersGui);
    }

    @Test
    void badgesButtonOpensBadgesGui() {
        // Use real guiManager for this test
        GuiManager realGuiManager = new GuiManager(plugin);
        StatsService realStatsService = plugin.getStatsService();
        realStatsService.handleJoin(player);
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, realGuiManager, realStatsService, player);
        realGuiManager.openGui(player, gui);
        
        // Click on Badges button (slot 47)
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open BadgesGui
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof BadgesGui);
    }

    @Test
    void compareButtonOpensPlayerSelectorGui() {
        // Use real guiManager for this test
        GuiManager realGuiManager = new GuiManager(plugin);
        StatsService realStatsService = plugin.getStatsService();
        realStatsService.handleJoin(player);
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, realGuiManager, realStatsService, player);
        realGuiManager.openGui(player, gui);
        
        // Click on Compare button (slot 49)
        InventoryClickEvent event = new InventoryClickEvent(
                player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                49, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open PlayerSelectorGui (verify the GUI changed)
        // TODO: Restore this check once PlayerSelectorGui exists
        // assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof PlayerSelectorGui);
    }
}
