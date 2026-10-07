package de.nurrobin.smpstats.throwback;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ThrowbackSettingsTest {
    private static final Logger LOG = Logger.getLogger("test");

    @Test
    void defaultsWhenSectionIsMissing() {
        ThrowbackSettings s = ThrowbackSettings.read(null, LOG);

        assertTrue(s.enabled());
        assertEquals("throwback_hall", s.world());
        assertEquals("2025", s.season());
        assertEquals(60, s.minPlaytimeMinutes());
        assertEquals(15, s.rankBadgeTop());
        assertEquals("throwback/images", s.imagesFolder());
        assertFalse(s.layout().usable());
        assertNull(s.spawnPortal());
    }

    @Test
    void worldNameCannotEscapeIntoAPath() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", "../../etc");

        assertEquals("throwback_hall", ThrowbackSettings.read(yaml, LOG).world());
    }

    @Test
    void layoutRoundTripsThroughYaml() throws Exception {
        HallLayout layout = PlaceholderHall.layout();
        YamlConfiguration yaml = new YamlConfiguration();
        layout.write(yaml.createSection("layout"));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());

        HallLayout read = HallLayout.read(reloaded.getConfigurationSection("layout"), LOG);

        assertEquals(layout.start(), read.start());
        assertEquals(layout.stages(), read.stages());
        assertEquals(layout.rules(), read.rules());
        assertEquals(layout.portal(), read.portal());
        assertEquals(layout.walls(), read.walls());
        assertTrue(read.usable());
    }

    @Test
    void placeholderLayoutHasTwelveStagesAndThreeWalls() {
        HallLayout layout = PlaceholderHall.layout();

        assertEquals(12, layout.stages().size());
        assertEquals(3, layout.walls().size());
        assertTrue(layout.usable());
        assertFalse(layout.portal().contains(layout.start().x(), layout.start().y(), layout.start().z()));
    }

    @Test
    void stagesMustBeAddedWithoutHoles() {
        HallLayout layout = HallLayout.empty().withStage(0, new Pos(1, 2, 3, 0));

        assertEquals(1, layout.stages().size());
        assertThrows(IllegalArgumentException.class, () -> layout.withStage(2, new Pos(1, 2, 3, 0)));
        assertThrows(IllegalArgumentException.class, () -> layout.withStage(HallLayout.MAX_STAGES, new Pos(1, 2, 3, 0)));
        assertEquals(new Pos(9, 9, 9, 0), layout.withStage(0, new Pos(9, 9, 9, 0)).stages().get(0));
    }

    @Test
    void wallsWithBadNamesOrFacingAreSkipped() {
        YamlConfiguration yaml = new YamlConfiguration();
        java.util.List<java.util.Map<String, Object>> walls = new java.util.ArrayList<>();
        walls.add(new WallDef("ok", 1, 2, 3, BlockFace.NORTH, 4, 3, "T", "B").toMap());
        walls.add(new WallDef("../evil", 1, 2, 3, BlockFace.NORTH, 4, 3, "T", "B").toMap());
        java.util.Map<String, Object> up = new WallDef("up", 1, 2, 3, BlockFace.NORTH, 4, 3, "T", "B").toMap();
        up.put("facing", "UP");
        walls.add(up);
        yaml.set("walls", walls);

        assertEquals(1, WallDef.readAll(yaml, LOG).size());
    }

    @Test
    void wallRightFollowsTheViewer() {
        assertEquals(BlockFace.NORTH, new WallDef("a", 0, 0, 0, BlockFace.EAST, 1, 1, "", "").right());
        assertEquals(BlockFace.EAST, new WallDef("a", 0, 0, 0, BlockFace.SOUTH, 1, 1, "", "").right());
        assertEquals(BlockFace.SOUTH, new WallDef("a", 0, 0, 0, BlockFace.WEST, 1, 1, "", "").right());
        assertEquals(BlockFace.WEST, new WallDef("a", 0, 0, 0, BlockFace.NORTH, 1, 1, "", "").right());
    }

    @Test
    void regionContainsWholeBlocks() {
        Region r = Region.of(1, 64, 53, -1, 66, 54);

        assertTrue(r.contains(1.99, 64.0, 54.99));
        assertFalse(r.contains(2.0, 64.0, 54.0));
        assertEquals(0.5, r.centerX());
    }
}
