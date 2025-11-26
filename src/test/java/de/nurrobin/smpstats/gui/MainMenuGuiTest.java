package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void showsMenuItems() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        Inventory inv = gui.getInventory();

        // My Stats
        assertNotNull(inv.getItem(11));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(11).getType());

        // Server Health
        assertNotNull(inv.getItem(13));
        assertEquals(Material.REDSTONE_BLOCK, inv.getItem(13).getType());

        // Leaderboards
        assertNotNull(inv.getItem(15));
        assertEquals(Material.GOLD_INGOT, inv.getItem(15).getType());
    }

    @Test
    void opensMyStats() {
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(11);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(PlayerStatsGui.class));
    }

    @Test
    void opensServerHealthWithPermission() {
        player.addAttachment(plugin, "smpstats.health", true);
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(13);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(ServerHealthGui.class));
    }

    @Test
    void deniesServerHealthWithoutPermission() {
        // Player doesn't have permission by default
        MainMenuGui gui = new MainMenuGui(plugin, guiManager, statsService, healthService);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(13);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager, never()).openGui(eq(player), any(ServerHealthGui.class));
        // Should send message
        // assertEquals(Component.text("You do not have permission to view server health.", NamedTextColor.RED), player.nextComponentMessage());
    }
}
