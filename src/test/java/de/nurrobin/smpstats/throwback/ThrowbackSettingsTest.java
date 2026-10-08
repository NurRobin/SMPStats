package de.nurrobin.smpstats.throwback;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        HallLayout base = PlaceholderHall.layout();
        HallLayout layout = new HallLayout(base.start(), base.stages(), base.rules(), base.privacy(), base.portal(),
                List.of(new HallText(new Pos(1.5, 64, 2.5, 90f), "<gold>Titel<br>Zeile 2")));
        YamlConfiguration yaml = new YamlConfiguration();
        layout.write(yaml.createSection("layout"));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());

        HallLayout read = HallLayout.read(reloaded.getConfigurationSection("layout"), LOG);

        assertEquals(layout.start(), read.start());
        assertEquals(layout.stages(), read.stages());
        assertEquals(layout.rules(), read.rules());
        assertNull(read.privacy(), "the stand-in hall has no privacy place of its own");
        assertEquals(layout.portal(), read.portal());
        assertEquals(layout.texts(), read.texts());
        assertTrue(read.usable());
    }

    @Test
    void placeholderLayoutHasTwelveStagesAndNoTexts() {
        HallLayout layout = PlaceholderHall.layout();

        assertEquals(12, layout.stages().size());
        assertTrue(layout.texts().isEmpty());
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
    void textsWithoutCoordinatesOrContentAreSkipped() {
        List<Map<String, Object>> texts = new ArrayList<>();
        texts.add(new HallText(new Pos(1, 2, 3, 90f), "ok").toMap());
        texts.add(new HallText(new Pos(1, 2, 3, 90f), "  ").toMap());
        Map<String, Object> noZ = new HallText(new Pos(1, 2, 3, 90f), "no z").toMap();
        noZ.remove("z");
        texts.add(noZ);
        texts.add(Map.of("x", 1, "y", 2, "z", 3));

        List<Map<?, ?>> raw = new ArrayList<>(texts);
        List<HallText> read = HallText.readAll(raw, LOG);

        assertEquals(List.of(new HallText(new Pos(1, 2, 3, 90f), "ok")), read);
    }

    @Test
    void aTextWithoutYawReadsAsYawZero() {
        List<HallText> read = HallText.readAll(List.of(Map.of("x", 1, "y", 2, "z", 3, "text", "T")), LOG);

        assertEquals(0f, read.get(0).pos().yaw());
    }

    @Test
    void writingALayoutDropsTheOldWallsKeyAndReplacesTexts() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("layout");
        section.set("walls", List.of(Map.of("name", "bauten-1")));
        new HallLayout(null, List.of(), null, null, null, List.of(new HallText(new Pos(1, 2, 3, 0f), "A"))).write(section);

        assertFalse(section.contains("walls"));
        assertEquals(1, section.getMapList("texts").size());

        PlaceholderHall.layout().write(section);

        assertTrue(section.getMapList("texts").isEmpty());
    }

    @Test
    void regionContainsWholeBlocks() {
        Region r = Region.of(1, 64, 53, -1, 66, 54);

        assertTrue(r.contains(1.99, 64.0, 54.99));
        assertFalse(r.contains(2.0, 64.0, 54.0));
        assertEquals(0.5, r.centerX());
    }

    @Test
    void placeholderStagesAreMirroredAcrossTheCorridor() {
        java.util.List<Pos> stages = PlaceholderHall.layout().stages();
        for (int i = 0; i < stages.size(); i += 2) {
            Pos west = stages.get(i);
            Pos east = stages.get(i + 1);
            org.junit.jupiter.api.Assertions.assertEquals(0.5, (west.x() + east.x()) / 2, 1e-9,
                    "the corridor spans blocks -2..2, so its middle is x = 0.5");
            org.junit.jupiter.api.Assertions.assertEquals(west.z(), east.z(), 1e-9);
        }
    }

    @Test
    void privacyPlaceRoundTripsThroughYaml() throws Exception {
        HallLayout layout = PlaceholderHall.layout().withPrivacy(new Pos(4.5, 64, 90.5, -90f));
        YamlConfiguration yaml = new YamlConfiguration();
        layout.write(yaml.createSection("layout"));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());

        HallLayout read = HallLayout.read(reloaded.getConfigurationSection("layout"), LOG);

        assertEquals(new Pos(4.5, 64, 90.5, -90f), read.privacy());
        assertEquals(layout, read);
    }

    @Test
    void oldLayoutsWithoutPrivacyStillRead() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        PlaceholderHall.layout().write(yaml.createSection("layout"));
        yaml.set("layout.privacy", null);

        HallLayout read = HallLayout.read(yaml.getConfigurationSection("layout"), LOG);

        assertNull(read.privacy());
        assertTrue(read.usable(), "a hall without a privacy place is still usable");
    }

    @Test
    void writingWithoutPrivacyRemovesAnOldEntry() {
        YamlConfiguration yaml = new YamlConfiguration();
        PlaceholderHall.layout().withPrivacy(new Pos(1, 2, 3, 0)).write(yaml.createSection("layout"));
        assertTrue(yaml.isConfigurationSection("layout.privacy"));

        PlaceholderHall.layout().write(yaml.getConfigurationSection("layout"));

        assertFalse(yaml.isConfigurationSection("layout.privacy"));
    }

    @Test
    void withersKeepThePrivacyPlace() {
        HallLayout layout = PlaceholderHall.layout().withPrivacy(new Pos(1, 2, 3, 0));

        assertEquals(new Pos(1, 2, 3, 0), layout.withStart(new Pos(0, 0, 0, 0)).privacy());
        assertEquals(new Pos(1, 2, 3, 0), layout.withRules(new Pos(0, 0, 0, 0)).privacy());
        assertEquals(new Pos(1, 2, 3, 0), layout.withPortal(Region.of(0, 0, 0, 1, 1, 1)).privacy());
        assertEquals(new Pos(1, 2, 3, 0), layout.withStage(0, new Pos(9, 9, 9, 0)).privacy());
    }

    @Test
    void editMinimumStagesDefaultsToEightAndIsClamped() {
        assertEquals(8, ThrowbackSettings.read(null, LOG).editMinStages());

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("edit.min_stages", 10);
        assertEquals(10, ThrowbackSettings.read(yaml, LOG).editMinStages());
        yaml.set("edit.min_stages", 99);
        assertEquals(12, ThrowbackSettings.read(yaml, LOG).editMinStages());
        yaml.set("edit.min_stages", 0);
        assertEquals(1, ThrowbackSettings.read(yaml, LOG).editMinStages());
    }

    @Test
    void shippedConfigHasTheEditSection() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            yaml.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }

        assertEquals(8, yaml.getInt("throwback.edit.min_stages"));
        assertEquals(8, ThrowbackSettings.read(yaml.getConfigurationSection("throwback"), LOG).editMinStages());
    }
}
