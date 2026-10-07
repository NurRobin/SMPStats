package de.nurrobin.smpstats.throwback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The pool of trophy categories. Wording and comparisons live in {@code throwback/texts.yml}, not here. */
public final class TrophyCatalog {
    private static final String CUSTOM = "minecraft:custom/minecraft:";
    private static final String MINED = "minecraft:mined/";
    private static final String KILLED_BY = "minecraft:killed_by/";

    private final List<TrophyDef> defs;

    public TrophyCatalog(List<TrophyDef> defs) {
        this.defs = List.copyOf(defs);
    }

    public static TrophyCatalog standard() {
        List<TrophyDef> d = new ArrayList<>();
        d.add(def("playtime", TrophyDef.Kind.HIGHEST, Unit.HOURS, SceneType.TIME, true,
                p -> Value.of(p.player().playtimeMs())));
        d.add(def("blocks_broken", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.MINE, true,
                p -> Value.of(Math.max(p.stat("smpstats:blocks_broken"), p.sumPrefix(MINED)))));
        d.add(def("distance_foot", TrophyDef.Kind.HIGHEST, Unit.KM, SceneType.WALK, true,
                p -> Value.of(p.sum(CUSTOM + "walk_one_cm", CUSTOM + "sprint_one_cm", CUSTOM + "crouch_one_cm",
                        CUSTOM + "swim_one_cm", CUSTOM + "walk_under_water_one_cm", CUSTOM + "walk_on_water_one_cm"))));
        d.add(def("elytra", TrophyDef.Kind.HIGHEST, Unit.KM, SceneType.ELYTRA, false,
                p -> Value.of(p.stat(CUSTOM + "aviate_one_cm"))));
        d.add(def("happy_ghast", TrophyDef.Kind.HIGHEST, Unit.KM, SceneType.GHAST, false,
                p -> Value.of(p.stat(CUSTOM + "happy_ghast_one_cm"))));
        d.add(def("rides", TrophyDef.Kind.HIGHEST, Unit.KM, SceneType.RIDE, false,
                p -> Value.of(p.sum(CUSTOM + "boat_one_cm", CUSTOM + "horse_one_cm", CUSTOM + "pig_one_cm",
                        CUSTOM + "strider_one_cm", CUSTOM + "minecart_one_cm"))));
        d.add(def("mob_kills", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.FIGHT, false,
                p -> Value.of(Math.max(p.stat(CUSTOM + "mob_kills"), p.stat("smpstats:mob_kills")))));
        d.add(def("deaths", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.DEATH, false,
                p -> Value.of(Math.max(p.stat(CUSTOM + "deaths"), p.stat("smpstats:deaths")))));
        d.add(def("nemesis", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.NEMESIS, false, p -> {
            Map.Entry<String, Double> top = p.top(KILLED_BY);
            return top == null ? Value.of(0) : new Value(top.getValue(), translation("entity", top.getKey(), KILLED_BY));
        }));
        d.add(def("trades", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.TRADE, false,
                p -> Value.of(p.stat(CUSTOM + "traded_with_villager"))));
        d.add(def("fish", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.FISH, false,
                p -> Value.of(p.stat(CUSTOM + "fish_caught"))));
        d.add(def("fav_block", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.BLOCK, false, p -> {
            Map.Entry<String, Double> top = p.top(MINED);
            return top == null ? Value.of(0) : new Value(top.getValue(), translation("block", top.getKey(), MINED));
        }));
        d.add(def("nether", TrophyDef.Kind.EARLIEST, Unit.DATE, SceneType.PORTAL, false,
                p -> Value.of(p.advancements().getOrDefault("minecraft:story/enter_the_nether", 0L))));
        d.add(def("end", TrophyDef.Kind.EARLIEST, Unit.DATE, SceneType.PORTAL, false,
                p -> Value.of(p.advancements().getOrDefault("minecraft:story/enter_the_end", 0L))));
        d.add(def("biomes", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.BIOME, false,
                p -> Value.of(p.stat("smpstats:biomes_count"))));
        d.add(def("cake", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.CAKE, false,
                p -> Value.of(p.stat(CUSTOM + "eat_cake_slice"))));
        d.add(def("bell", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.BELL, false,
                p -> Value.of(p.stat(CUSTOM + "bell_ring"))));
        d.add(def("raids", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.RAID, false,
                p -> Value.of(p.stat(CUSTOM + "raid_win"))));
        d.add(def("sleep", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.SLEEP, false,
                p -> Value.of(p.stat(CUSTOM + "sleep_in_bed"))));
        d.add(def("jumps", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.JUMP, false,
                p -> Value.of(p.stat(CUSTOM + "jump"))));
        return new TrophyCatalog(d);
    }

    private static TrophyDef def(String id, TrophyDef.Kind kind, Unit unit, SceneType scene, boolean always,
                                 java.util.function.Function<PlayerData, Value> fn) {
        return new TrophyDef(id, kind, unit, scene, always, fn);
    }

    /** {@code minecraft:mined/minecraft:stone} becomes {@code block.minecraft.stone}. */
    static String translation(String type, String statKey, String prefix) {
        String id = statKey.substring(prefix.length());
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return type + "." + namespace + "." + path.replace('/', '.');
    }

    public List<TrophyDef> defs() {
        return defs;
    }

    public int indexOf(TrophyDef def) {
        return defs.indexOf(def);
    }

    /** Exact stat keys the value functions of the standard catalog read. */
    public static List<String> exactKeys() {
        List<String> keys = new ArrayList<>(List.of("smpstats:blocks_broken", "smpstats:mob_kills", "smpstats:deaths",
                "smpstats:biomes_count"));
        for (String c : List.of("walk_one_cm", "sprint_one_cm", "crouch_one_cm", "swim_one_cm", "walk_under_water_one_cm",
                "walk_on_water_one_cm", "aviate_one_cm", "happy_ghast_one_cm", "boat_one_cm", "horse_one_cm", "pig_one_cm",
                "strider_one_cm", "minecart_one_cm", "mob_kills", "deaths", "traded_with_villager", "fish_caught",
                "eat_cake_slice", "bell_ring", "raid_win", "sleep_in_bed", "jump")) {
            keys.add(CUSTOM + c);
        }
        return keys;
    }

    public static List<String> prefixes() {
        return List.of(MINED, KILLED_BY);
    }

    public static List<String> advancementKeys() {
        return List.of("minecraft:story/enter_the_nether", "minecraft:story/enter_the_end");
    }
}
