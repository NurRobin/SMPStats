package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

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
}
