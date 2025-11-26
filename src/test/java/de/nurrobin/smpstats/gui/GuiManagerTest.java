package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuiManagerTest {
    private ServerMock server;
    private SMPStats plugin;
    private GuiManager guiManager;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        guiManager = new GuiManager(plugin);
        player = server.addPlayer();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testOpenGui() {
        InventoryGui mockGui = mock(InventoryGui.class);
        Inventory mockInventory = server.createInventory(null, 9);
        when(mockGui.getInventory()).thenReturn(mockInventory);

        guiManager.openGui(player, mockGui);

        verify(mockGui).open(player);
        // We can't easily verify internal state of GuiManager without reflection or getters,
        // but we can verify behavior on click.
    }

    @Test
    void testInventoryClick() {
        InventoryGui mockGui = mock(InventoryGui.class);
        Inventory mockInventory = server.createInventory(null, 9);
        when(mockGui.getInventory()).thenReturn(mockInventory);

        guiManager.openGui(player, mockGui);

        // Simulate click
        // We need to construct an InventoryClickEvent or trigger it via MockBukkit
        // MockBukkit handles events if registered.
        // GuiManager is not registered in this test setup unless we register it manually.
        server.getPluginManager().registerEvents(guiManager, plugin);

        player.openInventory(mockInventory);
        // This is tricky with MockBukkit to simulate exact click on specific inventory
        // simpler to call the event handler directly for unit testing logic.
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(mockInventory);
        
        guiManager.onInventoryClick(event);
        
        verify(event).setCancelled(true);
        verify(mockGui).handleClick(event);
    }
    
    @Test
    void testInventoryClickWrongInventory() {
        InventoryGui mockGui = mock(InventoryGui.class);
        Inventory mockInventory = server.createInventory(null, 9);
        when(mockGui.getInventory()).thenReturn(mockInventory);

        guiManager.openGui(player, mockGui);
        
        Inventory otherInventory = server.createInventory(null, 9);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(otherInventory);
        
        guiManager.onInventoryClick(event);
        
        verify(event, never()).setCancelled(true);
        verify(mockGui, never()).handleClick(event);
    }

    @Test
    void testInventoryDrag() {
        InventoryGui mockGui = mock(InventoryGui.class);
        Inventory mockInventory = server.createInventory(null, 9);
        when(mockGui.getInventory()).thenReturn(mockInventory);

        guiManager.openGui(player, mockGui);

        org.bukkit.event.inventory.InventoryDragEvent event = mock(org.bukkit.event.inventory.InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(mockInventory);

        guiManager.onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void testInventoryClose() {
        InventoryGui mockGui = mock(InventoryGui.class);
        Inventory mockInventory = server.createInventory(null, 9);
        when(mockGui.getInventory()).thenReturn(mockInventory);

        guiManager.openGui(player, mockGui);

        org.bukkit.event.inventory.InventoryCloseEvent event = mock(org.bukkit.event.inventory.InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(mockInventory);

        guiManager.onInventoryClose(event);

        // Verify it's removed by checking if click no longer works
        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getInventory()).thenReturn(mockInventory);

        guiManager.onInventoryClick(clickEvent);

        verify(mockGui, never()).handleClick(clickEvent);
    }
}
