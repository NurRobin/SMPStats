package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.heatmap.HeatmapBin;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalHeatmapGuiTest {

    private ServerMock server;
    private SMPStats plugin;
    private GuiManager guiManager;
    private StatsService statsService;
    private HeatmapService heatmapService;
    private PlayerMock viewer;
    private UUID targetUuid;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(SMPStats.class);
        guiManager = mock(GuiManager.class);
        statsService = mock(StatsService.class);
        heatmapService = mock(HeatmapService.class);
        
        // Setup mock player
        viewer = server.addPlayer("TestViewer");
        targetUuid = UUID.randomUUID();
        
        // Configure plugin mocks
        when(plugin.getHeatmapService()).thenReturn(Optional.of(heatmapService));
        
        // Configure heatmap service to return empty bins by default
        when(heatmapService.generateHeatmap(anyString(), anyString(), anyLong(), anyLong(), anyDouble()))
                .thenReturn(new ArrayList<>());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testOpen_createsInventoryWithCorrectSize() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        Inventory inv = gui.getInventory();
        assertNotNull(inv);
        assertEquals(54, inv.getSize()); // 6 rows
    }

    @Test
    void testOpen_hasActivityTypeSelectorAtSlot0() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        ItemStack item = gui.getInventory().getItem(0);
        assertNotNull(item);
        assertEquals(Material.COMPASS, item.getType());
    }

    @Test
    void testOpen_hasWorldSelectorAtSlot8() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        ItemStack item = gui.getInventory().getItem(8);
        assertNotNull(item);
        assertEquals(Material.GRASS_BLOCK, item.getType());
    }

    @Test
    void testOpen_hasBackButtonAtSlot49() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        ItemStack item = gui.getInventory().getItem(49);
        assertNotNull(item);
        assertEquals(Material.ARROW, item.getType());
    }

    @Test
    void testOpen_rendersGridWithGrayGlassWhenNoData() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        // Check first row of grid (slots 10-16)
        for (int col = 0; col < 7; col++) {
            int slot = 10 + col;
            ItemStack item = gui.getInventory().getItem(slot);
            assertNotNull(item, "Slot " + slot + " should have an item");
            // Should be gray when no activity or player head for current chunk
            assertTrue(item.getType() == Material.GRAY_STAINED_GLASS_PANE || item.getType() == Material.PLAYER_HEAD,
                    "Slot " + slot + " should be gray glass pane or player head");
        }
    }

    @Test
    void testOpen_rendersPlayerChunkWithPlayerHead() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        // The player's current chunk should be marked with a player head
        // Center chunk is at grid position (3, 2) -> slot 10 + 2*9 + 3 = 31
        ItemStack item = gui.getInventory().getItem(31);
        assertNotNull(item);
        assertEquals(Material.PLAYER_HEAD, item.getType());
    }

    @Test
    void testOpen_rendersLegendInBottomRow() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        // Legend slots: 46, 47, 48, 50, 51, 52, 53
        int[] legendSlots = {46, 47, 48, 50, 51, 52, 53};
        for (int slot : legendSlots) {
            ItemStack item = gui.getInventory().getItem(slot);
            assertNotNull(item, "Legend slot " + slot + " should have an item");
            assertTrue(item.getType().name().contains("STAINED_GLASS_PANE"),
                    "Legend slot " + slot + " should be stained glass pane");
        }
    }

    @Test
    void testHandleClick_cyclesActivityTypeOnSlot0Click() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        // Get initial activity type from lore
        ItemStack initialItem = gui.getInventory().getItem(0);
        assertNotNull(initialItem);
        
        // Simulate click on slot 0
        InventoryClickEvent event = createClickEvent(gui.getInventory(), 0);
        gui.handleClick(event);
        
        assertTrue(event.isCancelled());
        // After click, activity should have changed - service should be called with new type
        verify(heatmapService, atLeast(2)).generateHeatmap(anyString(), anyString(), anyLong(), anyLong(), anyDouble());
    }

    @Test
    void testHandleClick_cyclesWorldOnSlot8Click() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        // Simulate click on slot 8
        InventoryClickEvent event = createClickEvent(gui.getInventory(), 8);
        gui.handleClick(event);
        
        assertTrue(event.isCancelled());
        // World should have cycled - heatmap service should be called again
        verify(heatmapService, atLeast(2)).generateHeatmap(anyString(), anyString(), anyLong(), anyLong(), anyDouble());
    }

    @Test
    void testHandleClick_cancelsClickOnGridSlots() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        // Click on grid slot (e.g., slot 15)
        InventoryClickEvent event = createClickEvent(gui.getInventory(), 15);
        gui.handleClick(event);
        
        assertTrue(event.isCancelled());
    }

    @Test
    void testOpen_displaysHeatmapDataWhenAvailable() {
        // Setup heatmap bins
        List<HeatmapBin> bins = new ArrayList<>();
        int viewerChunkX = viewer.getLocation().getBlockX() >> 4;
        int viewerChunkZ = viewer.getLocation().getBlockZ() >> 4;
        bins.add(new HeatmapBin("POSITION", "world", viewerChunkX + 1, viewerChunkZ, 16, 50.0));
        
        when(heatmapService.generateHeatmap(eq("POSITION"), anyString(), anyLong(), anyLong(), anyDouble()))
                .thenReturn(bins);
        
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        // The chunk with data should have a colored glass pane (not gray)
        // The chunk at (viewerChunkX + 1, viewerChunkZ) is at grid position (4, 2) -> slot 10 + 2*9 + 4 = 32
        ItemStack item = gui.getInventory().getItem(32);
        assertNotNull(item);
        assertNotEquals(Material.GRAY_STAINED_GLASS_PANE, item.getType());
    }

    @Test
    void testGetInventory_returnsSameInventory() {
        PersonalHeatmapGui gui = new PersonalHeatmapGui(plugin, guiManager, statsService, viewer, targetUuid, "TestPlayer");
        gui.open(viewer);
        
        Inventory inv1 = gui.getInventory();
        Inventory inv2 = gui.getInventory();
        
        assertSame(inv1, inv2);
    }

    private InventoryClickEvent createClickEvent(Inventory inventory, int slot) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        
        InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                slot,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        
        // Mock the whoClicked
        try {
            var field = InventoryClickEvent.class.getSuperclass().getSuperclass().getDeclaredField("who");
            field.setAccessible(true);
            field.set(event, viewer);
        } catch (Exception e) {
            // Fallback if reflection fails
        }
        
        return event;
    }
}
