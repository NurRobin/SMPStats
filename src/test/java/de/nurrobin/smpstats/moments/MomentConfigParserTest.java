package de.nurrobin.smpstats.moments;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentConfigParserTest {

    private final MomentConfigParser parser = new MomentConfigParser();

    @Test
    void returnsDefaultsWhenNoSectionProvided() {
        List<MomentDefinition> defaults = parser.parse(null);
        assertFalse(defaults.isEmpty());
        assertEquals(8, defaults.size());
        assertTrue(defaults.stream().anyMatch(def -> def.getId().equals("diamond_run")));
        assertTrue(defaults.stream().anyMatch(def -> def.getTrigger() == MomentDefinition.TriggerType.FIRST_DEATH));
    }

    @Test
    void parsesCustomDefinitionsAndIgnoresInvalidValues() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection definitions = config.createSection("definitions");
        ConfigurationSection boss = definitions.createSection("boss");
        boss.set("type", "boss_kill");
        boss.set("title", "Boss fight");
        boss.set("detail", "details here");
        boss.set("merge_seconds", 12);
        boss.set("first_only", true);
        boss.set("materials", List.of("DIAMOND_BLOCK", "not_a_material"));
        boss.set("min_fall_distance", 3.5);
        boss.set("max_health_after_damage", 5.5);
        boss.set("causes", List.of("lava", " "));
        boss.set("require_self", true);
        boss.set("entity_types", List.of("wither", "ENDER_DRAGON"));

        ConfigurationSection fallback = definitions.createSection("fallback");
        fallback.set("type", "unknown_type");

        List<MomentDefinition> parsed = parser.parse(config);
        assertEquals(2, parsed.size());

        MomentDefinition bossDef = parsed.stream()
                .filter(def -> def.getId().equals("boss"))
                .findFirst()
                .orElseThrow();
        assertEquals(MomentDefinition.TriggerType.BOSS_KILL, bossDef.getTrigger());
        assertEquals("Boss fight", bossDef.getTitle());
        assertEquals("details here", bossDef.getDetail());
        assertEquals(12, bossDef.getMergeSeconds());
        assertTrue(bossDef.isFirstOnly());
        assertEquals(1, bossDef.getMaterials().size());
        assertTrue(bossDef.getMaterials().contains(Material.DIAMOND_BLOCK));
        assertEquals(3.5, bossDef.getMinFallDistance());
        assertEquals(5.5, bossDef.getMaxHealthAfterDamage());
        assertEquals(Set.of("LAVA"), bossDef.getCauses());
        assertTrue(bossDef.isRequireSelf());
        assertEquals(Set.of("WITHER", "ENDER_DRAGON"), bossDef.getEntityTypes());

        MomentDefinition fallbackDef = parsed.stream()
                .filter(def -> def.getId().equals("fallback"))
                .findFirst()
                .orElseThrow();
        assertEquals(MomentDefinition.TriggerType.BLOCK_BREAK, fallbackDef.getTrigger());
        assertEquals("fallback", fallbackDef.getTitle());
        assertEquals("", fallbackDef.getDetail());
    }
}
