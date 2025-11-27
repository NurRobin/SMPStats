package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.EntityAnalysisService;
import de.nurrobin.smpstats.health.ServerHealthService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
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
    void handleClickBackButton() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(45); // Back button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickRefreshButton() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(53); // Refresh button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickPreviousPageButton() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(47); // Previous page button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickNextPageButton() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(51); // Next page button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickKillAllButtonWithPermission() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(46); // Kill all button
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickKillAllButtonWithoutPermission() {
        PlayerMock noPermPlayer = server.addPlayer();
        noPermPlayer.setOp(false);
        
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(noPermPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(46); // Kill all button
        when(event.getWhoClicked()).thenReturn(noPermPlayer);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickEntitySlotLeftClickWithoutPermission() {
        // Create a zombie to have an entity slot
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        PlayerMock noPermPlayer = server.addPlayer();
        noPermPlayer.setOp(false);
        
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(noPermPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0); // First entity slot
        when(event.getWhoClicked()).thenReturn(noPermPlayer);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickEntitySlotShiftRightClickWithoutPermission() {
        // Create a zombie to have an entity slot
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        PlayerMock noPermPlayer = server.addPlayer();
        noPermPlayer.setOp(false);
        
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(noPermPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0); // First entity slot
        when(event.getWhoClicked()).thenReturn(noPermPlayer);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickEntitySlotWithValidEntity() {
        // Create a zombie to have an entity slot
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        // Left click for teleport confirmation
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        
        // First click shows confirmation
        gui.handleClick(event);
        
        // Second click should teleport
        gui.handleClick(event);
    }
    
    @Test
    void handleClickEntitySlotShiftRightWithValidEntity() {
        // Create a zombie to have an entity slot
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        // Shift right click for kill confirmation
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        // First click shows confirmation
        gui.handleClick(event);
        
        // Verify item changed to confirmation state
        Inventory inventory = gui.getInventory();
        ItemStack confirmItem = inventory.getItem(0);
        assertNotNull(confirmItem);
        assertEquals(Material.BARRIER, confirmItem.getType());
    }
    
    @Test
    void showsEntitiesWithCustomNames() {
        World world = server.getWorld("world");
        if (world != null) {
            Zombie zombie = world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
            zombie.customName(net.kyori.adventure.text.Component.text("Named Zombie"));
        }
        
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        gui.open(player);
        
        Inventory inventory = gui.getInventory();
        // Should have the entity item displayed
        assertNotNull(inventory.getItem(0));
    }
    
    @Test
    void opensSuccessfully() {
        EntityDetailGui gui = new EntityDetailGui(plugin, guiManager, healthService, entityService, EntityType.ZOMBIE, 0);
        
        assertDoesNotThrow(() -> gui.open(player));
    }
}
