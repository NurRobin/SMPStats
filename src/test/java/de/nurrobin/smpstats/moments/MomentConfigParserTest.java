package de.nurrobin.smpstats.moments;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentConfigParserTest {

    @Test
    void loadsDefaultsWhenSectionMissing() {
        MomentConfigParser parser = new MomentConfigParser();
        List<MomentDefinition> defs = parser.parse(null);
        assertFalse(defs.isEmpty());
    }

    @Test
    void parsesCustomDefinitions() {
        String yaml = """
                moments:
                  definitions:
                    my_diamonds:
                      type: block_break
                      title: "Shiny"
                      detail: "Found {count}"
                      merge_seconds: 45
                      materials: [DIAMOND_ORE]
                    fall_fail:
                      type: death_fall
                      min_fall_distance: 60
                      title: "Ouch"
                      detail: "Fell {fall}"
                    clutch:
                      type: damage_low_hp
                      max_health_after_damage: 1.0
                      causes: [FALL]
                """;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml));
        MomentConfigParser parser = new MomentConfigParser();
        List<MomentDefinition> defs = parser.parse(config.getConfigurationSection("moments"));

        assertEquals(3, defs.size());
        MomentDefinition diamond = defs.stream().filter(d -> d.getId().equals("my_diamonds")).findFirst().orElseThrow();
        assertEquals(MomentDefinition.TriggerType.BLOCK_BREAK, diamond.getTrigger());
        assertEquals(45, diamond.getMergeSeconds());
        assertTrue(diamond.getMaterials().stream().anyMatch(m -> m.name().equals("DIAMOND_ORE")));

        MomentDefinition fall = defs.stream().filter(d -> d.getId().equals("fall_fail")).findFirst().orElseThrow();
        assertEquals(60, fall.getMinFallDistance(), 0.0001);

        MomentDefinition clutch = defs.stream().filter(d -> d.getId().equals("clutch")).findFirst().orElseThrow();
        assertEquals(MomentDefinition.TriggerType.DAMAGE_LOW_HP, clutch.getTrigger());
        assertEquals(1.0, clutch.getMaxHealthAfterDamage(), 0.0001);
        assertTrue(clutch.getCauses().contains("FALL"));
    }
}
