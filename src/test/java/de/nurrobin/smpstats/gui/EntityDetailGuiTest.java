package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.EntityAnalysisService;
import de.nurrobin.smpstats.health.ServerHealthService;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class EntityDetailGuiTest {

    private ServerMock server;
    private SMPStats plugin;
    private PlayerMock player;
    private GuiManager guiManager;
    private ServerHealthService healthService;
    private EntityAnalysisService entityService;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        player = server.addPlayer();
        player.setOp(true);
        guiManager = plugin.getGuiManager();
        healthService = plugin.getServerHealthService().orElseThrow();
        entityService = new EntityAnalysisService();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsInventoryWithCorrectSize() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        
        Inventory inventory = gui.getInventory();
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasBackButton() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        ItemStack backButton = inventory.getItem(45);
        assertNotNull(backButton);
        assertEquals(Material.ARROW, backButton.getType());
    }

    @Test
    void hasRefreshButton() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        ItemStack refreshButton = inventory.getItem(53);
        assertNotNull(refreshButton);
        assertEquals(Material.SUNFLOWER, refreshButton.getType());
    }

    @Test
    void hasKillAllButton() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        ItemStack killAllButton = inventory.getItem(46);
        assertNotNull(killAllButton);
        assertEquals(Material.TNT, killAllButton.getType());
    }

    @Test
    void showsNoEntitiesMessageWhenEmpty() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ENDER_DRAGON, 0);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        // Should have a barrier item indicating no entities
        ItemStack noDataItem = inventory.getItem(22);
        assertNotNull(noDataItem);
        assertEquals(Material.BARRIER, noDataItem.getType());
    }

    @Test
    void opensSuccessfully() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        
        assertDoesNotThrow(() -> gui.open(player));
    }
}
