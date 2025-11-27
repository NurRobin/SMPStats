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

        // All category buttons should be present (slots 0-6)
        for (int i = 0; i < LeaderboardsGui.LeaderboardType.values().length; i++) {
            assertNotNull(inv.getItem(i), "Category button at slot " + i + " should exist");
        }
    }

    @Test
    void showsPlayersInCorrectOrder() {
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

        // First player should be at slot 18 (gold block for #1)
        assertNotNull(inv.getItem(18));
        assertEquals(Material.GOLD_BLOCK, inv.getItem(18).getType());
        
        // Second player should be at slot 19 (iron block for #2)
        assertNotNull(inv.getItem(19));
        assertEquals(Material.IRON_BLOCK, inv.getItem(19).getType());
        
        // Third player should be at slot 20 (copper block for #3)
        assertNotNull(inv.getItem(20));
        assertEquals(Material.COPPER_BLOCK, inv.getItem(20).getType());
    }

    @Test
    void switchesCategory() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(1); // Kills category
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
        for (int i = 0; i < 25; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (25 - i));
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
        
        // Should NOT have previous page button on first page
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
        for (int i = 0; i < 25; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (25 - i));
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
        for (int i = 0; i < 25; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (25 - i));
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
        
        // Click same category (PLAYTIME is at slot 0)
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(0);
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
        
        // Just verify it doesn't throw and creates the GUI
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
    void playerRank4AndBeyondUsesPlayerHead() {
        List<StatsRecord> stats = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            StatsRecord record = new StatsRecord(UUID.randomUUID(), "Player" + i);
            record.setPlaytimeMillis(1000000L * (5 - i));
            stats.add(record);
        }
        
        when(statsService.getAllStats()).thenReturn(stats);

        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        Inventory inv = gui.getInventory();

        // Rank 4 (slot 21) should use PLAYER_HEAD
        assertNotNull(inv.getItem(21));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(21).getType());
    }

    @Test
    void opensPlayerInventory() {
        when(statsService.getAllStats()).thenReturn(new ArrayList<>());
        
        LeaderboardsGui gui = new LeaderboardsGui(plugin, guiManager, statsService, healthService,
                LeaderboardsGui.LeaderboardType.PLAYTIME, 0);
        
        gui.open(player);
        
        // MockBukkit tracks open inventory
        assertNotNull(player.getOpenInventory());
    }
}
