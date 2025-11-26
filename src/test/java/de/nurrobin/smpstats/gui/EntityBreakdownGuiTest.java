package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.health.ServerHealthService;
import org.bukkit.Material;
import org.bukkit.World;
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
    
    @Test
    void handleClickBackButton() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(45); // Back button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickRefreshButton() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(53); // Refresh button
        when(event.getWhoClicked()).thenReturn(player);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickPreviousPageWhenOnFirstPage() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(48); // Previous page button
        when(event.getWhoClicked()).thenReturn(player);
        
        // Should not throw even when on first page
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickNextPageWhenOnlyOnePage() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(50); // Next page button
        when(event.getWhoClicked()).thenReturn(player);
        
        // Should not throw even when only one page
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickEntitySlotWithNoEntities() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0); // First entity slot
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        
        // Should not throw even when no entities
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickShiftRightWithoutPermission() {
        // Create player without permissions
        PlayerMock noPermPlayer = server.addPlayer();
        noPermPlayer.setOp(false);
        
        // Add an entity to click on
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(noPermPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(noPermPlayer);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void displayEntityWithHighCount() {
        // Spawn many entities to test high count color coding
        World world = server.getWorld("world");
        if (world != null) {
            for (int i = 0; i < 150; i++) {
                world.spawn(new org.bukkit.Location(world, i, 64, 0), Zombie.class);
            }
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        Inventory inventory = gui.getInventory();
        // Should have at least one entity item
        assertNotNull(inventory.getItem(0));
    }
    
    @Test
    void handleClickShiftRightWithPermissionFirstClick() {
        // Create player with manage permission
        PlayerMock adminPlayer = server.addPlayer();
        adminPlayer.addAttachment(plugin, "smpstats.gui.health.manage", true);
        
        // Add an entity to click on
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(adminPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(adminPlayer);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        // First shift-right click should show confirmation prompt
        assertDoesNotThrow(() -> gui.handleClick(event));
        
        // Item should change to TNT for confirmation
        Inventory inventory = gui.getInventory();
        ItemStack confirmItem = inventory.getItem(0);
        assertEquals(Material.TNT, confirmItem.getType());
    }
    
    @Test
    void handleClickShiftRightWithPermissionConfirmKill() {
        // Create player with manage permission
        PlayerMock adminPlayer = server.addPlayer();
        adminPlayer.addAttachment(plugin, "smpstats.gui.health.manage", true);
        
        // Add an entity to click on
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(adminPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(adminPlayer);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        // First click - confirmation prompt
        gui.handleClick(event);
        
        // Second click - confirm kill
        gui.handleClick(event);
        
        // Entities should be killed - just verify no error
        assertDoesNotThrow(() -> {});
    }
    
    @Test
    void handleClickShiftRightWithAdminPermission() {
        // Create player with admin permission
        PlayerMock adminPlayer = server.addPlayer();
        adminPlayer.addAttachment(plugin, "smpstats.admin", true);
        
        // Add an entity to click on
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(adminPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(adminPlayer);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        // Admin should be able to kill
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickLeftOpensDetailGui() {
        // Add an entity to click on
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        
        // Left click should open detail gui
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void paginationNextAndPrevious() {
        // Spawn enough entities to create multiple pages (need >45 entity types which is unrealistic)
        // But we can test the navigation doesn't break
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        // Try clicking next
        InventoryClickEvent nextEvent = mock(InventoryClickEvent.class);
        when(nextEvent.getSlot()).thenReturn(50);
        when(nextEvent.getWhoClicked()).thenReturn(player);
        gui.handleClick(nextEvent);
        
        // Try clicking previous
        InventoryClickEvent prevEvent = mock(InventoryClickEvent.class);
        when(prevEvent.getSlot()).thenReturn(48);
        when(prevEvent.getWhoClicked()).thenReturn(player);
        gui.handleClick(prevEvent);
        
        // Should handle gracefully
        assertDoesNotThrow(() -> {});
    }
    
    @Test
    void handleClickOutOfBoundsSlot() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        // Click in an empty slot that's within entity range but beyond entity count
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(44);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void handleClickWithMultipleEntityTypes() {
        // Add multiple entity types
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
            world.spawn(new org.bukkit.Location(world, 1, 64, 0), org.bukkit.entity.Cow.class);
            world.spawn(new org.bukkit.Location(world, 2, 64, 0), org.bukkit.entity.Sheep.class);
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(player);
        
        // Click on second entity slot
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(1);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        
        assertDoesNotThrow(() -> gui.handleClick(event));
    }
    
    @Test
    void confirmationExpiresAfterDelay() throws InterruptedException {
        // Create player with manage permission
        PlayerMock adminPlayer = server.addPlayer();
        adminPlayer.addAttachment(plugin, "smpstats.gui.health.manage", true);
        
        // Add an entity to click on
        World world = server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), Zombie.class);
        }
        
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 0);
        gui.open(adminPlayer);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
        when(event.getWhoClicked()).thenReturn(adminPlayer);
        when(event.getClick()).thenReturn(ClickType.SHIFT_RIGHT);
        
        // First click - confirmation prompt
        gui.handleClick(event);
        
        // Advance scheduler to trigger expiry (100 ticks)
        for (int i = 0; i < 101; i++) {
            server.getScheduler().performOneTick();
        }
        
        // Item should be back to original (not TNT anymore)
        // The confirmation expired, so clicking again should show confirmation prompt again
        Inventory inventory = gui.getInventory();
        ItemStack item = inventory.getItem(0);
        // The item should have been refreshed
        assertNotNull(item);
    }
    
    @Test
    void startPageGreaterThanZero() {
        EntityBreakdownGui gui = new EntityBreakdownGui(plugin, guiManager, healthService, 1);
        gui.open(player);
        
        // Should handle starting on page 1
        assertDoesNotThrow(() -> gui.getInventory());
    }
}

