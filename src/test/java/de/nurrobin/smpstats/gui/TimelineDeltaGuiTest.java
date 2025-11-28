package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimelineDeltaGuiTest {
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
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasThisWeekHeader() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // This week header at slot 2
        ItemStack header = inventory.getItem(2);
        assertNotNull(header);
        assertEquals(Material.LIME_BANNER, header.getType());
    }

    @Test
    void hasVsIcon() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // VS icon at slot 4
        ItemStack vs = inventory.getItem(4);
        assertNotNull(vs);
        assertEquals(Material.COMPARATOR, vs.getType());
    }

    @Test
    void hasLastWeekHeader() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Last week header at slot 6
        ItemStack header = inventory.getItem(6);
        assertNotNull(header);
        assertEquals(Material.YELLOW_BANNER, header.getType());
    }

    @Test
    void hasBackButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 49
        ItemStack back = inventory.getItem(49);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasRefreshButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Refresh button at slot 53
        ItemStack refresh = inventory.getItem(53);
        assertNotNull(refresh);
        assertEquals(Material.SUNFLOWER, refresh.getType());
    }

    @Test
    void showsNoDataMessageWhenEmpty() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show clock with no data message at slot 22
        ItemStack noData = inventory.getItem(22);
        assertNotNull(noData);
        assertEquals(Material.CLOCK, noData.getType());
    }

    @Test
    void implementsInventoryGui() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertDoesNotThrow(() -> gui.open(viewer));
    }

    @Test
    void fillsBackgroundWithGlassPanes() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Top row should be black stained glass
        ItemStack topCorner = inventory.getItem(0);
        assertNotNull(topCorner);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, topCorner.getType());
    }

    @Test
    void emptyDataShowsNoComparisonIcons() {
        // When there is no timeline data, comparison rows are not rendered
        // Instead, the "no data" message is shown at slot 22
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Slots where comparison icons would be are filled with glass panes
        ItemStack slot10 = inventory.getItem(10);
        assertNotNull(slot10);
        assertTrue(slot10.getType().name().endsWith("STAINED_GLASS_PANE"));
        
        ItemStack slot12 = inventory.getItem(12);
        assertNotNull(slot12);
        assertTrue(slot12.getType().name().endsWith("STAINED_GLASS_PANE"));
    }

    @Test
    void rendersComparisonRowsWhenTimelineDataExists() throws Exception {
        StatsStorage storage = plugin.getStatsStorage().orElseThrow();

        StatsRecord record = new StatsRecord(viewer.getUniqueId(), viewer.getName());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        // Baseline two weeks ago
        record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(1));
        storage.upsertTimeline(record, today.minusDays(14));

        // End of last week
        record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(5));
        record.setBlocksBroken(1000);
        record.setBlocksPlaced(500);
        record.setPlayerKills(5);
        record.setMobKills(25);
        record.setDeaths(5);
        record.setDistanceOverworld(500);
        record.setDistanceNether(500);
        record.setDistanceEnd(0);
        record.setItemsCrafted(200);
        record.setItemsConsumed(50);
        record.setDamageDealt(5000);
        storage.upsertTimeline(record, today.minusDays(7));

        // End of this week
        record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(15));
        record.setBlocksBroken(5000);
        record.setBlocksPlaced(2000);
        record.setPlayerKills(20);
        record.setMobKills(2025); // large enough to trigger K formatting
        record.setDeaths(6); // slight increase to test lower-is-better branch
        record.setDistanceOverworld(3500);
        record.setDistanceNether(1500);
        record.setDistanceEnd(800);
        record.setItemsCrafted(1500);
        record.setItemsConsumed(400);
        record.setDamageDealt(15000);
        storage.upsertTimeline(record, today);

        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        Inventory inventory = gui.getInventory();

        // Playtime row should show an improvement (lime glass and hours formatted)
        ItemStack playtime = inventory.getItem(9);
        assertNotNull(playtime);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, playtime.getType());
        String playtimeName = PlainTextComponentSerializer.plainText().serialize(playtime.getItemMeta().displayName());
        assertTrue(playtimeName.contains("h"), "Playtime should be formatted with hours: " + playtimeName);

        // Deaths row should treat lower as better and still use lime glass due to fewer deaths than last week
        ItemStack deaths = inventory.getItem(13);
        assertNotNull(deaths);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, deaths.getType());

        // Distance should be formatted as km when large enough
        ItemStack distance = inventory.getItem(18);
        assertNotNull(distance);
        String distanceName = PlainTextComponentSerializer.plainText().serialize(distance.getItemMeta().displayName());
        assertTrue(distanceName.contains("km"), "Distance should be shown in km: " + distanceName);

        // Summary verdict should favor improvements and use the emerald icon
        ItemStack summary = inventory.getItem(40);
        assertNotNull(summary);
        assertEquals(Material.EMERALD, summary.getType());
    }

    @Test
    void handleClickRefreshRebuildsPage() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());

        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        gui.open(viewer);

        InventoryClickEvent refresh = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                53, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        assertDoesNotThrow(() -> gui.handleClick(refresh));
        assertNotNull(gui.getInventory().getItem(49), "Page info should still be present after refresh");
    }

    @Test
    void handleClickBackClosesInventory() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());

        TimelineDeltaGui gui = new TimelineDeltaGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        gui.open(viewer);

        InventoryClickEvent back = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                49, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        gui.handleClick(back);
        assertNotEquals(gui.getInventory(), viewer.getOpenInventory().getTopInventory(), "Back should close the GUI");
    }
}
