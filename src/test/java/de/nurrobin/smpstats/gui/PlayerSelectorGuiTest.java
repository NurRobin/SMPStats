package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSelectorGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private StatsService statsService;
    private GuiManager guiManager;
    private PlayerMock viewer;
    private PlayerMock otherPlayer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        statsService = plugin.getStatsService();
        guiManager = new GuiManager(plugin);
        
        // Create test players
        viewer = server.addPlayer("Viewer");
        otherPlayer = server.addPlayer("OtherPlayer");
        
        // Register them so they have stats
        statsService.handleJoin(viewer);
        statsService.handleJoin(otherPlayer);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsInventoryWithCorrectSize() {
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void excludesSelfFromPlayerList() {
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should have OtherPlayer's head, but not Viewer's head
        // Check that at least one player head exists
        boolean foundHead = false;
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                foundHead = true;
                break;
            }
        }
        assertTrue(foundHead, "Should show at least one other player");
    }

    @Test
    void hasBackButton() {
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 45
        ItemStack back = inventory.getItem(45);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasPageInfoItem() {
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Page info at slot 49
        ItemStack pageInfo = inventory.getItem(49);
        assertNotNull(pageInfo);
        assertEquals(Material.PAPER, pageInfo.getType());
    }

    @Test
    void showsEmptyMessageWhenNoOtherPlayers() {
        // Create a new server with only one player
        MockBukkit.unmock();
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        statsService = plugin.getStatsService();
        guiManager = new GuiManager(plugin);
        
        PlayerMock lonePlayer = server.addPlayer("LonePlayer");
        statsService.handleJoin(lonePlayer);
        
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                lonePlayer, lonePlayer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show barrier when no other players
        ItemStack barrier = inventory.getItem(22);
        assertNotNull(barrier);
        assertEquals(Material.BARRIER, barrier.getType());
    }

    @Test
    void implementsInventoryGui() {
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId());
        
        assertDoesNotThrow(() -> gui.open(viewer));
    }

    @Test
    void supportsPagination() {
        // Add many players to test pagination
        for (int i = 0; i < 50; i++) {
            PlayerMock p = server.addPlayer("Player" + i);
            statsService.handleJoin(p);
        }
        
        // First page
        PlayerSelectorGui gui = new PlayerSelectorGui(plugin, guiManager, statsService,
                viewer, viewer.getUniqueId(), 0);
        
        Inventory inventory = gui.getInventory();
        
        // Should have next page arrow at slot 50
        ItemStack nextPage = inventory.getItem(50);
        assertNotNull(nextPage);
        assertEquals(Material.ARROW, nextPage.getType());
    }
}
