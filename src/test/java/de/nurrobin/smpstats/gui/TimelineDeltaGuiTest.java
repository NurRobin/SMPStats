package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TimelineDeltaGuiTest {
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
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasThisWeekHeader() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // This week header at slot 2
        ItemStack header = inventory.getItem(2);
        assertNotNull(header);
        assertEquals(Material.LIME_BANNER, header.getType());
    }

    @Test
    void hasVsIcon() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // VS icon at slot 4
        ItemStack vs = inventory.getItem(4);
        assertNotNull(vs);
        assertEquals(Material.COMPARATOR, vs.getType());
    }

    @Test
    void hasLastWeekHeader() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Last week header at slot 6
        ItemStack header = inventory.getItem(6);
        assertNotNull(header);
        assertEquals(Material.YELLOW_BANNER, header.getType());
    }

    @Test
    void hasBackButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 49
        ItemStack back = inventory.getItem(49);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasRefreshButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Refresh button at slot 53
        ItemStack refresh = inventory.getItem(53);
        assertNotNull(refresh);
        assertEquals(Material.SUNFLOWER, refresh.getType());
    }

    @Test
    void showsNoDataMessageWhenEmpty() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show clock with no data message at slot 22
        ItemStack noData = inventory.getItem(22);
        assertNotNull(noData);
        assertEquals(Material.CLOCK, noData.getType());
    }

    @Test
    void implementsInventoryGui() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertDoesNotThrow(() -> gui.open(viewer));
    }

    @Test
    void fillsBackgroundWithGlassPanes() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Top row should be black stained glass
        ItemStack topCorner = inventory.getItem(0);
        assertNotNull(topCorner);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, topCorner.getType());
    }

    @Test
    void emptyDataShowsNoComparisonIcons() {
        // When there is no timeline data, comparison rows are not rendered
        // Instead, the "no data" message is shown at slot 22
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Slots where comparison icons would be are filled with glass panes
        ItemStack slot10 = inventory.getItem(10);
        assertNotNull(slot10);
        assertTrue(slot10.getType().name().endsWith("STAINED_GLASS_PANE"));
        
        ItemStack slot12 = inventory.getItem(12);
        assertNotNull(slot12);
        assertTrue(slot12.getType().name().endsWith("STAINED_GLASS_PANE"));
    }
}
