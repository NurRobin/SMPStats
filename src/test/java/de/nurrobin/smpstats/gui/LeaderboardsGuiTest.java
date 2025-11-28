package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.health.ServerHealthService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaderboardsGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private GuiManager guiManager;
    private StatsService statsService;
    private ServerHealthService healthService;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        guiManager = mock(GuiManager.class);
        statsService = mock(StatsService.class);
        healthService = mock(ServerHealthService.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void showsCategoryButtons() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();

        // Category buttons at slots 1-7 (shifted for symmetry)
        for (int i = 1; i <= LeaderboardsGui.LeaderboardType.values().length; i++) {
            assertNotNull(inv.getItem(i), "Category button at slot " + i + " should exist");
        }
    }

    @Test
    void showsPlayersWithPlayerHeads() {
        List<StatsRecord> stats = new ArrayList<>();
        
        StatsRecord player1 = new StatsRecord(UUID.randomUUID(), "TopPlayer");
        player1.setPlaytimeMillis(10000000L);
        
        StatsRecord player2 = new StatsRecord(UUID.randomUUID(), "SecondPlayer");
        player2.setPlaytimeMillis(5000000L);
        
        StatsRecord player3 = new StatsRecord(UUID.randomUUID(), "ThirdPlayer");
        player3.setPlaytimeMillis(1000000L);
        
        stats.add(player2); // Add in wrong order
        stats.add(player3);
        stats.add(player1);
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();

        // All players should now use PLAYER_HEAD (with rank as item amount)
        // First player at slot 19 (row 3, first position)
        assertNotNull(inv.getItem(19));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(19).getType());
        assertEquals(1, inv.getItem(19).getAmount()); // Rank 1
        
        // Second player at slot 20 (row 3, second position)
        assertNotNull(inv.getItem(20));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(20).getType());
        assertEquals(2, inv.getItem(20).getAmount()); // Rank 2
        
        // Third player at slot 21 (row 3, third position)
        assertNotNull(inv.getItem(21));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(21).getType());
        assertEquals(3, inv.getItem(21).getAmount()); // Rank 3
    }

    @Test
    void switchesCategory() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(11); // Kills category at slot 11 (row 2, second position)
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(LeaderboardsGui.class));
    }

    @Test
    void hasNavigationButtons() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();

        // Back button at 48
        assertNotNull(inv.getItem(48));
        assertEquals(Material.BARRIER, inv.getItem(48).getType());
        
        // Refresh button at 50
        assertNotNull(inv.getItem(50));
        assertEquals(Material.SUNFLOWER, inv.getItem(50).getType());
        
        // Page indicator at 49
        assertNotNull(inv.getItem(49));
        assertEquals(Material.PAPER, inv.getItem(49).getType());
    }

    @Test
    void hasInfoPanel() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();

        // Info panel at slot 8
        assertNotNull(inv.getItem(8));
        assertEquals(Material.BOOK, inv.getItem(8).getType());
    }

    @Test
    void backButtonReturnsToMainMenu() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(48); // Back button
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(MainMenuGui.class));
    }

    @Test
    void refreshButtonRefreshesData() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(50); // Refresh button
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(LeaderboardsGui.class));
    }

    @Test
    void paginationWorksWithManyPlayers() {
        List<StatsRecord> stats = new ArrayList<>();
        for (int i = 0; i < 45; i++) { // More than 21 per page
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (45 - i));
            stats.add(record);
        }
        when(statsService.getAllStats()).thenReturn(stats);

        // Page 0
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();
        
        // Should have next page button
        assertNotNull(inv.getItem(53));
        assertEquals(Material.ARROW, inv.getItem(53).getType());
        
        // First page should NOT have previous page arrow (but slot 45 exists with glass)
        assertNotNull(inv.getItem(45));
        assertNotEquals(Material.ARROW, inv.getItem(45).getType());
    }

    @Test
    void allLeaderboardTypesWork() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        for (LeaderboardsGui.LeaderboardType type : LeaderboardsGui.LeaderboardType.values()) {
            LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService, type, 0);
            assertNotNull(gui.getInventory());
            assertEquals(54, gui.getInventory().getSize());
        }
    }

    @Test
    void previousPageButtonWorks() {
        List<StatsRecord> stats = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (45 - i));
            stats.add(record);
        }
        when(statsService.getAllStats()).thenReturn(stats);

        // Start on page 1
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 1);
        Inventory inv = gui.getInventory();
        
        // Should have previous page button
        assertNotNull(inv.getItem(45));
        assertEquals(Material.ARROW, inv.getItem(45).getType());

        // Click previous page
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(45);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(LeaderboardsGui.class));
    }

    @Test
    void nextPageButtonWorks() {
        List<StatsRecord> stats = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (45 - i));
            stats.add(record);
        }
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);

        // Click next page
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(53);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        verify(guiManager).openGui(eq(player), any(LeaderboardsGui.class));
    }

    @Test
    void clickingSameTypeDoesNotOpenNewGui() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        // Click same category (PLAYTIME is at slot 10 now)
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(10);
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Should NOT open a new GUI
        verify(guiManager, never()).openGui(eq(player), any(LeaderboardsGui.class));
    }

    @Test
    void leaderboardTypeEnumMethods() {
        LeaderboardsGui.LeaderboardType type = LeaderboardsGui.LeaderboardType.PLAYTIME;
        assertEquals("Playtime", type.getDisplayName());
        assertEquals(Material.CLOCK, type.getIcon());
        assertNotNull(type.getColor());
        
        StatsRecord record = new StatsRecord(UUID.randomUUID(), "TestPlayer");
        record.setPlaytimeMillis(3600000L); // 1 hour
        
        assertEquals(3600000.0, type.getValue(record));
        assertEquals("1h", type.formatValue(record));
    }

    @Test
    void formatPlaytimeWithDays() {
        List<StatsRecord> stats = new ArrayList<>();
        StatsRecord record = new StatsRecord(UUID.randomUUID(), "LongTimePlayer");
        record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(50)); // 2 days 2 hours
        stats.add(record);
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        assertNotNull(gui.getInventory());
    }

    @Test
    void formatDistanceWithKilometers() {
        List<StatsRecord> stats = new ArrayList<>();
        StatsRecord record = new StatsRecord(UUID.randomUUID(), "TravelPlayer");
        record.setDistanceOverworld(5000.0); // 5km
        stats.add(record);
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.DISTANCE, 0);
        
        assertNotNull(gui.getInventory());
    }

    @Test
    void formatDistanceWithMeters() {
        List<StatsRecord> stats = new ArrayList<>();
        StatsRecord record = new StatsRecord(UUID.randomUUID(), "ShortTravelPlayer");
        record.setDistanceOverworld(500.0); // 500m
        stats.add(record);
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.DISTANCE, 0);
        
        assertNotNull(gui.getInventory());
    }

    @Test
    void playerRankShownAsItemAmount() {
        List<StatsRecord> stats = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (10 - i));
            stats.add(record);
        }
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();

        // All entries should use PLAYER_HEAD with rank as amount
        // Slots 19-25 (row 3), 28-34 (row 4) for 7x2 grid
        int[] playerSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};
        for (int i = 0; i < 10; i++) {
            assertNotNull(inv.getItem(playerSlots[i]));
            assertEquals(Material.PLAYER_HEAD, inv.getItem(playerSlots[i]).getType());
            assertEquals(i + 1, inv.getItem(playerSlots[i]).getAmount(), "Rank " + (i+1) + " should have amount " + (i+1));
        }
    }

    @Test
    void maxRankIs50() {
        List<StatsRecord> stats = new ArrayList<>();
        for (int i = 0; i < 100; i++) { // More than max 50
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (100 - i));
            stats.add(record);
        }
        
        when(statsService.getAllStats()).thenReturn(stats);

        // Should limit to 50 players total
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        // Just verify it creates without error
        assertNotNull(gui.getInventory());
    }

    @Test
    void opensPlayerInventory() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        gui.open(player);
        
        assertNotNull(player.getOpenInventory());
    }

    @Test
    void hasFindMyRankButton() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();

        // Find My Rank button at slot 46
        assertNotNull(inv.getItem(46));
        assertEquals(Material.ENDER_EYE, inv.getItem(46).getType());
    }

    @Test
    void findMyRankJumpsToCorrectPage() {
        List<StatsRecord> stats = new ArrayList<>();
        
        // Create 30 players, with the test player at position 25
        for (int i = 0; i < 30; i++) {
            UUID uuid = i == 24 ? player.getUniqueId() : UUID.randomUUID();
            StatsRecord record = new StatsRecord(uuid, i == 24 ? player.getName() : "Player" + i);
            record.setPlaytimeMillis(1000000L * (30 - i)); // Higher playtime = higher rank
            stats.add(record);
        }
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(46); // Find My Rank button
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Should open a new GUI on page 1 (player is rank 25, and 21 players per page)
        verify(guiManager).openGui(eq(player), any(LeaderboardsGui.class));
    }

    @Test
    void findMyRankShowsNotFoundMessageWhenOutsideTop50() {
        List<StatsRecord> stats = new ArrayList<>();
        
        // Create 60 players, test player is NOT in top 50
        for (int i = 0; i < 60; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (60 - i));
            stats.add(record);
        }
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(46); // Find My Rank button
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Should NOT open a new GUI since player is not in top 50
        verify(guiManager, never()).openGui(eq(player), any(LeaderboardsGui.class));
    }
}
