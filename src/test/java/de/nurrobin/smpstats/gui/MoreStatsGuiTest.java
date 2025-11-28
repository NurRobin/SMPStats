package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
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

import static org.junit.jupiter.api.Assertions.*;

class MoreStatsGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private StatsService statsService;
    private GuiManager guiManager;
    private PlayerMock viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        statsService = plugin.getStatsService();
        guiManager = new GuiManager(plugin);
        
        viewer = server.addPlayer("Viewer");
        statsService.handleJoin(viewer);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsInventoryWithCorrectSize() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(27, inventory.getSize());
    }

    @Test
    void hasDeathHistoryButton() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        Inventory inventory = gui.getInventory();
        
        // Death History at slot 10
        ItemStack button = inventory.getItem(10);
        assertNotNull(button);
        assertEquals(Material.SKELETON_SKULL, button.getType());
    }

    @Test
    void hasTimelineButton() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        Inventory inventory = gui.getInventory();
        
        // Timeline at slot 12
        ItemStack button = inventory.getItem(12);
        assertNotNull(button);
        assertEquals(Material.RECOVERY_COMPASS, button.getType());
    }

    @Test
    void hasHeatmapButton() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        Inventory inventory = gui.getInventory();
        
        // Heatmap at slot 14
        ItemStack button = inventory.getItem(14);
        assertNotNull(button);
        assertEquals(Material.FILLED_MAP, button.getType());
    }

    @Test
    void hasMomentsButton() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        Inventory inventory = gui.getInventory();
        
        // Moments at slot 16
        ItemStack button = inventory.getItem(16);
        assertNotNull(button);
        assertEquals(Material.NETHER_STAR, button.getType());
    }

    @Test
    void hasBackButton() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 22
        ItemStack button = inventory.getItem(22);
        assertNotNull(button);
        assertEquals(Material.ARROW, button.getType());
    }

    @Test
    void handleClickDeathHistoryOpensGui() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        guiManager.openGui(viewer, gui);
        
        // Click on Death History button (slot 10)
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                10, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open DeathReplayGui
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof DeathReplayGui);
    }

    @Test
    void handleClickTimelineOpensGui() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        guiManager.openGui(viewer, gui);
        
        // Click on Timeline button (slot 12)
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                12, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open TimelineDeltaGui
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof TimelineDeltaGui);
    }

    @Test
    void handleClickHeatmapOpensGui() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        guiManager.openGui(viewer, gui);
        
        // Click on Heatmap button (slot 14)
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                14, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open PersonalHeatmapGui
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof PersonalHeatmapGui);
    }

    @Test
    void handleClickMomentsOpensGui() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        guiManager.openGui(viewer, gui);
        
        // Click on Moments button (slot 16)
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                16, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should open MomentsHistoryGui
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof MomentsHistoryGui);
    }

    @Test
    void handleClickBackReturnsToPlayerStats() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        guiManager.openGui(viewer, gui);
        
        // Click on Back button (slot 22)
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                22, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should return to PlayerStatsGui
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof PlayerStatsGui);
    }

    @Test
    void implementsInventoryHolder() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        // getInventory should return itself
        assertSame(gui.getInventory(), gui.getInventory());
    }

    @Test
    void implementsInventoryGui() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        assertTrue(gui instanceof InventoryGui);
    }

    @Test
    void correctFillerItemsUsed() {
        MoreStatsGui gui = new MoreStatsGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), viewer.getName());
        
        Inventory inventory = gui.getInventory();
        
        // Check that empty slots have filler items
        ItemStack filler = inventory.getItem(0);
        assertNotNull(filler);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, filler.getType());
    }
}
