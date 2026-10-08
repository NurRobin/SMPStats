package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.season.SeasonData;
import de.nurrobin.smpstats.season.SeasonImporter;
import de.nurrobin.smpstats.season.SeasonService;
import de.nurrobin.smpstats.season.SeasonStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** The edit mode end to end against MockBukkit; particles and per-player visibility are replaced by recorders. */
class ThrowbackEditorTest {
    @TempDir
    Path tempDir;

    private record Dust(UUID viewer, Location at, Color color) {
    }

    private ServerMock server;
    private WorldMock main;
    private WorldMock hallWorld;
    private Plugin plugin;
    private FakeScenes scenes;
    private ThrowbackService service;
    private ThrowbackEditor editor;
    private ThrowbackEditListener listener;
    private final List<Dust> dust = new ArrayList<>();
    private final List<int[]> probed = new ArrayList<>();
    private java.util.Set<List<Integer>> solid = new java.util.HashSet<>();

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        main = server.addSimpleWorld("world");
        hallWorld = server.addSimpleWorld("throwback_hall");
        plugin = MockBukkit.createMockPlugin("SMPStats");
        Path data = Files.createDirectories(tempDir.resolve("data"));
        Plugin storagePlugin = org.mockito.Mockito.mock(Plugin.class);
        org.mockito.Mockito.when(storagePlugin.getDataFolder()).thenReturn(data.toFile());
        org.mockito.Mockito.when(storagePlugin.getLogger()).thenReturn(Logger.getLogger("test"));
        org.bukkit.Server mockServer = org.mockito.Mockito.mock(org.bukkit.Server.class);
        org.mockito.Mockito.when(mockServer.getOnlinePlayers()).thenReturn(List.of());
        org.mockito.Mockito.when(storagePlugin.getServer()).thenReturn(mockServer);
        StatsStorage storage = new StatsStorage(storagePlugin);
        storage.init();
        SeasonStore seasonStore = new SeasonStore(storage);
        seasonStore.replace(new SeasonData("2025", "test", Map.of(), Map.of(), Map.of()), 1L);
        SeasonService seasons = new SeasonService(Logger.getLogger("test"), data.resolve("seasons"), seasonStore,
                new SeasonImporter(), Runnable::run);
        scenes = new FakeScenes();
        ThrowbackSettings settings = new ThrowbackSettings(true, "throwback_hall", "2025", 60, 15, "2026-1", "https://example.org",
                false, 0, "throwback/images", "throwback/texts.yml", "", null, null, 8, PlaceholderHall.layout());
        service = new ThrowbackService(plugin, seasons, new CachedProgressRepository(new ThrowbackStore(storage), Logger.getLogger("test")),
                settings, scenes, new FakeHallWorlds(hallWorld), new MapArtWall(plugin, data.resolve("mapwall.yml")), () -> false,
                () -> 10_000L);
        service.teleporter((player, location) -> {
            player.teleport(location);
            return CompletableFuture.completedFuture(true);
        });
        editor = newEditor();
        listener = new ThrowbackEditListener(editor, service);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private ThrowbackEditor newEditor() {
        return new ThrowbackEditor(plugin, service, scenes,
                (viewer, at, color) -> dust.add(new Dust(viewer.getUniqueId(), at.clone(), color)),
                world -> (x, y, z) -> {
                    probed.add(new int[] {x, y, z});
                    return solid.contains(List.of(x, y, z));
                });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock player(String name) {
        PlayerMock p = new PlayerMock(server, name, UUID.nameUUIDFromBytes(name.getBytes()));
        server.addPlayer(p);
        p.teleport(main.getSpawnLocation());
        p.setGameMode(GameMode.CREATIVE);
        return p;
    }

    /** A builder with a recognisable inventory who has started the edit mode inside the hall. */
    private PlayerMock builder() {
        PlayerMock p = player("Bob");
        p.getInventory().setItem(3, new ItemStack(Material.DIAMOND_PICKAXE));
        p.getInventory().setItem(20, new ItemStack(Material.COBBLESTONE, 64));
        p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        p.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        editor.begin(p);
        server.getScheduler().performTicks(2);
        return p;
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private List<String> messages(PlayerMock p) {
        List<String> out = new ArrayList<>();
        Component c;
        while ((c = p.nextComponentMessage()) != null) {
            out.add(plain(c));
        }
        return out;
    }

    private PlayerInteractEvent click(PlayerMock p, EditTool tool, Action action, Block block) {
        p.getInventory().setHeldItemSlot(tool.ordinal());
        PlayerInteractEvent e = new PlayerInteractEvent(p, action, p.getInventory().getItem(tool.ordinal()), block, BlockFace.UP,
                EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private PlayerInteractEvent rightClick(PlayerMock p, EditTool tool, int x, int y, int z) {
        return click(p, tool, Action.RIGHT_CLICK_BLOCK, hallWorld.getBlockAt(x, y, z));
    }

    private PlayerInteractEvent leftClick(PlayerMock p, EditTool tool, int x, int y, int z) {
        return click(p, tool, Action.LEFT_CLICK_BLOCK, hallWorld.getBlockAt(x, y, z));
    }

    private void look(PlayerMock p, float yaw) {
        Location l = p.getLocation();
        l.setYaw(yaw);
        p.teleport(l);
    }

    // ------------------------------------------------------------------ start

    @Test
    void startingFromOutsideTeleportsIntoTheHallAndSwapsTheInventoryForTools() {
        PlayerMock bob = builder();

        assertEquals(hallWorld, bob.getWorld());
        assertTrue(editor.inSession(bob.getUniqueId()));
        for (EditTool tool : EditTool.values()) {
            assertEquals(tool, editor.toolOf(bob.getInventory().getItem(tool.ordinal())), "slot " + tool.ordinal());
        }
        long nonTools = 0;
        for (ItemStack item : bob.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && !editor.isTool(item)) {
                nonTools++;
            }
        }
        assertEquals(0, nonTools, "the real inventory is out of the way");
        assertNull(bob.getInventory().getHelmet());
        assertTrue(editor.store().hasInventory(bob.getUniqueId()), "and safe in a file");
    }

    @Test
    void toolsAreRecognisedByTheirTagOnly() {
        PlayerMock bob = builder();
        ItemStack real = bob.getInventory().getItem(EditTool.SAVE.ordinal());

        ItemStack renamed = new ItemStack(Material.DIAMOND);
        renamed.editMeta(m -> m.displayName(real.getItemMeta().displayName()));
        ItemStack sameMaterial = new ItemStack(real.getType());
        ItemStack retitled = real.clone();
        retitled.editMeta(m -> m.displayName(Component.text("irgendwas")));

        assertFalse(editor.isTool(renamed), "same name is not enough");
        assertFalse(editor.isTool(sameMaterial), "same material is not enough");
        assertEquals(EditTool.SAVE, editor.toolOf(retitled), "the tag is");
        assertNull(editor.toolOf(null));
        assertNull(editor.toolOf(new ItemStack(Material.AIR)));
    }

    @Test
    void startingInsideTheHallDoesNotTeleport() {
        PlayerMock bob = player("Bob");
        bob.teleport(new Location(hallWorld, 40.5, 64, 40.5));

        editor.begin(bob);

        assertTrue(editor.inSession(bob.getUniqueId()));
        assertEquals(40.5, bob.getLocation().getX(), 1e-9);
    }

    @Test
    void theSessionStartsWithTheMarkersOfTheSavedLayout() {
        PlayerMock bob = builder();

        assertEquals(service.settings().layout(), editor.draft(bob.getUniqueId()).toLayout());
        assertFalse(editor.elements(bob.getUniqueId()).isEmpty());
    }

    @Test
    void startingTwiceDoesNotBackUpTheToolsAsTheRealInventory() {
        PlayerMock bob = builder();

        editor.begin(bob);

        ItemStack back = editor.store().loadInventory(bob.getUniqueId()).orElseThrow().storage().get(3);
        assertEquals(Material.DIAMOND_PICKAXE, back.getType());
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("schon im Bearbeitungsmodus")));
    }

    @Test
    void aPlayerInTheMandatoryTourCannotStartEditing() {
        PlayerMock vera = player("Vera");
        service.flow().onJoin(vera.getUniqueId());

        editor.begin(vera);

        assertFalse(editor.inSession(vera.getUniqueId()));
        assertTrue(messages(vera).stream().anyMatch(m -> m.contains("Rundgang")));
    }

    @Test
    void ifTheInventoryCannotBeSavedNothingIsCleared() throws Exception {
        PlayerMock bob = player("Bob");
        bob.getInventory().setItem(3, new ItemStack(Material.DIAMOND_PICKAXE));
        Path blocker = plugin.getDataFolder().toPath().resolve("throwback/edit");
        Files.createDirectories(blocker.getParent());
        Files.writeString(blocker, "this is a file where the folder should be");

        editor.begin(bob);
        server.getScheduler().performTicks(2);

        assertFalse(editor.inSession(bob.getUniqueId()));
        assertEquals(Material.DIAMOND_PICKAXE, bob.getInventory().getItem(3).getType());
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("nicht sichern")));
    }

    // ------------------------------------------------------------------ ways out

    private void assertOriginalInventory(PlayerMock bob) {
        assertEquals(Material.DIAMOND_PICKAXE, bob.getInventory().getItem(3).getType());
        assertEquals(64, bob.getInventory().getItem(20).getAmount());
        assertEquals(Material.IRON_HELMET, bob.getInventory().getHelmet().getType());
        assertEquals(Material.SHIELD, bob.getInventory().getItemInOffHand().getType());
        for (ItemStack item : bob.getInventory().getContents()) {
            assertFalse(editor.isTool(item), "no tool is left");
        }
        assertFalse(editor.store().hasInventory(bob.getUniqueId()), "the backup is used up");
    }

    @Test
    void cancelGivesTheInventoryBackAndDropsTheDraft() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.START, 30, 63, 30);
        assertTrue(editor.store().hasDraft(bob.getUniqueId()));

        editor.cancel(bob);

        assertFalse(editor.inSession(bob.getUniqueId()));
        assertOriginalInventory(bob);
        assertFalse(editor.store().hasDraft(bob.getUniqueId()), "cancel discards the draft");
        assertEquals(PlaceholderHall.layout(), service.settings().layout(), "nothing was saved");
    }

    @Test
    void quitGivesTheInventoryBackButKeepsTheDraft() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.START, 30, 63, 30);

        server.getPluginManager().callEvent(new PlayerQuitEvent(bob, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertFalse(editor.inSession(bob.getUniqueId()));
        assertOriginalInventory(bob);
        assertTrue(editor.store().hasDraft(bob.getUniqueId()));
    }

    @Test
    void kickGivesTheInventoryBack() {
        PlayerMock bob = builder();

        server.getPluginManager().callEvent(new PlayerKickEvent(bob, Component.text("weg"), Component.empty()));

        assertOriginalInventory(bob);
        assertFalse(editor.inSession(bob.getUniqueId()));
    }

    @Test
    void pluginDisableGivesTheInventoryBackToEveryBuilder() {
        PlayerMock bob = builder();
        PlayerMock amy = player("Amy");
        amy.getInventory().setItem(1, new ItemStack(Material.APPLE, 5));
        editor.begin(amy);
        server.getScheduler().performTicks(2);

        editor.shutdown();

        assertOriginalInventory(bob);
        assertEquals(Material.APPLE, amy.getInventory().getItem(1).getType());
        assertFalse(editor.inSession(amy.getUniqueId()));
    }

    @Test
    void deathDropsNoToolsAndKeepsTheRealInventory() {
        PlayerMock bob = builder();
        List<ItemStack> drops = new ArrayList<>(List.of(bob.getInventory().getItem(0), new ItemStack(Material.STICK)));

        PlayerDeathEvent death = new PlayerDeathEvent(bob, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                drops, 0, Component.empty(), false);
        server.getPluginManager().callEvent(death);

        assertTrue(death.getDrops().isEmpty(), "nothing of the edit mode drops");
        assertTrue(death.getKeepInventory());
        assertOriginalInventory(bob);
        assertFalse(editor.inSession(bob.getUniqueId()));
    }

    @Test
    void leavingTheHallWorldEndsTheSessionAndKeepsTheDraft() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.START, 30, 63, 30);

        bob.teleport(main.getSpawnLocation());
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerChangedWorldEvent(bob, hallWorld));

        assertFalse(editor.inSession(bob.getUniqueId()));
        assertOriginalInventory(bob);
        assertTrue(editor.store().hasDraft(bob.getUniqueId()));
    }

    @Test
    void aCrashLeavesABackupThatTheNextJoinRestoresWithoutDuplicates() {
        PlayerMock bob = builder();
        // crash: no quit handling, the player data keeps the tools; a new server run has a fresh editor
        ThrowbackEditor restarted = newEditor();
        bob.getInventory().setItem(30, new ItemStack(Material.GOLD_INGOT, 3));

        restarted.onJoin(bob);

        assertOriginalInventory(bob);
        int pickaxes = 0;
        int gold = 0;
        for (ItemStack item : bob.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DIAMOND_PICKAXE) {
                pickaxes++;
            }
            if (item != null && item.getType() == Material.GOLD_INGOT) {
                gold += item.getAmount();
            }
        }
        assertEquals(1, pickaxes);
        assertEquals(3, gold, "what the builder picked up meanwhile is not lost either");
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("Inventar")));
    }

    @Test
    void restoringTwiceDoesNotDuplicateAnything() {
        PlayerMock bob = builder();
        ThrowbackEditor restarted = newEditor();

        restarted.onJoin(bob);
        restarted.onJoin(bob);

        long pickaxes = java.util.Arrays.stream(bob.getInventory().getContents())
                .filter(i -> i != null && i.getType() == Material.DIAMOND_PICKAXE).count();
        assertEquals(1, pickaxes);
    }

    @Test
    void toolsThatStayedInTheInventoryWithoutABackupAreRemovedAtJoin() {
        PlayerMock bob = player("Bob");
        bob.getInventory().setItem(0, new ItemStack(Material.APPLE));
        ItemStack stale = new ItemStack(Material.LIME_DYE);
        stale.editMeta(m -> m.getPersistentDataContainer().set(EditTool.key(plugin), org.bukkit.persistence.PersistentDataType.STRING, "save"));
        bob.getInventory().setItem(8, stale);

        editor.onJoin(bob);

        assertNull(bob.getInventory().getItem(8));
        assertEquals(Material.APPLE, bob.getInventory().getItem(0).getType());
    }

    @Test
    void theDraftSurvivesARestartAndIsResumed() {
        PlayerMock bob = builder();
        look(bob, 90f);
        rightClick(bob, EditTool.START, 30, 63, 30);
        List<EditMark> marks = List.copyOf(editor.draft(bob.getUniqueId()).marks());
        editor.shutdown();

        ThrowbackEditor restarted = newEditor();
        restarted.begin(bob);
        server.getScheduler().performTicks(2);

        assertEquals(marks, restarted.draft(bob.getUniqueId()).marks());
        assertEquals(30.5, restarted.draft(bob.getUniqueId()).single(EditKind.START).orElseThrow().x(), 1e-9);
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("Entwurf")));
    }

    // ------------------------------------------------------------------ placing and removing

    private EditDraft draft(PlayerMock p) {
        return editor.draft(p.getUniqueId());
    }

    @Test
    void rightClickPutsTheStartOnTopOfTheBlockLookingTheWayTheBuilderLooks() {
        PlayerMock bob = builder();
        look(bob, 130f);

        PlayerInteractEvent e = rightClick(bob, EditTool.START, 30, 63, -20);

        assertTrue(e.isCancelled(), "the tool is used up by the click, not by the world");
        EditMark start = draft(bob).single(EditKind.START).orElseThrow();
        assertEquals(30.5, start.x(), 1e-9);
        assertEquals(64.0, start.y(), 1e-9);
        assertEquals(-19.5, start.z(), 1e-9);
        assertEquals(90f, start.yaw(), "130 degrees snaps to 90");
    }

    @Test
    void stagesLookTheWayTheBuilderLooksAndBoardsLookBack() {
        PlayerMock bob = builder();
        draft(bob).removeNearest(EditKind.STAGE, PlaceholderHall.layout().stages().get(0).x(), 64,
                PlaceholderHall.layout().stages().get(0).z(), 1);
        look(bob, -100f);

        rightClick(bob, EditTool.STAGE, 60, 63, 60);
        rightClick(bob, EditTool.RULES, 61, 63, 60);
        rightClick(bob, EditTool.PRIVACY, 62, 63, 60);

        assertEquals(-90f, draft(bob).stages().get(draft(bob).stages().size() - 1).yaw(), "stage: look direction");
        assertEquals(90f, draft(bob).single(EditKind.RULES).orElseThrow().yaw(), "rules: look direction + 180");
        assertEquals(90f, draft(bob).single(EditKind.PRIVACY).orElseThrow().yaw());
    }

    @Test
    void wallFramesFaceTheBuilderAndTheNextFreeNumberIsUsed() {
        PlayerMock bob = builder();
        look(bob, 90f);

        rightClick(bob, EditTool.WALL, 70, 63, 70);

        WallDef wall = draft(bob).walls().get(draft(bob).walls().size() - 1).wall();
        assertEquals("bauten-4", wall.name(), "the stand-in hall has bauten-1 to 3");
        assertEquals(BlockFace.EAST, wall.facing());
        assertEquals(64, wall.y());
        assertEquals(4, wall.width());
        assertEquals(3, wall.height());
        assertEquals("Bauten 2025: Bau 4", wall.title());
    }

    @Test
    void wallsizeChangesSizeAndTitleOfTheNextWalls() {
        PlayerMock bob = builder();

        editor.wallSize(bob, 6, 99, "Mein Dorf");
        rightClick(bob, EditTool.WALL, 70, 63, 70);

        WallDef wall = draft(bob).walls().get(draft(bob).walls().size() - 1).wall();
        assertEquals(6, wall.width());
        assertEquals(16, wall.height(), "clamped like the old addwall command");
        assertEquals("Mein Dorf", wall.title());
    }

    @Test
    void aThirteenthStageIsRefused() {
        PlayerMock bob = builder();
        assertEquals(12, draft(bob).stages().size());

        rightClick(bob, EditTool.STAGE, 80, 63, 80);

        assertEquals(12, draft(bob).stages().size());
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("höchstens 12")));
    }

    @Test
    void leftClickRemovesTheNearestMarkerOfTheToolWithinTwoBlocksAndDoesNotBreakTheBlock() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.START, 30, 63, 30);
        hallWorld.getBlockAt(31, 63, 31).setType(Material.STONE);

        PlayerInteractEvent far = leftClick(bob, EditTool.START, 40, 63, 40);
        assertEquals(30.5, draft(bob).single(EditKind.START).orElseThrow().x(), "too far away");

        PlayerInteractEvent near = leftClick(bob, EditTool.START, 31, 63, 31);

        assertTrue(far.isCancelled() && near.isCancelled(), "never break the block");
        assertEquals(Material.STONE, hallWorld.getBlockAt(31, 63, 31).getType());
        assertTrue(draft(bob).single(EditKind.START).isEmpty());
    }

    @Test
    void leftClickOnlyTouchesMarkersOfTheToolInHand() {
        PlayerMock bob = builder();
        Pos stage = PlaceholderHall.layout().stages().get(0);

        leftClick(bob, EditTool.RULES, (int) Math.floor(stage.x()), 63, (int) Math.floor(stage.z()));

        assertEquals(12, draft(bob).stages().size());
    }

    @Test
    void leftClickRemovesTheStageNextToTheClickedBlock() {
        PlayerMock bob = builder();
        Pos stage = PlaceholderHall.layout().stages().get(0);

        leftClick(bob, EditTool.STAGE, (int) Math.floor(stage.x()), 63, (int) Math.floor(stage.z()));

        assertEquals(11, draft(bob).stages().size());
    }

    @Test
    void undoToolTakesBackTheLastMarker() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.START, 30, 63, 30);

        click(bob, EditTool.UNDO, Action.RIGHT_CLICK_AIR, null);

        assertEquals(EditKind.WALL, draft(bob).marks().get(draft(bob).marks().size() - 1).kind(), "the new start is gone");
        assertTrue(draft(bob).single(EditKind.START).isEmpty() || draft(bob).single(EditKind.START).get().x() != 30.5);
    }

    @Test
    void anotherPlayersClicksAndItemsDoNothing() {
        PlayerMock bob = builder();
        PlayerMock other = player("Other");
        other.getInventory().setItem(0, bob.getInventory().getItem(0).clone());
        other.teleport(new Location(hallWorld, 0, 64, 0));
        int before = draft(bob).marks().size();

        PlayerInteractEvent e = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, other.getInventory().getItem(0),
                hallWorld.getBlockAt(5, 63, 5), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);

        assertFalse(e.isCancelled());
        assertEquals(before, draft(bob).marks().size());
    }

    @Test
    void offHandClicksAreIgnored() {
        PlayerMock bob = builder();
        look(bob, 0f);

        PlayerInteractEvent e = new PlayerInteractEvent(bob, Action.RIGHT_CLICK_BLOCK, bob.getInventory().getItem(0),
                hallWorld.getBlockAt(30, 63, 30), BlockFace.UP, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(e);

        assertNotEquals(30.5, draft(bob).single(EditKind.START).map(EditMark::x).orElse(0.0));
    }

    // ------------------------------------------------------------------ locks

    @Test
    void toolsCannotBeDropped() {
        PlayerMock bob = builder();
        Item entity = hallWorld.dropItem(bob.getLocation(), bob.getInventory().getItem(2).clone());

        PlayerDropItemEvent e = new PlayerDropItemEvent(bob, entity);
        server.getPluginManager().callEvent(e);

        assertTrue(e.isCancelled());
    }

    @Test
    void otherItemsMayStillBeDropped() {
        PlayerMock bob = builder();
        Item entity = hallWorld.dropItem(bob.getLocation(), new ItemStack(Material.STICK));

        PlayerDropItemEvent e = new PlayerDropItemEvent(bob, entity);
        server.getPluginManager().callEvent(e);

        assertFalse(e.isCancelled());
    }

    /** MockBukkit cannot build click events from its own views, so the view is a stub with the two items the click involves. */
    private InventoryClickEvent click(PlayerMock p, ItemStack current, ItemStack cursor, ClickType type, InventoryAction action,
                                      int hotbar) {
        org.bukkit.inventory.InventoryView view = org.mockito.Mockito.mock(org.bukkit.inventory.InventoryView.class);
        org.mockito.Mockito.when(view.getPlayer()).thenReturn(p);
        org.mockito.Mockito.when(view.getItem(org.mockito.ArgumentMatchers.anyInt())).thenReturn(current);
        org.mockito.Mockito.when(view.getCursor()).thenReturn(cursor);
        org.mockito.Mockito.when(view.getTopInventory()).thenReturn(p.getInventory());
        org.mockito.Mockito.when(view.getBottomInventory()).thenReturn(p.getInventory());
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.QUICKBAR, 36, type, action, hotbar);
        server.getPluginManager().callEvent(e);
        return e;
    }

    @Test
    void toolsCannotBeMovedByClicksShiftClicksOrNumberKeys() {
        PlayerMock bob = builder();
        ItemStack tool = bob.getInventory().getItem(0).clone();
        ItemStack stick = new ItemStack(Material.STICK);

        assertTrue(click(bob, tool, null, ClickType.LEFT, InventoryAction.PICKUP_ALL, -1).isCancelled(), "pick up");
        assertTrue(click(bob, tool, null, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, -1).isCancelled(), "into a container");
        assertTrue(click(bob, stick, null, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0).isCancelled(), "hotbar key into another slot");
        assertTrue(click(bob, tool, null, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, -1).isCancelled(), "swap to the off hand");
    }

    @Test
    void aToolOnTheCursorCannotBePlacedAnywhere() {
        PlayerMock bob = builder();

        InventoryClickEvent e = click(bob, null, bob.getInventory().getItem(0).clone(), ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        assertTrue(e.isCancelled());
    }

    @Test
    void clicksWithoutToolsAreLeftAlone() {
        PlayerMock bob = builder();

        InventoryClickEvent e = click(bob, new ItemStack(Material.STICK), new ItemStack(Material.STICK), ClickType.LEFT,
                InventoryAction.PLACE_ALL, -1);

        assertFalse(e.isCancelled());
    }

    @Test
    void toolsCannotBeDragged() {
        PlayerMock bob = builder();
        ItemStack tool = bob.getInventory().getItem(1).clone();
        org.bukkit.inventory.InventoryView view = org.mockito.Mockito.mock(org.bukkit.inventory.InventoryView.class);
        org.mockito.Mockito.when(view.getPlayer()).thenReturn(bob);
        org.mockito.Mockito.when(view.getTopInventory()).thenReturn(bob.getInventory());
        org.mockito.Mockito.when(view.getBottomInventory()).thenReturn(bob.getInventory());

        InventoryDragEvent dragTool = new InventoryDragEvent(view, tool, tool, false, Map.of(30, tool));
        InventoryDragEvent dragStick = new InventoryDragEvent(view, new ItemStack(Material.STICK), new ItemStack(Material.STICK), false,
                Map.of(30, new ItemStack(Material.STICK)));
        server.getPluginManager().callEvent(dragTool);
        server.getPluginManager().callEvent(dragStick);

        assertTrue(dragTool.isCancelled());
        assertFalse(dragStick.isCancelled());
    }

    @Test
    void toolsCannotSwapIntoTheOffHand() {
        PlayerMock bob = builder();

        PlayerSwapHandItemsEvent e = new PlayerSwapHandItemsEvent(bob, bob.getInventory().getItem(0), new ItemStack(Material.AIR));
        server.getPluginManager().callEvent(e);

        assertTrue(e.isCancelled());
    }

    @Test
    void containersCannotBeOpenedInTheEditMode() {
        PlayerMock bob = builder();
        org.bukkit.inventory.InventoryView view = org.mockito.Mockito.mock(org.bukkit.inventory.InventoryView.class);
        org.mockito.Mockito.when(view.getPlayer()).thenReturn(bob);

        InventoryOpenEvent e = new InventoryOpenEvent(view);
        server.getPluginManager().callEvent(e);

        assertTrue(e.isCancelled());
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("nichts öffnen")));
    }

    @Test
    void breakingBlocksWithAToolInHandIsStopped() {
        PlayerMock bob = builder();
        Block block = hallWorld.getBlockAt(5, 63, 5);
        block.setType(Material.STONE);
        bob.getInventory().setHeldItemSlot(0);

        org.bukkit.event.block.BlockBreakEvent e = new org.bukkit.event.block.BlockBreakEvent(block, bob);
        server.getPluginManager().callEvent(e);

        assertTrue(e.isCancelled());
    }

    @Test
    void entitiesCannotBeUsedWithAToolEither() {
        PlayerMock bob = builder();
        bob.getInventory().setHeldItemSlot(0);
        org.bukkit.entity.Entity target = hallWorld.spawn(new Location(hallWorld, 0, 64, 0), org.bukkit.entity.Pig.class);

        org.bukkit.event.player.PlayerInteractEntityEvent e = new org.bukkit.event.player.PlayerInteractEntityEvent(bob, target,
                EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);

        assertTrue(e.isCancelled());
    }

    // ------------------------------------------------------------------ boxes

    /** A session on an empty layout, so the only boxes are the ones the test places. */
    private PlayerMock emptyBuilder() {
        service.updateLayoutForTest(HallLayout.empty());
        PlayerMock bob = player("Bob");
        bob.teleport(new Location(hallWorld, 0.5, 64, 0.5));
        editor.begin(bob);
        server.getScheduler().performTicks(2);
        dust.clear();
        return bob;
    }

    @Test
    void aStartColumnIsDrawnWithItsTwelveEdgesOnly() {
        PlayerMock bob = emptyBuilder();
        look(bob, 0f);
        rightClick(bob, EditTool.START, 0, 63, 3);

        int points = editor.drawForTest(bob);

        // a 1 x 1 x 2 column: 8 corners, one point in the middle of each of the 8 short edges, 3 on each of the 4 tall ones
        assertEquals(8 + 8 * 1 + 4 * 3, points);
        assertEquals(points, dust.size());
        assertTrue(dust.stream().allMatch(d -> d.color().equals(ThrowbackEditor.FREE)));
        double spacing = dust.stream().filter(d -> d.at().getX() == 0.0 && d.at().getZ() == 3.0).map(d -> d.at().getY()).sorted()
                .reduce((a, b) -> b - a > 0 ? b : a).orElseThrow();
        assertEquals(66.0, spacing, 1e-9);
    }

    @Test
    void onlyTheBuilderReceivesTheParticles() {
        PlayerMock bob = emptyBuilder();
        PlayerMock other = player("Other");
        other.teleport(new Location(hallWorld, 1, 64, 1));
        look(bob, 0f);
        rightClick(bob, EditTool.START, 0, 63, 3);

        server.getScheduler().performTicks(ThrowbackEditor.PARTICLE_PERIOD);

        assertFalse(dust.isEmpty(), "the tick draws");
        assertTrue(dust.stream().allMatch(d -> d.viewer().equals(bob.getUniqueId())));
    }

    @Test
    void elementsBeyondThirtyTwoBlocksAreNotDrawn() {
        PlayerMock bob = emptyBuilder();
        look(bob, 90f);
        rightClick(bob, EditTool.STAGE, 0, 63, 10);
        rightClick(bob, EditTool.STAGE, 0, 63, 200);

        editor.drawForTest(bob);

        assertFalse(dust.isEmpty());
        assertTrue(dust.stream().allMatch(d -> d.at().getZ() < 40), "nothing near the second stage at z 200");
    }

    @Test
    void freeSpaceIsGreenBlockedSpaceRedAndTheFloorHasItsOwnColour() {
        PlayerMock bob = emptyBuilder();
        look(bob, 90f);
        rightClick(bob, EditTool.STAGE, 0, 63, 10);
        solid.add(List.of(-1, 66, 10));
        editor.blockChanged(bob, hallWorld.getBlockAt(-1, 66, 10));
        server.getScheduler().performTicks(1);

        editor.drawForTest(bob);

        assertTrue(editor.elements(bob.getUniqueId()).get(0).blocked());
        assertTrue(dust.stream().anyMatch(d -> d.color().equals(ThrowbackEditor.BLOCKED)));
        assertTrue(dust.stream().anyMatch(d -> d.color().equals(ThrowbackEditor.FLOOR)));
        assertTrue(dust.stream().noneMatch(d -> d.color().equals(ThrowbackEditor.FREE)));
    }

    @Test
    void theFloorOfAStandDoesNotMakeItRed() {
        PlayerMock bob = emptyBuilder();
        for (int x = -10; x < 10; x++) {
            for (int z = 0; z < 30; z++) {
                solid.add(List.of(x, 63, z));
            }
        }
        look(bob, 90f);

        rightClick(bob, EditTool.STAGE, 0, 63, 10);

        assertFalse(editor.elements(bob.getUniqueId()).get(0).blocked());
    }

    @Test
    void particlesPerRunAreCapped() {
        PlayerMock bob = emptyBuilder();
        look(bob, 90f);
        for (int i = 0; i < 12; i++) {
            rightClick(bob, EditTool.STAGE, 0, 63, 4 + 2 * i);
        }

        int points = editor.drawForTest(bob);

        // a stage is 140 points of free space plus 60 of floor; twelve of them would be 2400
        assertTrue(points <= ThrowbackEditor.MAX_POINTS_PER_RUN, "points: " + points);
        assertEquals(points, dust.size());
        assertTrue(points > ThrowbackEditor.MAX_POINTS_PER_RUN - 200, "but the budget is used: " + points);
        assertTrue(dust.stream().anyMatch(d -> d.at().getZ() < 8), "the nearest stage comes first");
        assertTrue(dust.stream().noneMatch(d -> d.at().getZ() > 24), "the farthest two are dropped");
    }

    @Test
    void theParticleTickReadsTheCacheAndNeverLooksAtBlocks() {
        PlayerMock bob = emptyBuilder();
        look(bob, 90f);
        rightClick(bob, EditTool.STAGE, 0, 63, 10);
        int looked = probed.size();
        assertTrue(looked > 0, "placing checks the blocks once");

        server.getScheduler().performTicks(ThrowbackEditor.PARTICLE_PERIOD * 5);
        editor.drawForTest(bob);

        assertEquals(looked, probed.size());
        assertFalse(dust.isEmpty());
    }

    @Test
    void blockChangesNearAnElementCheckItAgainAndFarOnesDoNot() {
        PlayerMock bob = emptyBuilder();
        look(bob, 90f);
        rightClick(bob, EditTool.STAGE, 0, 63, 10);
        EditElement stage = editor.elements(bob.getUniqueId()).get(0);
        assertFalse(stage.blocked());
        int looked = probed.size();

        editor.blockChanged(bob, hallWorld.getBlockAt(300, 64, 300));
        server.getScheduler().performTicks(1);
        assertEquals(looked, probed.size(), "a block far away costs nothing");

        solid.add(List.of(-1, 66, 10));
        editor.blockChanged(bob, hallWorld.getBlockAt(-1, 66, 10));
        assertFalse(stage.blocked(), "checked next tick, once the change has settled");
        server.getScheduler().performTicks(1);
        assertTrue(stage.blocked());

        solid.clear();
        editor.blockChanged(bob, hallWorld.getBlockAt(-1, 66, 10));
        server.getScheduler().performTicks(1);
        assertFalse(stage.blocked());
    }

    @Test
    void blockEventsOfTheBuilderTriggerTheCheck() {
        PlayerMock bob = emptyBuilder();
        look(bob, 90f);
        rightClick(bob, EditTool.STAGE, 0, 63, 10);
        Block block = hallWorld.getBlockAt(-1, 66, 10);
        solid.add(List.of(-1, 66, 10));
        // the hotbar is all tools, so a free hand has to be made for the test
        bob.getInventory().setItem(8, new ItemStack(Material.STICK));
        bob.getInventory().setHeldItemSlot(8);

        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(block, bob));
        server.getScheduler().performTicks(1);

        assertTrue(editor.elements(bob.getUniqueId()).get(0).blocked());
    }

    // ------------------------------------------------------------------ live preview of the boards

    @Test
    void rulesAndPrivacyBoardsShowAtOnceToTheBuilderOnly() {
        PlayerMock bob = builder();
        PlayerMock other = player("Other");

        FakeScenes.Session preview = scenes.lastOf(bob.getUniqueId());

        assertNotNull(preview);
        assertNull(scenes.lastOf(other.getUniqueId()), "nobody else gets a session");
        assertEquals(2, preview.panels.size(), "the rules board and, without a place of its own, the privacy board next to it");
        Pos rules = service.settings().layout().rules();
        assertEquals(PanelMetrics.rulesBoard(hallWorld, rules).getZ(), preview.panels.get(0).getZ(), 1e-9);
        assertEquals(1, preview.texts - preview.panels.size(), "and the button text");
    }

    @Test
    void settingThePrivacyMarkMovesThePrivacyBoardAndReplacesThePreview() {
        PlayerMock bob = builder();
        FakeScenes.Session before = scenes.lastOf(bob.getUniqueId());
        look(bob, 0f);

        rightClick(bob, EditTool.PRIVACY, 20, 63, 100);

        FakeScenes.Session after = scenes.lastOf(bob.getUniqueId());
        assertNotSame(before, after);
        assertTrue(before.closed, "the old preview is removed");
        assertFalse(after.closed);
        assertEquals(2, after.panels.size());
        assertEquals(20.5, after.panels.get(1).getX(), 1e-9);
        assertEquals(100.5, after.panels.get(1).getZ(), 1e-9);
        assertEquals(64 + PanelMetrics.PRIVACY_Y, after.panels.get(1).getY(), 1e-9);
        assertEquals(0f, after.panels.get(1).getYaw() - 180f, "looking south, the board faces north");
    }

    @Test
    void theRulesPreviewFollowsANewRulesMark() {
        PlayerMock bob = builder();
        look(bob, 90f);

        rightClick(bob, EditTool.RULES, 50, 63, 50);

        Location board = scenes.lastOf(bob.getUniqueId()).panels.get(0);
        Pos rules = draft(bob).single(EditKind.RULES).orElseThrow().pos();
        assertEquals(PanelMetrics.rulesBoard(hallWorld, rules).getX(), board.getX(), 1e-9);
        assertEquals(PanelMetrics.rulesBoard(hallWorld, rules).getZ(), board.getZ(), 1e-9);
    }

    @Test
    void thePreviewIsRemovedWhenTheSessionEnds() {
        PlayerMock bob = builder();

        editor.cancel(bob);

        assertTrue(scenes.lastOf(bob.getUniqueId()).closed);
    }

    @Test
    void teleportingRedrawsThePreviewBecauseShownEntitiesCanGetLost() {
        PlayerMock bob = builder();
        int sessions = scenes.opened.size();

        editor.onTeleported(bob);
        server.getScheduler().performTicks(1);

        assertEquals(sessions + 1, scenes.opened.size());
        assertFalse(scenes.lastOf(bob.getUniqueId()).closed);
    }

    // ------------------------------------------------------------------ saving

    private void removeStages(PlayerMock bob, int count) {
        for (int i = 0; i < count; i++) {
            Pos stage = draft(bob).stages().get(draft(bob).stages().size() - 1).pos();
            leftClick(bob, EditTool.STAGE, (int) Math.floor(stage.x()), 63, (int) Math.floor(stage.z()));
        }
    }

    @Test
    void savingWithMissingPartsShowsTheChecklistAndChangesNothing() {
        PlayerMock bob = builder();
        removeStages(bob, 6);
        messages(bob);

        editor.save(bob);

        List<String> lines = messages(bob);
        assertTrue(lines.contains("Bühnen 6/8 ✗"), lines.toString());
        assertTrue(lines.contains("Start ✓"), lines.toString());
        assertTrue(lines.contains("Regeltafel ✓"));
        assertTrue(lines.contains("Datenschutz-Tafel ✗"), "the stand-in hall has no privacy place yet");
        assertTrue(lines.contains("Portal 2/2 ✓"));
        assertTrue(editor.inSession(bob.getUniqueId()), "the builder stays in the edit mode");
        assertEquals(12, service.settings().layout().stages().size());
        assertEquals(PlaceholderHall.layout(), service.settings().layout());
    }

    @Test
    void theMinimumNumberOfStagesComesFromTheSettings() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.PRIVACY, 20, 63, 100);
        removeStages(bob, 4);
        messages(bob);

        editor.save(bob);

        assertFalse(editor.inSession(bob.getUniqueId()), "8 stages are enough for the default minimum");

        PlayerMock amy = player("Amy");
        service.updateLayoutForTest(PlaceholderHall.layout().withPrivacy(new Pos(1, 64, 1, 0)));
        editor.begin(amy);
        server.getScheduler().performTicks(2);
        removeStages(amy, 5);
        messages(amy);
        editor.save(amy);
        assertTrue(messages(amy).contains("Bühnen 7/8 ✗"));
    }

    @Test
    void aCompleteDraftIsSavedAsTheNewLayoutAndTheInventoryComesBack() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.PRIVACY, 20, 63, 100);
        rightClick(bob, EditTool.START, 7, 63, 7);
        messages(bob);

        click(bob, EditTool.SAVE, Action.RIGHT_CLICK_AIR, null);

        HallLayout saved = service.settings().layout();
        assertEquals(new Pos(20.5, 64, 100.5, 180f), saved.privacy());
        assertEquals(new Pos(7.5, 64, 7.5, 0f), saved.start());
        assertEquals(12, saved.stages().size());
        assertFalse(editor.inSession(bob.getUniqueId()));
        assertOriginalInventory(bob);
        assertFalse(editor.store().hasDraft(bob.getUniqueId()), "a saved draft is gone");
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("Halle gespeichert: 12 Bühnen, 3 Bilderwände")));
        assertEquals(20.5, plugin.getConfig().getDouble("throwback.layout.privacy.x"), "written to config.yml");
        assertTrue(scenes.lastOf(bob.getUniqueId()).closed);
    }

    @Test
    void removedPicturesLeaveTheSavedLayout() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.PRIVACY, 20, 63, 100);
        WallDef gone = draft(bob).walls().get(0).wall();
        leftClick(bob, EditTool.WALL, gone.x(), gone.y() - 1, gone.z());

        editor.save(bob);

        assertEquals(2, service.settings().layout().walls().size());
        assertTrue(service.settings().layout().walls().stream().noneMatch(w -> w.name().equals(gone.name())));
    }

    @Test
    void blockedBoxesOnlyWarnWhenSaving() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.PRIVACY, 20, 63, 100);
        Pos stage = draft(bob).stages().get(2).pos();
        int x = (int) Math.floor(stage.x());
        solid.add(List.of(x, 66, (int) Math.floor(stage.z())));
        editor.blockChanged(bob, hallWorld.getBlockAt(x, 66, (int) Math.floor(stage.z())));
        server.getScheduler().performTicks(1);
        messages(bob);

        editor.save(bob);

        assertFalse(editor.inSession(bob.getUniqueId()), "saved anyway");
        assertTrue(messages(bob).stream().anyMatch(m -> m.contains("Hinweis: Bühne 3 ragt in feste Blöcke")));
    }

    @Test
    void cancelledDraftsNeverReachTheLayout() {
        PlayerMock bob = builder();
        look(bob, 0f);
        rightClick(bob, EditTool.START, 7, 63, 7);

        editor.cancel(bob);

        assertEquals(PlaceholderHall.layout(), service.settings().layout());
    }

    @Test
    void theActionbarChecklistListsEveryRequiredPart() {
        PlayerMock bob = builder();

        EditReport report = editor.report(bob.getUniqueId());
        server.getScheduler().performTicks(ThrowbackEditor.PARTICLE_PERIOD);

        assertEquals(5, report.checks().size());
        assertFalse(report.complete(), "privacy is missing in the stand-in hall");
        assertNull(editor.report(UUID.randomUUID()));
    }

    // ------------------------------------------------------------------ the command and its permission

    private ThrowbackCommand command;

    /** One instance for the whole test, because portal1 and portal2 share state in it. */
    private ThrowbackCommand command() {
        if (command == null) {
            command = new ThrowbackCommand(plugin, service, () -> service.settings(), editor);
        }
        return command;
    }

    private void run(PlayerMock p, String... args) {
        command().onCommand(p, null, "throwback", args);
        server.getScheduler().performTicks(2);
    }

    @Test
    void theBuildPermissionAloneAllowsTheEditMode() {
        PlayerMock bob = player("Bob");
        bob.addAttachment(plugin, "smpstats.throwback.build", true);

        run(bob, "admin", "edit");

        assertTrue(editor.inSession(bob.getUniqueId()));
    }

    @Test
    void theBuildPermissionDoesNotOpenTheOtherAdminCommands() {
        PlayerMock bob = player("Bob");
        bob.addAttachment(plugin, "smpstats.throwback.build", true);
        bob.teleport(new Location(hallWorld, 5.5, 64, 5.5));

        run(bob, "admin", "setstart");
        run(bob, "admin", "reset", "Someone");
        run(bob, "admin", "build");

        assertEquals(PlaceholderHall.layout(), service.settings().layout());
        assertTrue(messages(bob).stream().filter(m -> m.contains("Berechtigung")).count() >= 3);
    }

    @Test
    void adminsMayEditToo() {
        PlayerMock amy = player("Amy");
        amy.addAttachment(plugin, "smpstats.admin", true);

        run(amy, "admin", "edit");

        assertTrue(editor.inSession(amy.getUniqueId()));
    }

    @Test
    void withoutAnyPermissionThereIsNoEditMode() {
        PlayerMock nobody = player("Nobody");

        run(nobody, "admin", "edit");

        assertFalse(editor.inSession(nobody.getUniqueId()));
        assertTrue(messages(nobody).stream().anyMatch(m -> m.contains("Berechtigung")));
    }

    @Test
    void theSmpstatsAdminPermissionIncludesTheBuildPermission() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/plugin.yml")) {
            yaml.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }

        assertTrue(yaml.getBoolean("permissions.smpstats.admin.children.smpstats.throwback.build"));
        assertTrue(yaml.isSet("permissions.smpstats.throwback.build.description"), "declared with a description");
        assertFalse(yaml.getBoolean("permissions.smpstats.throwback.build.default", false), "nobody has it by default");
    }

    @Test
    void editSubcommandsSaveCancelAndWallsize() {
        PlayerMock amy = player("Amy");
        amy.addAttachment(plugin, "smpstats.admin", true);
        run(amy, "admin", "edit");
        run(amy, "admin", "edit", "wallsize", "5", "2", "Mein", "Dorf");
        look(amy, 0f);
        rightClick(amy, EditTool.WALL, 70, 63, 70);

        WallDef wall = draft(amy).walls().get(draft(amy).walls().size() - 1).wall();
        assertEquals(5, wall.width());
        assertEquals("Mein Dorf", wall.title());

        run(amy, "admin", "edit", "wallsize", "x");
        assertTrue(messages(amy).stream().anyMatch(m -> m.contains("Nutze: /throwback admin edit wallsize")));

        run(amy, "admin", "edit", "save");
        assertTrue(editor.inSession(amy.getUniqueId()), "no privacy place yet, so saving is refused");

        run(amy, "admin", "edit", "cancel");
        assertFalse(editor.inSession(amy.getUniqueId()));
    }

    @Test
    void tabCompletionOffersOnlyEditToBuilders() {
        PlayerMock bob = player("Bob");
        bob.addAttachment(plugin, "smpstats.throwback.build", true);
        PlayerMock amy = player("Amy");
        amy.addAttachment(plugin, "smpstats.admin", true);

        assertEquals(List.of("admin"), command().onTabComplete(bob, null, "throwback", new String[] {"a"}));
        assertEquals(List.of("edit"), command().onTabComplete(bob, null, "throwback", new String[] {"admin", ""}));
        assertTrue(command().onTabComplete(amy, null, "throwback", new String[] {"admin", "e"}).contains("edit"));
        assertEquals(List.of("save", "cancel", "wallsize"), command().onTabComplete(bob, null, "throwback", new String[] {"admin", "edit", ""}));
        assertTrue(command().onTabComplete(player("Nobody"), null, "throwback", new String[] {"a"}).isEmpty());
    }

    @Test
    void theOldMeasuringCommandsStillWork() {
        PlayerMock amy = player("Amy");
        amy.addAttachment(plugin, "smpstats.admin", true);
        amy.teleport(new Location(hallWorld, 9.5, 64, 9.5, 90f, 0f));

        run(amy, "admin", "setstart");
        run(amy, "admin", "setrules");
        run(amy, "admin", "setstage", "2");
        run(amy, "admin", "portal1");
        amy.teleport(new Location(hallWorld, 12.5, 66, 12.5));
        run(amy, "admin", "portal2");
        // addwall reads Entity#getFacing, which MockBukkit does not implement

        HallLayout l = service.settings().layout();
        assertEquals(9.5, l.start().x(), 1e-9);
        assertEquals(9.5, l.rules().x(), 1e-9);
        assertEquals(9.5, l.stages().get(1).x(), 1e-9);
        assertEquals(Region.of(9, 64, 9, 12, 66, 12), l.portal());
    }

    // ------------------------------------------------------------------ the hall stays loaded

    @Test
    void theHallWorldIsNotUnloadedWhileSomeoneIsInside() {
        HallWorld real = new HallWorld(plugin, "throwback_hall", 0);
        PlayerMock bob = player("Bob");
        bob.teleport(new Location(hallWorld, 0.5, 64, 0.5));

        assertFalse(real.unload(), "a builder in the hall keeps the world loaded");
        assertNotNull(server.getWorld("throwback_hall"));
    }
}
