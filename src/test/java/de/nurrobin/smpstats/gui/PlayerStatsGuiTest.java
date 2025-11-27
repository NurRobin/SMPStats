package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class PlayerStatsGuiTest {
    private ServerMock server;
    private SMPStats plugin;
    private GuiManager guiManager;
    private StatsService statsService;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        guiManager = mock(GuiManager.class);
        statsService = mock(StatsService.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void showsNoStatsMessageWhenEmpty() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.empty());
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();
        
        assertNotNull(inv.getItem(22));
        assertEquals(Material.BARRIER, inv.getItem(22).getType());
    }

    @Test
    void showsStats() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setMobKills(10);
        record.setPlayerKills(5);
        record.setDeaths(3);
        record.setBlocksBroken(100);
        record.setBlocksPlaced(50);
        
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Kills
        assertNotNull(inv.getItem(12));
        assertEquals(Material.DIAMOND_SWORD, inv.getItem(12).getType());
        
        // Deaths
        assertNotNull(inv.getItem(14));
        assertEquals(Material.SKELETON_SKULL, inv.getItem(14).getType());
        
        // Blocks
        assertNotNull(inv.getItem(16));
        assertEquals(Material.GRASS_BLOCK, inv.getItem(16).getType());
    }
    
    @Test
    void backButtonNavigatesToMainMenu() {
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.empty());
        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(36); // New back button position
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Should either open MainMenu or close inventory
        // If ServerHealthService is available, it opens MainMenu
    }

    @Test
    void refreshButtonRefreshesStats() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setMobKills(10);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));
        
        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(44); // Refresh button
        when(event.getWhoClicked()).thenReturn(player);
        
        gui.handleClick(event);
        
        // Verify stats were fetched again
        verify(statsService, atLeast(2)).getStats(player.getUniqueId());
    }

    @Test
    void showsPlayerHead() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Player head at slot 4
        assertNotNull(inv.getItem(4));
        assertEquals(Material.PLAYER_HEAD, inv.getItem(4).getType());
    }

    @Test
    void showsPlaytimeAndDistance() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setPlaytimeMillis(7200000); // 2 hours in millis
        record.setDistanceOverworld(5000.0); // 5000 blocks
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Playtime at slot 10
        assertNotNull(inv.getItem(10));
        assertEquals(Material.CLOCK, inv.getItem(10).getType());

        // Distance at slot 20
        assertNotNull(inv.getItem(20));
        assertEquals(Material.LEATHER_BOOTS, inv.getItem(20).getType());
    }

    @Test
    void showsItemsCrafted() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setItemsCrafted(100);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Items crafted at slot 24
        assertNotNull(inv.getItem(24));
        assertEquals(Material.CRAFTING_TABLE, inv.getItem(24).getType());
    }

    @Test
    void showsDamageStats() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        record.setDamageDealt(1000.0);
        record.setDamageTaken(500.0);
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        Inventory inv = gui.getInventory();

        // Combat at slot 22
        assertNotNull(inv.getItem(22));
        assertEquals(Material.IRON_SWORD, inv.getItem(22).getType());
    }

    @Test
    void opensPlayerInventory() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);
        gui.open(player);

        assertNotNull(player.getOpenInventory());
    }

    @Test
    void clickOutsideIgnored() {
        StatsRecord record = new StatsRecord(player.getUniqueId(), player.getName());
        when(statsService.getStats(player.getUniqueId())).thenReturn(Optional.of(record));

        PlayerStatsGui gui = new PlayerStatsGui(plugin, guiManager, statsService, player);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getSlot()).thenReturn(50); // Outside of inventory
        when(event.getWhoClicked()).thenReturn(player);

        gui.handleClick(event); // Should not throw
    }
}
