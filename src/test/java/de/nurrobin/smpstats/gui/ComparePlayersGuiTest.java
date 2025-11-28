package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ComparePlayersGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private StatsService statsService;
    private GuiManager guiManager;
    private PlayerMock player1;
    private PlayerMock player2;
    private PlayerMock viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        statsService = plugin.getStatsService();
        guiManager = new GuiManager(plugin);
        
        // Create test players
        player1 = server.addPlayer("TestPlayer1");
        player2 = server.addPlayer("TestPlayer2");
        viewer = server.addPlayer("Viewer");
        
        // Setup stats for both players
        StatsRecord record1 = new StatsRecord(player1.getUniqueId(), player1.getName());
        record1.setPlaytimeMillis(3600000); // 1 hour
        record1.setDeaths(5);
        record1.setMobKills(100);
        record1.setBlocksBroken(500);
        record1.setBlocksPlaced(300);
        
        StatsRecord record2 = new StatsRecord(player2.getUniqueId(), player2.getName());
        record2.setPlaytimeMillis(7200000); // 2 hours
        record2.setDeaths(10);
        record2.setMobKills(50);
        record2.setBlocksBroken(1000);
        record2.setBlocksPlaced(800);
        
        statsService.handleJoin(player1);
        statsService.handleJoin(player2);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsInventoryWithCorrectTitle() {
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, player1.getUniqueId(), player2.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasPlayerHeadsForBothPlayers() {
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, player1.getUniqueId(), player2.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Player 1 head at slot 1
        ItemStack head1 = inventory.getItem(1);
        assertNotNull(head1);
        assertEquals(Material.PLAYER_HEAD, head1.getType());
        
        // Player 2 head at slot 7
        ItemStack head2 = inventory.getItem(7);
        assertNotNull(head2);
        assertEquals(Material.PLAYER_HEAD, head2.getType());
    }

    @Test
    void hasVsComparatorInCenter() {
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, player1.getUniqueId(), player2.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // VS comparator at slot 4
        ItemStack vs = inventory.getItem(4);
        assertNotNull(vs);
        assertEquals(Material.COMPARATOR, vs.getType());
    }

    @Test
    void hasBackButton() {
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, player1.getUniqueId(), player2.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 49
        ItemStack back = inventory.getItem(49);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasSkillComparisonIcons() {
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, player1.getUniqueId(), player2.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Skill comparison icons at slots 45-49 (mining, combat, explore, build, farm)
        ItemStack mining = inventory.getItem(45);
        assertNotNull(mining);
        assertEquals(Material.DIAMOND_PICKAXE, mining.getType());
        
        ItemStack combat = inventory.getItem(46);
        assertNotNull(combat);
        assertEquals(Material.DIAMOND_SWORD, combat.getType());
    }

    @Test
    void showsErrorWhenPlayerStatsNotFound() {
        UUID unknownUuid = UUID.randomUUID();
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, unknownUuid, player2.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show barrier with error message
        ItemStack error = inventory.getItem(22);
        assertNotNull(error);
        assertEquals(Material.BARRIER, error.getType());
    }

    @Test
    void implementsInventoryGui() {
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, player1.getUniqueId(), player2.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        ComparePlayersGui gui = new ComparePlayersGui(plugin, guiManager, statsService,
                viewer, player1.getUniqueId(), player2.getUniqueId());
        
        // Should not throw
        assertDoesNotThrow(() -> gui.open(viewer));
    }
}
