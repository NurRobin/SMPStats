package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.HealthSnapshot;
import de.nurrobin.smpstats.health.ServerHealthService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class HotChunksGuiTest {
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
    void showsNoHotChunksMessageWhenEmpty() {
        when(healthService.getLatest()).thenReturn(null);
        
        HotChunksGui gui = new HotChunksGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();
        
        assertNotNull(inv.getItem(22));
        assertEquals(Material.BARRIER, inv.getItem(22).getType());
    }

    @Test
    void showsHotChunksAndTeleports() {
        World world = server.addSimpleWorld("world");
        HealthSnapshot.HotChunk hotChunk = new HealthSnapshot.HotChunk("world", 10, 10, 5, 2, "Owner");
        HealthSnapshot snapshot = new HealthSnapshot(
            System.currentTimeMillis(), 20.0, 100, 200, 1, 5, 0, 2, 10.0, 
            Collections.emptyMap(), List.of(hotChunk)
        );
        when(healthService.getLatest()).thenReturn(snapshot);

        HotChunksGui gui = new HotChunksGui(plugin, guiManager, healthService);
        Inventory inv = gui.getInventory();

        assertNotNull(inv.getItem(0));
        assertEquals(Material.MAGMA_BLOCK, inv.getItem(0).getType());

        // Test Click
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        assertEquals(10 * 16 + 8, player.getLocation().getBlockX());
        assertEquals(10 * 16 + 8, player.getLocation().getBlockZ());
    }
    
    @Test
    void backButtonNavigatesToServerHealth() {
        HotChunksGui gui = new HotChunksGui(plugin, guiManager, healthService);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(49);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(ServerHealthGui.class));
    }
}
