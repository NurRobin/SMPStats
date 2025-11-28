package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.moments.MomentService;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MomentsHistoryGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private StatsService statsService;
    private MomentService momentService;
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
        
        // Get moment service
        Optional<MomentService> momentOpt = plugin.getMomentService();
        assertTrue(momentOpt.isPresent(), "MomentService should be available");
        momentService = momentOpt.get();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsInventoryWithCorrectSize() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasAchievementsHeader() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Header at slot 4
        ItemStack header = inventory.getItem(4);
        assertNotNull(header);
        assertEquals(Material.NETHER_STAR, header.getType());
    }

    @Test
    void hasBackButton() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 45
        ItemStack back = inventory.getItem(45);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasRefreshButton() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Refresh button at slot 53
        ItemStack refresh = inventory.getItem(53);
        assertNotNull(refresh);
        assertEquals(Material.SUNFLOWER, refresh.getType());
    }

    @Test
    void hasPageInfoSlot() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Page info at slot 49
        ItemStack pageInfo = inventory.getItem(49);
        assertNotNull(pageInfo);
        assertEquals(Material.PAPER, pageInfo.getType());
    }

    @Test
    void hasStatsButton() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Stats button at slot 52
        ItemStack stats = inventory.getItem(52);
        assertNotNull(stats);
        assertEquals(Material.BOOK, stats.getType());
    }

    @Test
    void showsNoAchievementsMessageWhenEmpty() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show barrier with no achievements message at slot 22
        ItemStack noAchievements = inventory.getItem(22);
        assertNotNull(noAchievements);
        assertEquals(Material.BARRIER, noAchievements.getType());
    }

    @Test
    void implementsInventoryGui() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        assertDoesNotThrow(() -> gui.open(viewer));
    }

    @Test
    void fillsBackgroundWithGlassPanes() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Top row should have orange stained glass
        ItemStack topCorner = inventory.getItem(0);
        assertNotNull(topCorner);
        assertEquals(Material.ORANGE_STAINED_GLASS_PANE, topCorner.getType());
    }

    @Test
    void supportsPageConstructor() {
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, viewer.getUniqueId(), 1);
        
        assertNotNull(gui);
        assertNotNull(gui.getInventory());
    }

    @Test
    void canViewDifferentPlayerAchievements() {
        // Create another player
        PlayerMock otherPlayer = server.addPlayer("OtherPlayer");
        statsService.handleJoin(otherPlayer);
        
        // Viewer opens other player's achievements
        MomentsHistoryGui gui = new MomentsHistoryGui(plugin, guiManager, statsService,
                momentService, viewer, otherPlayer.getUniqueId());
        
        assertNotNull(gui);
        assertNotNull(gui.getInventory());
        assertEquals(54, gui.getInventory().getSize());
    }
}
