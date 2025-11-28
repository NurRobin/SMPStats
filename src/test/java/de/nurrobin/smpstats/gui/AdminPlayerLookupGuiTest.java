package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the AdminPlayerLookupGui class.
 */
class AdminPlayerLookupGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private StatsService statsService;
    private StatsStorage storage;
    private GuiManager guiManager;
    private PlayerMock viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        statsService = mock(StatsService.class);
        storage = mock(StatsStorage.class);
        guiManager = new GuiManager(plugin);
        viewer = server.addPlayer("AdminPlayer");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<StatsRecord> createTestPlayers(int count) {
        List<StatsRecord> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(10 + i));
            record.setMobKills(50 + i * 10);
            record.setPlayerKills(i);
            record.setDeaths(5 + i);
            record.setLastJoin(System.currentTimeMillis() - (i * 3600000L)); // Each player last seen 1 hour later
            players.add(record);
        }
        return players;
    }

    @Test
    void testCreateInventory() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void testHeaderShowsPlayerCount() throws SQLException {
        List<StatsRecord> players = createTestPlayers(10);
        when(storage.loadAll()).thenReturn(players);
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        // Header should be at slot 4
        ItemStack header = inventory.getItem(4);
        assertNotNull(header);
        assertEquals(Material.COMMAND_BLOCK, header.getType());
        
        // Should show player lookup title
        Component displayName = header.getItemMeta().displayName();
        assertNotNull(displayName);
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("Player Lookup"));
    }

    @Test
    void testSortModeButtonExists() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        ItemStack sortButton = inventory.getItem(0);
        assertNotNull(sortButton);
        assertEquals(Material.HOPPER, sortButton.getType());
    }

    @Test
    void testSearchButtonExists() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        ItemStack searchButton = inventory.getItem(8);
        assertNotNull(searchButton);
        assertEquals(Material.OAK_SIGN, searchButton.getType());
    }

    @Test
    void testBackButtonExists() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        ItemStack backButton = inventory.getItem(45);
        assertNotNull(backButton);
        assertEquals(Material.ARROW, backButton.getType());
    }

    @Test
    void testRefreshButtonExists() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        ItemStack refreshButton = inventory.getItem(53);
        assertNotNull(refreshButton);
        assertEquals(Material.SUNFLOWER, refreshButton.getType());
    }

    @Test
    void testPageIndicatorExists() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        ItemStack pageIndicator = inventory.getItem(49);
        assertNotNull(pageIndicator);
        assertEquals(Material.PAPER, pageIndicator.getType());
        
        // Should show page info
        Component displayName = pageIndicator.getItemMeta().displayName();
        assertNotNull(displayName);
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("Page"));
    }

    @Test
    void testPlayersDisplayedAsHeads() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        // Player slots start at 10
        int[] playerSlots = {10, 11, 12, 13, 14, 15, 16};
        int headsFound = 0;
        for (int slot : playerSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                headsFound++;
            }
        }
        
        assertEquals(5, headsFound, "Should display 5 player heads");
    }

    @Test
    void testEmptyPlayersShowsNothing() throws SQLException {
        when(storage.loadAll()).thenReturn(new ArrayList<>());
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        // Player slots should have filler
        int[] playerSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int slot : playerSlots) {
            ItemStack item = inventory.getItem(slot);
            assertNotNull(item);
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, item.getType());
        }
    }

    @Test
    void testPaginationWithManyPlayers() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(50));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        // Next page button should exist at slot 50
        ItemStack nextButton = inventory.getItem(50);
        assertNotNull(nextButton);
        assertEquals(Material.SPECTRAL_ARROW, nextButton.getType());
    }

    @Test
    void testHandleSortModeClick() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                0, // Sort mode button
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        
        gui.handleClick(event);
        
        // Sort button should now show different text (NAME instead of PLAYTIME)
        ItemStack sortButton = gui.getInventory().getItem(0);
        assertNotNull(sortButton);
        Component displayName = sortButton.getItemMeta().displayName();
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("Name"));
    }

    @Test
    void testHandleSearchFilterClear() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        
        // Set a filter first
        gui.setSearchFilter("Player1");
        
        // Now click search to clear it
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                8, // Search button (clears filter)
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        
        gui.handleClick(event);
        
        // Should refresh and show all players
        assertNotNull(gui.getInventory());
    }

    @Test
    void testHandleRefreshClick() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                53, // Refresh button
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        
        gui.handleClick(event);
        
        // Should have called loadAll() again
        verify(storage, atLeast(2)).loadAll();
    }

    @Test
    void testHandleNextPageClick() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(50));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                50, // Next page button
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        
        gui.handleClick(event);
        
        // Page indicator should show page 2
        ItemStack pageIndicator = gui.getInventory().getItem(49);
        assertNotNull(pageIndicator);
        Component displayName = pageIndicator.getItemMeta().displayName();
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("Page 2"));
    }

    @Test
    void testHandlePreviousPageClick() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(50));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        
        // First go to page 2
        InventoryClickEvent nextEvent = new InventoryClickEvent(
                viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                50,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        gui.handleClick(nextEvent);
        
        // Now go back to page 1
        InventoryClickEvent prevEvent = new InventoryClickEvent(
                viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER,
                48, // Previous page button
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        gui.handleClick(prevEvent);
        
        // Page indicator should show page 1
        ItemStack pageIndicator = gui.getInventory().getItem(49);
        assertNotNull(pageIndicator);
        Component displayName = pageIndicator.getItemMeta().displayName();
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("Page 1"));
    }

    @Test
    void testSetSearchFilter() throws SQLException {
        List<StatsRecord> players = createTestPlayers(10);
        when(storage.loadAll()).thenReturn(players);
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        
        // Apply filter
        gui.setSearchFilter("Player5");
        
        // Should only show matching players
        // Count player heads
        int headsFound = 0;
        int[] playerSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int slot : playerSlots) {
            ItemStack item = gui.getInventory().getItem(slot);
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                headsFound++;
            }
        }
        
        assertEquals(1, headsFound, "Should only show 1 player matching 'Player5'");
    }

    @Test
    void testSQLExceptionHandled() throws SQLException {
        when(storage.loadAll()).thenThrow(new SQLException("Test error"));
        
        // Should not throw
        assertDoesNotThrow(() -> new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer));
    }

    @Test
    void testOpenMethod() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        gui.open(viewer);
        
        assertNotNull(viewer.getOpenInventory());
    }

    @Test
    void testSortModeEnum() {
        assertEquals(5, AdminPlayerLookupGui.SortMode.values().length);
        
        assertEquals("⏱ Playtime", AdminPlayerLookupGui.SortMode.PLAYTIME.getDisplayName());
        assertEquals("📛 Name", AdminPlayerLookupGui.SortMode.NAME.getDisplayName());
        assertEquals("📅 Last Seen", AdminPlayerLookupGui.SortMode.LAST_SEEN.getDisplayName());
        assertEquals("⚔ Kills", AdminPlayerLookupGui.SortMode.KILLS.getDisplayName());
        assertEquals("💀 Deaths", AdminPlayerLookupGui.SortMode.DEATHS.getDisplayName());
    }

    @Test
    void testSortingByPlaytime() throws SQLException {
        List<StatsRecord> players = new ArrayList<>();
        
        StatsRecord player1 = new StatsRecord(UUID.randomUUID(), "LowPlaytime");
        player1.setPlaytimeMillis(TimeUnit.HOURS.toMillis(1));
        players.add(player1);
        
        StatsRecord player2 = new StatsRecord(UUID.randomUUID(), "HighPlaytime");
        player2.setPlaytimeMillis(TimeUnit.HOURS.toMillis(100));
        players.add(player2);
        
        when(storage.loadAll()).thenReturn(players);
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        
        // Default sort is PLAYTIME (descending), so HighPlaytime should be first
        ItemStack firstPlayer = gui.getInventory().getItem(10);
        assertNotNull(firstPlayer);
        assertEquals(Material.PLAYER_HEAD, firstPlayer.getType());
        
        Component displayName = firstPlayer.getItemMeta().displayName();
        String text = PlainTextComponentSerializer.plainText().serialize(displayName);
        assertTrue(text.contains("HighPlaytime"));
    }

    @Test
    void testOnlineFilterButtonExists() throws SQLException {
        when(storage.loadAll()).thenReturn(createTestPlayers(5));
        
        AdminPlayerLookupGui gui = new AdminPlayerLookupGui(plugin, guiManager, statsService, storage, viewer);
        Inventory inventory = gui.getInventory();
        
        ItemStack onlineFilter = inventory.getItem(46);
        assertNotNull(onlineFilter);
        assertEquals(Material.ENDER_EYE, onlineFilter.getType());
    }
}
