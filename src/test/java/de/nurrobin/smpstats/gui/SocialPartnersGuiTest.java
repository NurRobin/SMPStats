package de.nurrobin.smpstats.gui;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import de.nurrobin.smpstats.SMPStats;
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

import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SocialPartnersGuiTest {
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
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        assertNotNull(inventory);
        assertEquals(54, inventory.getSize());
    }

    @Test
    void hasHeaderItem() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Header at slot 4
        ItemStack header = inventory.getItem(4);
        assertNotNull(header);
        assertEquals(Material.PLAYER_HEAD, header.getType());
    }

    @Test
    void hasBackButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Back button at slot 45
        ItemStack back = inventory.getItem(45);
        assertNotNull(back);
        assertEquals(Material.ARROW, back.getType());
    }

    @Test
    void hasFilterButton() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Filter button at slot 49
        ItemStack filter = inventory.getItem(49);
        assertNotNull(filter);
        assertEquals(Material.HOPPER, filter.getType());
    }

    @Test
    void hasTimeStatsItem() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Total time at slot 46
        ItemStack timeItem = inventory.getItem(46);
        assertNotNull(timeItem);
        assertEquals(Material.CLOCK, timeItem.getType());
    }

    @Test
    void hasKillStatsItem() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Shared kills at slot 47
        ItemStack killsItem = inventory.getItem(47);
        assertNotNull(killsItem);
        assertEquals(Material.DIAMOND_SWORD, killsItem.getType());
    }

    @Test
    void showsNoPartnersMessageWhenEmpty() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        Inventory inventory = gui.getInventory();
        
        // Should show barrier with no partners message at slot 22
        ItemStack noPartners = inventory.getItem(22);
        assertNotNull(noPartners);
        assertEquals(Material.BARRIER, noPartners.getType());
    }

    @Test
    void implementsInventoryGui() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertInstanceOf(InventoryGui.class, gui);
    }

    @Test
    void canOpenForViewer() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        
        assertDoesNotThrow(() -> gui.open(viewer));
    }

    @Test
    void handleClickBackReturnsToPlayerStats() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        guiManager.openGui(viewer, gui);
        
        // Click on Back button (slot 45)
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                45, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Should return to PlayerStatsGui
        assertTrue(viewer.getOpenInventory().getTopInventory().getHolder() instanceof PlayerStatsGui);
    }

    @Test
    void handleClickFilterChangesFilter() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        guiManager.openGui(viewer, gui);
        
        // Initial filter should be ALL (HOPPER)
        Inventory inventory = gui.getInventory();
        ItemStack filterBefore = inventory.getItem(49);
        assertNotNull(filterBefore);
        assertEquals(Material.HOPPER, filterBefore.getType());
        
        // Click on Filter button (slot 49)
        InventoryClickEvent event = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                49, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        
        // Filter changes to ONLINE (ENDER_EYE)
        ItemStack filterAfter = inventory.getItem(49);
        assertNotNull(filterAfter);
        assertEquals(Material.ENDER_EYE, filterAfter.getType());
    }

    @Test
    void filterModeEnumValues() {
        // Test that FilterMode enum has expected values
        SocialPartnersGui.FilterMode[] modes = SocialPartnersGui.FilterMode.values();
        assertEquals(3, modes.length);
        assertEquals(SocialPartnersGui.FilterMode.ALL, SocialPartnersGui.FilterMode.valueOf("ALL"));
        assertEquals(SocialPartnersGui.FilterMode.ONLINE, SocialPartnersGui.FilterMode.valueOf("ONLINE"));
        assertEquals(SocialPartnersGui.FilterMode.TOP_PARTNERS, SocialPartnersGui.FilterMode.valueOf("TOP_PARTNERS"));
    }

    @Test
    void cyclesThroughFilterModes() {
        Optional<StatsStorage> storageOpt = plugin.getStatsStorage();
        assertTrue(storageOpt.isPresent());
        
        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storageOpt.get(), viewer, viewer.getUniqueId());
        guiManager.openGui(viewer, gui);
        
        // Click filter button 3 times to cycle through all modes (slot 49)
        for (int i = 0; i < 3; i++) {
            InventoryClickEvent event = new InventoryClickEvent(
                    viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                    49, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertDoesNotThrow(() -> gui.handleClick(event));
        }
        
        // GUI should still be functional after cycling - back to HOPPER (ALL)
        assertEquals(Material.HOPPER, gui.getInventory().getItem(49).getType());
    }

    @Test
    void showsPartnersAndFiltersOnlineAndTop() throws Exception {
        StatsStorage storage = plugin.getStatsStorage().orElseThrow();

        PlayerMock bestFriend = server.addPlayer("BestFriend");
        statsService.handleJoin(bestFriend);
        UUID offlinePartner = UUID.randomUUID();

        storage.incrementSocialPair(viewer.getUniqueId(), bestFriend.getUniqueId(), 20_000, 5, 2, 3);
        storage.incrementSocialPair(viewer.getUniqueId(), offlinePartner, 300, 1, 0, 1);

        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        guiManager.openGui(viewer, gui);

        int initialHeads = countHeads(gui.getInventory());
        assertTrue(initialHeads >= 2, "Both partners should be visible initially");

        InventoryClickEvent filterOnline = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                49, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(filterOnline);
        assertEquals(1, countHeads(gui.getInventory()), "Online filter should hide offline partners");

        InventoryClickEvent filterTop = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                49, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(filterTop);
        assertEquals(1, countHeads(gui.getInventory()), "Top partners filter keeps best friend only");
    }

    @Test
    void clickingOfflinePartnerShowsMessage() throws Exception {
        StatsStorage storage = plugin.getStatsStorage().orElseThrow();
        UUID offlinePartner = UUID.randomUUID();
        storage.incrementSocialPair(viewer.getUniqueId(), offlinePartner, 5_000, 0, 0, 0);

        SocialPartnersGui gui = new SocialPartnersGui(plugin, guiManager, statsService,
                storage, viewer, viewer.getUniqueId());
        guiManager.openGui(viewer, gui);

        int partnerSlot = findFirstHeadSlot(gui.getInventory());
        assertTrue(partnerSlot >= 0, "Expected a partner head to click");

        InventoryClickEvent clickPartner = new InventoryClickEvent(
                viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                partnerSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        gui.handleClick(clickPartner);

        String message = viewer.nextMessage();
        assertNotNull(message, "Clicking an offline partner should send feedback");
        assertTrue(message.contains("offline"));
    }

    private int countHeads(Inventory inventory) {
        int heads = 0;
        int[] partnerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int slot : partnerSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                heads++;
            }
        }
        return heads;
    }

    private int findFirstHeadSlot(Inventory inventory) {
        int[] partnerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int slot : partnerSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.PLAYER_HEAD) {
                return slot;
            }
        }
        return -1;
    }
}
