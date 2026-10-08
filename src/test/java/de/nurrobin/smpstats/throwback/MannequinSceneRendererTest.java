package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real renderer against MockBukkit's entities: spawn, hidden-by-default, shown to the owner, removal. */
class MannequinSceneRendererTest {
    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;
    private final java.util.List<String> log = new java.util.ArrayList<>();

    /** Records hide/show order; the mock has no per-player visibility. */
    private final MannequinSceneRenderer.Visibility visibility = new MannequinSceneRenderer.Visibility() {
        @Override
        public void hideByDefault(Entity entity) {
            log.add("hide " + entity.getUniqueId());
        }

        @Override
        public void skin(Mannequin figure, org.bukkit.entity.Player viewer) {
            log.add("skin of " + viewer.getName());
        }

        @Override
        public void styleText(org.bukkit.entity.TextDisplay display, Component text) {
            log.add("text");
        }

        @Override
        public void stylePanel(org.bukkit.entity.TextDisplay display, Component text, float scale) {
            log.add("panel " + scale);
        }

        @Override
        public void styleInteraction(org.bukkit.entity.Interaction interaction, float width, float height) {
            log.add("interaction");
        }

        @Override
        public void show(org.bukkit.entity.Player viewer, Plugin plugin, Entity entity) {
            log.add("show " + entity.getUniqueId() + " to " + viewer.getName());
        }
    };

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("throwback_hall");
        plugin = MockBukkit.createMockPlugin("SMPStats");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private long nonPlayers() {
        return world.getEntities().stream().filter(e -> !(e instanceof org.bukkit.entity.Player)).count();
    }

    @Test
    void sceneEntitiesAreHiddenByDefaultAndRemovedOnClose() {
        PlayerMock viewer = server.addPlayer("Vera");
        MannequinSceneRenderer renderer = new MannequinSceneRenderer(plugin, visibility);
        SceneRenderer.SceneSession session = renderer.open(viewer);
        Location base = new Location(world, 0.5, 64, 12.5, 90f, 0f);

        session.figure(base, SceneLibrary.spec(SceneType.MINE));
        session.text(base.clone().add(0, 2.5, 0), Component.text("hallo"));
        session.interaction(base, 1f, 1f, ThrowbackService.ACTION_RULES);

        int count = session.entityCount();
        assertTrue(count > 5, "figure, ore wall props, text and button: " + count);
        assertEquals(count, world.getEntities().stream().filter(e -> !(e instanceof org.bukkit.entity.Player)).count());
        for (Entity e : world.getEntities()) {
            if (e instanceof org.bukkit.entity.Player) {
                continue;
            }
            int hide = log.indexOf("hide " + e.getUniqueId());
            int show = log.indexOf("show " + e.getUniqueId() + " to Vera");
            assertTrue(hide >= 0 && show > hide, "hidden by default first, then shown to the owner only: " + e.getType());
            assertFalse(e.isPersistent(), "nothing may be written to the world files");
            assertTrue(e.getPersistentDataContainer().has(HallWorld.tagKey(plugin)), "tagged for cleanup");
        }
        assertEquals(1, world.getEntitiesByClass(Mannequin.class).size());
        assertTrue(log.contains("skin of Vera"), "the figure wears the viewer's skin");

        session.close();

        assertEquals(0, nonPlayers(), "no entity may be left behind");
        assertEquals(0, session.entityCount());
        session.close();
    }

    @Test
    void closedSessionsIgnoreFurtherWork() {
        PlayerMock viewer = server.addPlayer("Vera");
        SceneRenderer.SceneSession session = new MannequinSceneRenderer(plugin, visibility).open(viewer);
        session.close();

        session.figure(new Location(world, 0, 64, 0), SceneLibrary.spec(SceneType.TIME));
        session.text(new Location(world, 0, 64, 0), Component.text("x"));

        assertEquals(0, nonPlayers());
    }

    @Test
    void infoBoardsHaveNoFigure() {
        PlayerMock viewer = server.addPlayer("Vera");
        SceneRenderer.SceneSession session = new MannequinSceneRenderer(plugin, visibility).open(viewer);

        session.figure(new Location(world, 0.5, 64, 0.5, 0f, 0f), SceneLibrary.spec(SceneType.INFO));

        assertTrue(world.getEntitiesByClass(Mannequin.class).isEmpty());
        assertTrue(nonPlayers() > 0, "the Christmas tree props are there");
        assertNotNull(Material.SPRUCE_LOG);
        session.close();
    }

    @Test
    void panelsKeepTheirYawAndAreShownToTheOwnerOnly() {
        PlayerMock viewer = server.addPlayer("Vera");
        SceneRenderer.SceneSession session = new MannequinSceneRenderer(plugin, visibility).open(viewer);

        session.panel(new Location(world, 2.4, 65.2, 40.5, 90f, 30f), Component.text("Regeln"), 0.5f);

        var displays = world.getEntitiesByClass(org.bukkit.entity.TextDisplay.class);
        assertEquals(1, displays.size());
        var panel = displays.iterator().next();
        assertEquals(90f, panel.getLocation().getYaw(), 0.01f);
        assertEquals(0f, panel.getLocation().getPitch(), 0.01f, "a wall panel hangs upright");
        assertTrue(log.contains("panel 0.5"));
        assertTrue(log.indexOf("show " + panel.getUniqueId() + " to Vera") > log.indexOf("hide " + panel.getUniqueId()));
        session.close();
        assertEquals(0, nonPlayers());
    }
}
