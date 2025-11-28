package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.health.ServerHealthService;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MainMenuGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private GuiManager guiManager;
    private StatsService statsService;
    private ServerHealthService healthService;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        guiManager = mock(GuiManager.class);
        statsService = mock(StatsService.class);
        healthService = mock(ServerHealthService.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void showsMenuItemsAfterOpen() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        Inventory inv = gui.getInventory();

        // My Stats at slot 20 (player head)
        assertNotNull(inv.getItem(20));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(20).getType());

        // Server Health at slot 22 (redstone block for player with permission)
        assertNotNull(inv.getItem(22));
        // Material depends on permission, but should exist
        assertNotNull(inv.getItem(22).getType());

        // Leaderboards at slot 24
        assertNotNull(inv.getItem(24));
        // Material depends on permission
        assertNotNull(inv.getItem(24).getType());
    }

    @Test
    void hasCorrectInventorySize() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        
        assertEquals(45, gui.getInventory().getSize());
    }

    @Test
    void hasHeaderInfo() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        Inventory inv = gui.getInventory();

        // Header info at slot 4
        assertNotNull(inv.getItem(4));
        assertEquals(Material.NETHER_STAR, inv.getItem(4).getType());
    }

    @Test
    void hasCloseButton() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        Inventory inv = gui.getInventory();

        // Close button at slot 40
        assertNotNull(inv.getItem(40));
        assertEquals(Material.BARRIER, inv.getItem(40).getType());
    }

    @Test
    void opensMyStats() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(20); // New slot for My Stats
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(PlayerStatsGui.class));
    }

    @Test
    void opensServerHealthWithPermission() {
        player.addAttachment(plugin, "smpstats.health", true);
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(22); // New slot for Server Health
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(ServerHealthGui.class));
    }

    @Test
    void deniesServerHealthWithoutPermission() {
        // Player doesn't have permission by default
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(22); // New slot for Server Health
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager, never()).openGui(eq(player), any(ServerHealthGui.class));
    }

    @Test
    void opensLeaderboards() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(24); // New slot for Leaderboards
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(LeaderboardsGui.class));
    }

    @Test
    void closeButtonClosesInventory() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(40); // Close button
        when(event.getWhoClicked()).thenReturn(player);
        
        // Should not throw and should not open any other GUI
        assertDoesNotThrow(() -> gui.handleClick(event));
        verify(guiManager, never()).openGui(eq(player), any());
    }

    @Test
    void hasQuickTipsItems() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        Inventory inv = gui.getInventory();

        // Quick tips at slot 37
        assertNotNull(inv.getItem(37));
        assertEquals(Material.BOOK, inv.getItem(37).getType());
        
        // Web dashboard info at slot 43
        assertNotNull(inv.getItem(43));
        assertEquals(Material.COMPASS, inv.getItem(43).getType());
    }

    @Test
    void opensServerHealthWithNewPermission() {
        player.addAttachment(plugin, "smpstats.gui.health", true);
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(22);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(ServerHealthGui.class));
    }
}
