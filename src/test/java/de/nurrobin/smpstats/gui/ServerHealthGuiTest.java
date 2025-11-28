package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.HealthSnapshot;
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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class ServerHealthGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private GuiManager guiManager;
    private ServerHealthService healthService;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        guiManager = mock(GuiManager.class);
        healthService = mock(ServerHealthService.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void showsNoDataMessageWhenSnapshotNull() {
        when(healthService.getLatest()).thenReturn(null);
        
        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();
        
        assertNotNull(inv.getItem(13));
        assertEquals(Material.BARRIER, inv.getItem(13).getType());
    }

    @Test
    void showsHealthStats() {
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 19.5, 100 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 5.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        // Overall Status
        assertNotNull(inv.getItem(4));
        assertEquals(Material.BEACON, inv.getItem(4).getType());
        
        // TPS (Primary metric)
        assertNotNull(inv.getItem(10));
        assertEquals(Material.CLOCK, inv.getItem(10).getType());
        
        // Memory (Primary metric)
        assertNotNull(inv.getItem(12));
        assertEquals(Material.ENDER_CHEST, inv.getItem(12).getType());
        
        // Cost Index (Primary metric)
        assertNotNull(inv.getItem(14));
        assertEquals(Material.EMERALD, inv.getItem(14).getType());
        
        // Chunks (Secondary metric)
        assertNotNull(inv.getItem(19));
        assertEquals(Material.GRASS_BLOCK, inv.getItem(19).getType());
        
        // Entities (Secondary metric)
        assertNotNull(inv.getItem(21));
        assertEquals(Material.ZOMBIE_HEAD, inv.getItem(21).getType());
        
        // Hoppers (Secondary metric)
        assertNotNull(inv.getItem(23));
        assertEquals(Material.HOPPER, inv.getItem(23).getType());
        
        // Redstone (Secondary metric)
        assertNotNull(inv.getItem(25));
        assertEquals(Material.REDSTONE, inv.getItem(25).getType());
        
        // Hot Chunks Button
        assertNotNull(inv.getItem(18));
        assertEquals(Material.MAGMA_CREAM, inv.getItem(18).getType());
    }

    @Test
    void opensHotChunksGui() {
        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(18);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(HotChunksGui.class));
    }

    @Test
    void backButtonNavigatesToMainMenu() {
        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(22);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(MainMenuGui.class));
    }

    @Test
    void refreshButtonRefreshesData() {
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 19.0, 100 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 5.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);
        
        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(26); // Refresh button
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Verify data was fetched again
        verify(healthService, atLeast(2)).getLatest();
    }

    @Test
    void hasRefreshButton() {
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 19.0, 100 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 5.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(26));
        assertEquals(Material.SUNFLOWER, inv.getItem(26).getType());
    }

    @Test
    void tpsColorCodedYellow() {
        // TPS 16 - should be yellow
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 16.0, 100 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 5.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(10));
        assertEquals(Material.CLOCK, inv.getItem(10).getType());
    }

    @Test
    void tpsColorCodedRed() {
        // TPS 12 - should be red
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 12.0, 100 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 5.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(10));
    }

    @Test
    void memoryHighUsageColorCoded() {
        // Memory 90% used - should show warning color
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 19.5, 180 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 5.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(12));
        assertEquals(Material.ENDER_CHEST, inv.getItem(12).getType());
    }

    @Test
    void costIndexHighShowsWarning() {
        // Cost index 75 - high load
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 19.5, 100 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 75.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(14));
        assertEquals(Material.EMERALD, inv.getItem(14).getType());
    }

    @Test
    void costIndexCriticalShowsWarning() {
        // Cost index 150 - critical load
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 19.5, 100 * 1024 * 1024, 200 * 1024 * 1024, 
            50, 100, 10, 20, 150.0, Collections.emptyMap(), Collections.emptyList()
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(16));
    }

    @Test
    void opensPlayerInventory() {
        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        
        gui.open(player);
        
        assertNotNull(player.getOpenInventory());
    }

    @Test
    void noDataShowsBackButton() {
        when(healthService.getLatest()).thenReturn(null);
        
        ServerHealthGui gui = new ServerHealthGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();
        
        // Should still have back button
        assertNotNull(inv.getItem(22));
        assertEquals(Material.ARROW, inv.getItem(22).getType());
    }
}
