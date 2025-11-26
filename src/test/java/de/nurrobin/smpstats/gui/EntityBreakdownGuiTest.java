package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.ServerHealthService;
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

class EntityBreakdownGuiTest {

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
        player.setOp(true); // Give admin permissions
        guiManager = plugin.getGuiManager();
        healthService = plugin.getServerHealthService().orElseThrow();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsInventoryWithCorrectSize() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        
        Inventory inventory = gui.getInventory();
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasBackButton() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        ItemStack backButton = inventory.getItem(45);
        assertNotNull(backButton);
        assertEquals(Material.ARROW, backButton.getType());
    }

    @Test
    void hasRefreshButton() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        ItemStack refreshButton = inventory.getItem(53);
        assertNotNull(refreshButton);
        assertEquals(Material.SUNFLOWER, refreshButton.getType());
    }

    @Test
    void hasSummaryItemOrNoEntitiesMessage() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        
        gui.open(player);
        Inventory inventory = gui.getInventory();
        
        // Either there's a summary item (if entities exist) or a barrier (no entities)
        ItemStack summaryItem = inventory.getItem(49);
        ItemStack noEntitiesItem = inventory.getItem(22);
        
        // At least one of these should be correct based on whether entities exist
        boolean hasSummary = summaryItem != null && summaryItem.getType() == Material.BOOK;
        boolean hasNoEntities = noEntitiesItem != null && noEntitiesItem.getType() == Material.BARRIER;
        
        assertTrue(hasSummary || hasNoEntities, "Should have either summary or no-entities message");
    }

    @Test
    void opensSuccessfully() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        
        assertDoesNotThrow(() -> gui.open(player));
    }
}
