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

class SocialPartnersGuiTest {
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
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasHeaderItem() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Header at slot 4
        ItemStack header = inventory.getItem(4);
        assertNotNull(header);
        assertEquals(Material.PLAYER_HEAD, header.getType());
    }

    @Test
    void hasBackButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 49
        ItemStack back = inventory.getItem(49);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasTimeStatsItem() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Total time at slot 48
        ItemStack timeItem = inventory.getItem(48);
        assertNotNull(timeItem);
        assertEquals(Material.CLOCK, timeItem.getType());
    }

    @Test
    void hasKillStatsItem() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Shared kills at slot 50
        ItemStack killsItem = inventory.getItem(50);
        assertNotNull(killsItem);
        assertEquals(Material.DIAMOND_SWORD, killsItem.getType());
    }

    @Test
    void showsNoPartnersMessageWhenEmpty() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show barrier with no partners message at slot 22
        ItemStack noPartners = inventory.getItem(22);
        assertNotNull(noPartners);
        assertEquals(Material.BARRIER, noPartners.getType());
    }

    @Test
    void implementsInventoryGui() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertDoesNotThrow(() -> gui.open(viewer));
    }
}
