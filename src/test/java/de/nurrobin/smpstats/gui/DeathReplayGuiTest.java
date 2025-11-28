package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.timeline.DeathReplayEntry;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DeathReplayGuiTest {
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
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasDeathHistoryHeader() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Death history header at slot 4
        ItemStack header = inventory.getItem(4);
        assertNotNull(header);
        assertEquals(Material.SKELETON_SKULL, header.getType());
    }

    @Test
    void hasBackButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 45
        ItemStack back = inventory.getItem(45);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasRefreshButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Refresh button at slot 53
        ItemStack refresh = inventory.getItem(53);
        assertNotNull(refresh);
        assertEquals(Material.SUNFLOWER, refresh.getType());
    }

    @Test
    void hasPageInfoSlot() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Page info at slot 49
        ItemStack pageInfo = inventory.getItem(49);
        assertNotNull(pageInfo);
        assertEquals(Material.PAPER, pageInfo.getType());
    }

    @Test
    void showsNoDeathsMessageWhenEmpty() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show totem with no deaths message at slot 22
        ItemStack noDeaths = inventory.getItem(22);
        assertNotNull(noDeaths);
        assertEquals(Material.TOTEM_OF_UNDYING, noDeaths.getType());
    }

    @Test
    void implementsInventoryGui() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertDoesNotThrow(() -> gui.open(viewer));
    }

    @Test
    void fillsBackgroundWithGlassPanes() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Top row should be black stained glass
        ItemStack topCorner = inventory.getItem(0);
        assertNotNull(topCorner);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, topCorner.getType());
    }

    @Test
    void showsDeathEntriesWhenDeathsExist() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        // Save a death replay for the viewer
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "fell from a high place",
                0.0,
                "world",
                100, 64, 200,
                25.0,
                List.of(),
                List.of("ZOMBIE"),
                List.of("DIAMOND_SWORD")
        );
        storage.saveDeathReplay(entry);
        
        // Verify the death was saved and can be loaded
        List<DeathReplayEntry> loaded = storage.loadDeathReplaysForPlayer(viewer.getUniqueId(), 10);
        assertEquals(1, loaded.size(), "Death replay should be stored and loadable");
        assertEquals("fell from a high place", loaded.get(0).cause(), "Cause should be preserved");
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // First death slot is at index 10
        ItemStack death = inventory.getItem(10);
        assertNotNull(death, "Slot 10 should have an item");
        
        // Check that it's not a glass pane (meaning no death was displayed)
        assertFalse(death.getType().name().endsWith("STAINED_GLASS_PANE"), 
                "Slot 10 should have a death item, not glass pane. Got: " + death.getType());
        
        // Fall deaths should have FEATHER icon
        assertEquals(Material.FEATHER, death.getType(), 
                "Fall death should show FEATHER icon. Loaded cause was: " + loaded.get(0).cause());
    }

    @Test
    void supportsPageConstructor() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId(), 1);
        
        assertNotNull(gui);
        assertNotNull(gui.getInventory());
    }

    @Test
    void getDeathCauseIconReturnsCorrectIcons() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        // Test lava death
        DeathReplayEntry lavaEntry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "tried to swim in lava",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of(),
                List.of(),
                List.of()
        );
        storage.saveDeathReplay(lavaEntry);
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Lava deaths should have LAVA_BUCKET icon
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.LAVA_BUCKET, death.getType());
    }

    @Test
    void showsNearbyPlayerInfo() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        // Create death with nearby players
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "was slain by TestPlayer",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of("Player1", "Player2"),
                List.of(),
                List.of()
        );
        storage.saveDeathReplay(entry);
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Slain by player should show DIAMOND_SWORD icon
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.DIAMOND_SWORD, death.getType());
        
        // Item should have lore with nearby players
        assertTrue(death.hasItemMeta());
        assertTrue(death.getItemMeta().hasLore());
    }

    @Test
    void showsDeathsWithInventoryInfo() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        // Create death with inventory items
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "drowned",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of(),
                List.of(),
                List.of("DIAMOND_PICKAXE", "NETHERITE_SWORD", "GOLDEN_APPLE")
        );
        storage.saveDeathReplay(entry);
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Drown deaths should show WATER_BUCKET icon
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.WATER_BUCKET, death.getType());
        assertTrue(death.hasItemMeta());
    }

    @Test
    void showsZombieDeathWithCorrectIcon() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "was killed by Zombie",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of(),
                List.of("ZOMBIE"),
                List.of()
        );
        storage.saveDeathReplay(entry);
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.ZOMBIE_HEAD, death.getType());
    }

    @Test
    void showsCreeperDeathWithCorrectIcon() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "was blown up by Creeper",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of(),
                List.of("CREEPER"),
                List.of()
        );
        storage.saveDeathReplay(entry);
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.CREEPER_HEAD, death.getType());
    }

    @Test
    void showsVoidDeathWithCorrectIcon() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "fell into the void",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of(),
                List.of(),
                List.of()
        );
        storage.saveDeathReplay(entry);
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.END_PORTAL_FRAME, death.getType());
    }

    @Test
    void showsSkeletonDeathWithCorrectIcon() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "was shot by Skeleton",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of(),
                List.of("SKELETON"),
                List.of()
        );
        storage.saveDeathReplay(entry);
        
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.SKELETON_SKULL, death.getType());
    }

    @Test
    void canViewDifferentPlayerDeaths() throws SQLException {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        StatsStorage storage = storageOpt.get();
        
        // Create another player
        PlayerMock otherPlayer = server.addPlayer("OtherPlayer");
        statsService.handleJoin(otherPlayer);
        
        // Save a death for the other player
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis(),
                otherPlayer.getUniqueId().toString(),
                otherPlayer.getName(),
                "went up in flames",
                0.0,
                "world",
                100, 64, 200,
                0.0,
                List.of(),
                List.of(),
                List.of()
        );
        storage.saveDeathReplay(entry);
        
        // Viewer opens other player's death history
        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, otherPlayer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show the fire death (flames = fire/lava death)
        ItemStack death = inventory.getItem(10);
        assertNotNull(death);
        assertEquals(Material.LAVA_BUCKET, death.getType()); // fire uses LAVA_BUCKET
    }

    @Test
    void paginatesDeathsAndNavigatesBetweenPages() throws SQLException {
        StatsStorage storage = plugin.getStatsStorage().orElseThrow();

        for (int i = 0; i < 25; i++) {
            DeathReplayEntry entry = new DeathReplayEntry(
                    System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(i),
                    viewer.getUniqueId().toString(),
                    viewer.getName(),
                    "fell from a high place " + i,
                    0.0,
                    "world",
                    i, 64, i,
                    10.0,
                    List.of(),
                    List.of(),
                    List.of()
            );
            storage.saveDeathReplay(entry);
        }

        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        guiManager.openGui(viewer, gui);
        assertNotNull(gui.getInventory().getItem(50), "Next page button should be present");

        InventoryClickEvent next = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                50, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(next);
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof DeathReplayGui);

        InventoryClickEvent prev = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                48, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        ((DeathReplayGui) viewer.getOpenInventory().getTopInventory().getHolder()).handleClick(prev);
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof DeathReplayGui);
    }

    @Test
    void formatsMobsAndInventoryInLore() throws SQLException {
        StatsStorage storage = plugin.getStatsStorage().orElseThrow();
        DeathReplayEntry entry = new DeathReplayEntry(
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(10),
                viewer.getUniqueId().toString(),
                viewer.getName(),
                "was killed by Skeleton",
                0.0,
                "world",
                10, 64, 10,
                8.0,
                List.of(),
                List.of("ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "BLAZE", "WITCH"),
                List.of("DIAMOND_SWORD", "GOLDEN_APPLE")
        );
        storage.saveDeathReplay(entry);

        DeathReplayGui gui = new DeathReplayGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        ItemStack deathItem = gui.getInventory().getItem(10);
        assertNotNull(deathItem);

        List<String> lore = deathItem.getItemMeta().getLore().stream()
                .map(Object::toString)
                .toList();

        assertTrue(lore.stream().anyMatch(l -> l.contains("... and")), "Lore should collapse long mob lists");
        assertTrue(lore.stream().anyMatch(l -> l.contains("item")), "Lore should mention carried items");
    }
}
