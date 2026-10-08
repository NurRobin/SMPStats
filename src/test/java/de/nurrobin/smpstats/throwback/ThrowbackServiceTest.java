package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.season.SeasonData;
import de.nurrobin.smpstats.season.SeasonImporter;
import de.nurrobin.smpstats.season.SeasonPlayer;
import de.nurrobin.smpstats.season.SeasonService;
import de.nurrobin.smpstats.season.SeasonStore;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ThrowbackServiceTest {
    @TempDir
    Path tempDir;

    private ServerMock server;
    private WorldMock main;
    private WorldMock hallWorld;
    private Plugin plugin;
    private FakeScenes scenes;
    private ThrowbackService service;
    private ThrowbackStore progressStore;
    private final AtomicLong clock = new AtomicLong(10_000);
    private static final UUID VERA = TestData.id(1);

    private final class FakeHall implements HallWorlds {
        boolean unloaded;

        @Override
        public boolean isHall(World world) {
            return world != null && world.getName().equals("throwback_hall");
        }

        @Override
        public World loaded() {
            return unloaded ? null : hallWorld;
        }

        @Override
        public World load() {
            unloaded = false;
            return hallWorld;
        }

        @Override
        public boolean unload() {
            unloaded = true;
            return true;
        }

        @Override
        public void configure(String name) {
        }
    }

    private FakeHall hall;

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
        org.mockito.Mockito.when(mockServer.getOnlinePlayers()).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(storagePlugin.getServer()).thenReturn(mockServer);
        StatsStorage storage = new StatsStorage(storagePlugin);
        storage.init();
        SeasonStore seasonStore = new SeasonStore(storage);
        seasonStore.replace(new SeasonData("2025", "test",
                Map.of(VERA, new SeasonPlayer(VERA, "Vera", 30 * TestData.HOUR)),
                Map.of(VERA, Map.of("smpstats:blocks_broken", 5000.0)), Map.of()), 1L);
        SeasonService seasons = new SeasonService(Logger.getLogger("test"), data.resolve("seasons"), seasonStore,
                new SeasonImporter(), Runnable::run);
        progressStore = new ThrowbackStore(storage);
        scenes = new FakeScenes();
        hall = new FakeHall();
        HallLayout layout = PlaceholderHall.layout();
        ThrowbackSettings settings = new ThrowbackSettings(true, "throwback_hall", "2025", 60, 15, "2026-1", "https://example.org",
                false, 0, "throwback/images", "throwback/texts.yml", "", null, null, 8, layout);
        service = new ThrowbackService(plugin, seasons, new CachedProgressRepository(progressStore, Logger.getLogger("test")),
                settings, scenes, hall, new MapArtWall(plugin, data.resolve("mapwall.yml")), () -> false, clock::get);
        service.teleporter((player, location) -> {
            player.teleport(location);
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        });
        service.ensureBoard();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock join(String name, UUID uuid) {
        PlayerMock p = new PlayerMock(server, name, uuid);
        server.addPlayer(p);
        p.teleport(main.getSpawnLocation());
        p.setGameMode(GameMode.SURVIVAL);
        service.preLogin(uuid);
        service.onJoin(p);
        server.getScheduler().performTicks(40);
        return p;
    }

    private void walkInto(PlayerMock p, Region region) {
        p.teleport(new Location(hallWorld, region.centerX(), region.minY(), region.centerZ()));
        service.tickHall();
        server.getScheduler().performTicks(5);
    }

    @Test
    void firstJoinTeleportsIntoTheHallAndBuildsTwelveStagesForThatPlayerOnly() {
        PlayerMock vera = join("Vera", VERA);
        PlayerMock other = join("Other", UUID.randomUUID());

        assertEquals(hallWorld, vera.getWorld());
        assertEquals(GameMode.ADVENTURE, vera.getGameMode());
        FakeScenes.Session veraScenes = scenes.lastOf(VERA);
        assertNotNull(veraScenes);
        assertEquals(1 + 1 + 12, veraScenes.figures >= 12 ? 14 : veraScenes.figures + 2, "start, rules station and 12 stages built");
        assertTrue(veraScenes.figures >= 12, "twelve stage figures plus the statue: " + veraScenes.figures);
        assertEquals(1, veraScenes.interactions, "one rules button");
        assertNotEquals(scenes.lastOf(VERA), scenes.lastOf(other.getUniqueId()), "every player has a session of their own");
        assertEquals(VERA, veraScenes.viewer);
    }

    @Test
    void withoutAPrivacyPlaceTheBoardsHangSideBySideNextToTheRulesStation() {
        PlayerMock vera = join("Vera", VERA);

        java.util.List<Location> panels = scenes.lastOf(VERA).panels;

        Pos rules = service.settings().layout().rules();
        assertEquals(2, panels.size());
        assertLocation(PanelMetrics.rulesBoard(hallWorld, rules), panels.get(0));
        assertLocation(PanelMetrics.privacyBoard(hallWorld, rules, null), panels.get(1));
        assertEquals(rules.z() - 2, panels.get(0).getZ(), 1e-9, "rules on the reader's left");
        assertEquals(rules.z() + 2, panels.get(1).getZ(), 1e-9);
        assertEquals(hallWorld, vera.getWorld());
    }

    @Test
    void thePrivacyBoardHangsAtItsOwnPlaceWhenTheLayoutHasOne() {
        Pos own = new Pos(20.5, 64, 100.5, 180f);
        service.updateLayoutForTest(service.settings().layout().withPrivacy(own));

        join("Vera", VERA);

        java.util.List<Location> panels = scenes.lastOf(VERA).panels;
        assertEquals(2, panels.size());
        assertLocation(PanelMetrics.rulesBoard(hallWorld, service.settings().layout().rules()), panels.get(0));
        assertEquals(20.5, panels.get(1).getX(), 1e-9);
        assertEquals(100.5, panels.get(1).getZ(), 1e-9);
        assertEquals(64, panels.get(1).getY(), 1e-9, "the own place is the bottom middle of the board");
        assertEquals(180f, panels.get(1).getYaw());
    }

    private static void assertLocation(Location expected, Location actual) {
        assertEquals(expected.getX(), actual.getX(), 1e-9);
        assertEquals(expected.getY(), actual.getY(), 1e-9);
        assertEquals(expected.getZ(), actual.getZ(), 1e-9);
        assertEquals(expected.getYaw(), actual.getYaw());
    }

    @Test
    void portalStaysClosedWithoutRulesAndOpensAfterConfirmation() throws Exception {
        PlayerMock vera = join("Vera", VERA);
        Region portal = service.settings().layout().portal();

        walkInto(vera, portal);
        assertEquals(hallWorld, vera.getWorld(), "blocked");
        assertTrue(service.flow().inHall(VERA));

        assertTrue(service.confirmRules(vera, false));
        clock.set(20_000);
        walkInto(vera, portal);

        assertEquals(main, vera.getWorld());
        assertEquals(GameMode.SURVIVAL, vera.getGameMode(), "game mode comes back");
        assertFalse(service.flow().inHall(VERA));
        assertTrue(scenes.lastOf(VERA).closed, "the scenes are gone");
        server.getScheduler().performTicks(5);
        service.repository().close();
        Progress saved = progressStore.find(VERA).orElseThrow();
        assertEquals(10_000L, saved.rulesConfirmedAt());
        assertEquals("2026-1", saved.rulesVersion());
        assertEquals(20_000L, saved.completedAt());
    }

    @Test
    void laterJoinsAreNotSentToTheHall() {
        PlayerMock vera = join("Vera", VERA);
        service.confirmRules(vera, false);
        walkInto(vera, service.settings().layout().portal());
        service.onQuit(vera);

        PlayerMock again = join("Vera", VERA);

        assertEquals(main, again.getWorld());
        assertEquals(1, scenes.opened.size(), "no new scenes");
    }

    @Test
    void quitInTheMiddleOfTheTourRemovesEverythingAndTheNextJoinStartsOver() {
        PlayerMock vera = join("Vera", VERA);
        service.confirmRules(vera, false);

        service.onQuit(vera);

        assertTrue(scenes.lastOf(VERA).closed, "nothing stays behind after a quit");
        assertEquals(0, service.entityCount(VERA));
        assertFalse(service.flow().inHall(VERA));
        assertEquals(GameMode.SURVIVAL, vera.getGameMode());

        PlayerMock back = join("Vera", VERA);
        assertEquals(hallWorld, back.getWorld());
        assertFalse(service.flow().rulesRead(VERA), "rules have to be confirmed again after a restart");
        assertEquals(2, scenes.opened.size());
    }

    @Test
    void leavingTheHallWorldBySomeOtherWayCleansUp() {
        PlayerMock vera = join("Vera", VERA);
        service.confirmRules(vera, false);
        walkInto(vera, service.settings().layout().portal());
        PlayerMock second = join("Second", UUID.randomUUID());

        second.teleport(main.getSpawnLocation());
        service.onWorldChanged(second, hallWorld);

        assertTrue(scenes.lastOf(second.getUniqueId()).closed);
        assertFalse(service.flow().inHall(second.getUniqueId()));
    }

    @Test
    void oneQuitDoesNotTouchTheOtherPlayersScenes() {
        PlayerMock a = join("Alpha", UUID.randomUUID());
        PlayerMock b = join("Beta", UUID.randomUUID());

        service.onQuit(a);

        assertTrue(scenes.lastOf(a.getUniqueId()).closed);
        assertFalse(scenes.lastOf(b.getUniqueId()).closed);
        assertTrue(service.hasVisuals(b.getUniqueId()));
    }

    @Test
    void visitorsComeBackToWhereTheyStoodWithoutConfirmingAnything() {
        PlayerMock vera = join("Vera", VERA);
        service.confirmRules(vera, false);
        walkInto(vera, service.settings().layout().portal());
        Location spot = new Location(main, 100.5, 70, -40.5);
        vera.teleport(spot);

        assertTrue(service.visit(vera));
        server.getScheduler().performTicks(40);
        assertEquals(hallWorld, vera.getWorld());
        assertEquals(ThrowbackFlow.Mode.VISIT, service.flow().mode(VERA).orElseThrow());

        service.flow().rulesRead(VERA);
        walkInto(vera, service.settings().layout().portal());

        assertEquals(main, vera.getWorld());
        assertEquals(100.5, vera.getLocation().getX(), 0.01);
        assertEquals(-40.5, vera.getLocation().getZ(), 0.01);
    }

    @Test
    void tourPlayersCannotTeleportOutButVisitorsCan() {
        PlayerMock vera = join("Vera", VERA);
        Location inside = new Location(hallWorld, 0, 64, 0);
        Location outside = new Location(main, 0, 64, 0);

        assertTrue(service.blocksLeaving(vera, inside, outside), "a /spawn during the mandatory tour is stopped");
        assertFalse(service.blocksLeaving(vera, inside, new Location(hallWorld, 3, 64, 3)));

        service.confirmRules(vera, false);
        walkInto(vera, service.settings().layout().portal());
        vera.teleport(main.getSpawnLocation());
        assertTrue(service.visit(vera));
        server.getScheduler().performTicks(40);
        assertFalse(service.blocksLeaving(vera, inside, outside), "visits can be left any way");
    }

    @Test
    void rulesChatFallbackNeedsTheRulesStationNearby() {
        PlayerMock vera = join("Vera", VERA);
        vera.teleport(new Location(hallWorld, -1, 64, -6));

        assertFalse(service.confirmRules(vera, true), "too far away");
        assertFalse(service.flow().rulesRead(VERA));

        Pos rules = service.settings().layout().rules();
        vera.teleport(new Location(hallWorld, rules.x() - 2, rules.y(), rules.z()));
        assertTrue(service.confirmRules(vera, true));
        assertTrue(service.flow().rulesRead(VERA));
    }

    @Test
    void hallStaysOffUntilTheLayoutIsComplete() {
        service.updateLayoutForTest(HallLayout.empty());

        PlayerMock p = join("Vera", VERA);

        assertEquals(main, p.getWorld());
        assertTrue(scenes.opened.isEmpty());
    }

    @Test
    void worldUnloadsAfterTheLastVisitorLeft() {
        PlayerMock vera = join("Vera", VERA);
        service.confirmRules(vera, false);
        walkInto(vera, service.settings().layout().portal());
        server.getScheduler().performTicks(5);

        assertTrue(hall.unloaded, "hall world is released when empty");
    }
}
