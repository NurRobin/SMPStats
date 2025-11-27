package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthChartGuiTest {

    private ServerMock server;
    private SMPStats plugin;
    private PlayerMock player;
    private GuiManager guiManager;
    private ServerHealthService healthService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        player = server.addPlayer();
        guiManager = plugin.getGuiManager();
        healthService = plugin.getServerHealthService().orElseThrow();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsInventoryWithCorrectSize() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        
        Inventory inventory = gui.getInventory();
        assertEquals(54, inventory.getSize());
    }

    @Test
    void showsNoDataMessageWhenHistoryEmpty() {
        // Create a fresh health service that hasn't sampled yet
        ServerHealthService freshService = new ServerHealthService(plugin, plugin.getSettings());
        // Don't start it, so no data is sampled
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, freshService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        // Should have a barrier item indicating no data
        ItemStack noDataItem = inventory.getItem(22);
        assertNotNull(noDataItem);
        assertEquals(Material.BARRIER, noDataItem.getType());
    }

    @Test
    void hasBackButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        ItemStack backButton = inventory.getItem(45);
        assertNotNull(backButton);
        assertEquals(Material.ARROW, backButton.getType());
    }

    @Test
    void hasTimeScaleButtons() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        // 4 time scale buttons at slots 47-50
        for (int i = 47; i <= 50; i++) {
            ItemStack button = inventory.getItem(i);
            assertNotNull(button, "Time scale button at slot " + i + " should exist");
            assertTrue(button.getType() == Material.LIME_DYE || button.getType() == Material.GRAY_DYE,
                    "Time scale button should be a dye");
        }
    }

    @Test
    void hasRefreshButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        ItemStack refreshButton = inventory.getItem(53);
        assertNotNull(refreshButton);
        assertEquals(Material.SUNFLOWER, refreshButton.getType());
    }

    @Test
    void selectedTimeScaleIsHighlighted() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        // 5m is at index 1 (ONE_MINUTE=0, FIVE_MINUTES=1), so slot 48
        ItemStack selectedButton = inventory.getItem(48);
        assertNotNull(selectedButton);
        assertEquals(Material.LIME_DYE, selectedButton.getType(), "Selected time scale should be lime dye");
    }

    @Test
    void allMetricTypesCanBeDisplayed() {
        for (HealthChartGui.MetricType type : HealthChartGui.MetricType.values()) {
            HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                    type, HealthChartGui.TimeScale.FIVE_MINUTES);
            
            assertNotNull(gui.getInventory());
            gui.open(player);
            player.closeInventory();
        }
    }

    @Test
    void allTimeScalesCanBeDisplayed() {
        for (HealthChartGui.TimeScale scale : HealthChartGui.TimeScale.values()) {
            HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                    HealthChartGui.MetricType.TPS, scale);
            
            assertNotNull(gui.getInventory());
            gui.open(player);
            player.closeInventory();
        }
    }

    @Test
    void drawsChartWhenHistoryAvailable() {
        // Sample some data first
        healthService.sampleNow();
        healthService.sampleNow();
        healthService.sampleNow();
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.ONE_HOUR);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        // Check that chart area has items (not just empty)
        boolean hasChartItems = false;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack item = inventory.getItem(row * 9 + col);
                if (item != null && item.getType().name().contains("STAINED_GLASS_PANE")) {
                    hasChartItems = true;
                    break;
                }
            }
        }
        assertTrue(hasChartItems, "Chart should have stained glass pane items");
    }
    
    @Test
    void handleClickBackButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(45); // Back button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickRefreshButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(53); // Refresh button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickTimeScaleOneMinuteButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.FIVE_MINUTES);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(47); // 1m button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickTimeScaleFiveMinuteButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.ONE_MINUTE);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(48); // 5m button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickTimeScaleThirtyMinuteButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.ONE_MINUTE);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(49); // 10m button (adjusted since there's no 30m)
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickTimeScaleOneHourButton() {
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.ONE_MINUTE);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(50); // 1h button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickChartAreaWithData() {
        healthService.sampleNow();
        healthService.sampleNow();
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.ONE_HOUR);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0); // Chart area
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void chartShowsMemoryMetric() {
        healthService.sampleNow();
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.MEMORY, HealthChartGui.TimeScale.ONE_HOUR);
        gui.open(player);
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
    }
    
    @Test
    void chartShowsChunksMetric() {
        healthService.sampleNow();
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.CHUNKS, HealthChartGui.TimeScale.ONE_HOUR);
        gui.open(player);
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
    }
    
    @Test
    void chartShowsEntitiesMetric() {
        healthService.sampleNow();
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.ENTITIES, HealthChartGui.TimeScale.ONE_HOUR);
        gui.open(player);
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
    }
    
    @Test
    void chartShowsCostIndexMetric() {
        healthService.sampleNow();
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.COST_INDEX, HealthChartGui.TimeScale.ONE_HOUR);
        gui.open(player);
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
    }
    
    @Test
    void chartUsesThresholdsForColors() {
        // Generate some history
        for (int i = 0; i < 10; i++) {
            healthService.sampleNow();
        }
        
        HealthChartGui gui = new HealthChartGui(plugin, guiManager, healthService,
                HealthChartGui.MetricType.TPS, HealthChartGui.TimeScale.ONE_HOUR);
        gui.open(player);
        
        Inventory inventory = gui.getInventory();
        
        // Verify chart area has colored glass panes
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack item = inventory.getItem(row * 9 + col);
                if (item != null) {
                    String typeName = item.getType().name();
                    assertTrue(typeName.contains("STAINED_GLASS_PANE") || typeName.equals("BLACK_STAINED_GLASS_PANE"),
                            "Chart items should be stained glass panes, got: " + typeName);
                }
            }
        }
    }
    
    @Test
    void timeScaleEnumGetMinutesReturnsCorrectValues() {
        assertEquals(1, HealthChartGui.TimeScale.ONE_MINUTE.getMinutes());
        assertEquals(5, HealthChartGui.TimeScale.FIVE_MINUTES.getMinutes());
        assertEquals(10, HealthChartGui.TimeScale.TEN_MINUTES.getMinutes());
        assertEquals(60, HealthChartGui.TimeScale.ONE_HOUR.getMinutes());
    }
    
    @Test
    void allTimeScaleValuesExist() {
        HealthChartGui.TimeScale[] scales = HealthChartGui.TimeScale.values();
        assertEquals(4, scales.length);
    }
    
    @Test
    void allMetricTypeValuesExist() {
        HealthChartGui.MetricType[] types = HealthChartGui.MetricType.values();
        assertTrue(types.length > 0);
    }
}
