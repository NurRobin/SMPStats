package de.nurrobin.smpstats.moments;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MomentConfigParser {
    public List<MomentDefinition> parse(ConfigurationSection section) {
        List<MomentDefinition> definitions = new ArrayList<>();
        if (section == null) {
            return defaults();
        }
        ConfigurationSection defs = section.getConfigurationSection("definitions");
        if (defs == null) {
            return defaults();
        }
        for (String key : defs.getKeys(false)) {
            ConfigurationSection def = defs.getConfigurationSection(key);
            if (def == null) {
                continue;
            }
            MomentDefinition.TriggerType trigger = parseTrigger(def.getString("type"));
            String title = def.getString("title", key);
            String detail = def.getString("detail", "");
            long merge = def.getLong("merge_seconds", 0);
            boolean firstOnly = def.getBoolean("first_only", false);
            Set<Material> materials = parseMaterials(def.getStringList("materials"));
            double minFall = def.getDouble("min_fall_distance", 0);
            double maxHealth = def.getDouble("max_health_after_damage", 0);
            Set<String> causes = parseCauses(def.getStringList("causes"));
            boolean requireSelf = def.getBoolean("require_self", false);
            Set<String> entityTypes = parseTypes(def.getStringList("entity_types"));
            definitions.add(new MomentDefinition(key, trigger, title, detail, merge, firstOnly, materials, minFall, maxHealth, causes, requireSelf, entityTypes));
        }
        return definitions.isEmpty() ? defaults() : definitions;
    }

    private MomentDefinition.TriggerType parseTrigger(String raw) {
        if (raw == null) {
            return MomentDefinition.TriggerType.BLOCK_BREAK;
        }
        try {
            return MomentDefinition.TriggerType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MomentDefinition.TriggerType.BLOCK_BREAK;
        }
    }

    private Set<Material> parseMaterials(List<String> names) {
        Set<Material> set = new HashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
        return set;
    }

    private List<MomentDefinition> defaults() {
        List<MomentDefinition> defs = new ArrayList<>();
        defs.add(new MomentDefinition(
                "diamond_run",
                MomentDefinition.TriggerType.BLOCK_BREAK,
                "Diamanten Run",
                "Diamanten gefunden: {count}",
                30,
                false,
                Set.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE),
                0,
                0,
                Set.of(),
                false,
                Set.of()
        ));
        defs.add(new MomentDefinition(
                "first_death",
                MomentDefinition.TriggerType.FIRST_DEATH,
                "Erster Tod",
                "{player} ist das erste Mal gestorben.",
                0,
                true,
                Set.of(),
                0,
                0,
                Set.of(),
                false,
                Set.of()
        ));
        defs.add(new MomentDefinition(
                "big_fall_death",
                MomentDefinition.TriggerType.DEATH_FALL,
                "Big Fall Death",
                "{player} fiel aus {fall} Blöcken.",
                0,
                false,
                Set.of(),
                50,
                0,
                Set.of("FALL"),
                false,
                Set.of()
        ));
        defs.add(new MomentDefinition(
                "clutch_low_hp",
                MomentDefinition.TriggerType.DAMAGE_LOW_HP,
                "Clutch",
                "{player} überlebt mit {health} HP.",
                0,
                false,
                Set.of(),
                0,
                1.0,
                Set.of(),
                false,
                Set.of()
        ));
        defs.add(new MomentDefinition(
                "tnt_self",
                MomentDefinition.TriggerType.DEATH_EXPLOSION,
                "Self TNT",
                "{player} hat sich selbst in die Luft gesprengt.",
                0,
                false,
                Set.of(),
                0,
                0,
                Set.of("BLOCK_EXPLOSION", "ENTITY_EXPLOSION"),
                true,
                Set.of()
        ));
        defs.add(new MomentDefinition(
                "wither_kill",
                MomentDefinition.TriggerType.BOSS_KILL,
                "Wither besiegt",
                "{player} hat den Wither gelegt.",
                0,
                false,
                Set.of(),
                0,
                0,
                Set.of(),
                false,
                Set.of("WITHER")
        ));
        defs.add(new MomentDefinition(
                "mlg_water",
                MomentDefinition.TriggerType.BOSS_KILL,
                "MLG!",
                "{player} hat ein MLG geschafft.",
                0,
                false,
                Set.of(),
                0,
                0,
                Set.of(),
                false,
                Set.of("MLG_WATER")
        ));
        defs.add(new MomentDefinition(
                "netherite_gain",
                MomentDefinition.TriggerType.ITEM_GAIN,
                "Netherite!",
                "{player} hat Netherite gefunden.",
                10,
                false,
                Set.of(Material.NETHERITE_INGOT),
                0,
                0,
                Set.of(),
                false,
                Set.of()
        ));
        return defs;
    }

    private Set<String> parseCauses(List<String> causeStrings) {
        Set<String> set = new HashSet<>();
        for (String c : causeStrings) {
            if (c != null && !c.isBlank()) {
                set.add(c.toUpperCase(Locale.ROOT));
            }
        }
        return set;
    }

    private Set<String> parseTypes(List<String> types) {
        Set<String> set = new HashSet<>();
        for (String t : types) {
            if (t != null && !t.isBlank()) {
                set.add(t.toUpperCase(Locale.ROOT));
            }
        }
        return set;
    }
}
