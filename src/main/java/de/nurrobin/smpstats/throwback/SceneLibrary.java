package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.throwback.SceneSpec.Prop;
import org.bukkit.Material;
import org.bukkit.entity.Pose;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage sets for the Mannequin renderer. Mannequins cannot move single limbs, so a scene is a pose, held items
 * and a few block or item displays around the figure; mining and fighting swing the main hand in a loop.
 */
public final class SceneLibrary {
    private SceneLibrary() {
    }

    public static SceneSpec spec(SceneType type) {
        SceneSpec base = base(type);
        return switch (type) {
            case MINE, BLOCK -> turned(base, 0);
            case WALK, ELYTRA, RIDE, FIGHT, NEMESIS, TRADE, FISH, CAKE -> turned(base, 90);
            default -> base;
        };
    }

    private static SceneSpec base(SceneType type) {
        return switch (type) {
            case TIME -> scene(Pose.STANDING, Material.CLOCK, null, null, null, 0, null,
                    Prop.block(Material.OAK_PLANKS, 0.9, 0, 0.4, 0.5f), Prop.item(Material.CLOCK, 0.9, 0.85, 0.4, 0.5f),
                    Prop.block(Material.CAMPFIRE, -0.9, 0, 0.5, 0.6f));
            case MINE -> mine(Material.DIAMOND_PICKAXE, Material.STONE);
            case WALK -> scene(Pose.STANDING, Material.STICK, null, null, null, 0, null,
                    Prop.block(Material.GRASS_BLOCK, 0, -0.01, 0.8, 0.5f), Prop.block(Material.OAK_FENCE, 1.1, 0, -0.4, 0.4f),
                    Prop.block(Material.OAK_SIGN, -1.0, 0.4, 0.2, 0.4f));
            case ELYTRA -> scene(Pose.FALL_FLYING, null, null, null, Material.ELYTRA, 1.0, null,
                    Prop.item(Material.FEATHER, 0.0, 1.2, -1.2, 0.5f), Prop.block(Material.WHITE_WOOL, -1.0, 1.4, -0.4, 0.5f),
                    Prop.block(Material.WHITE_WOOL, 1.0, 0.9, 0.6, 0.6f));
            case GHAST -> scene(Pose.STANDING, Material.LEAD, null, null, null, 1.5, null,
                    Prop.block(Material.WHITE_WOOL, 0, 0.0, 0, 1.5f), Prop.block(Material.WHITE_CONCRETE, 0.45, 0.0, 0.2, 0.3f),
                    Prop.block(Material.WHITE_CONCRETE, -0.45, 0.0, 0.2, 0.3f));
            case RIDE -> scene(Pose.STANDING, Material.SADDLE, null, null, null, 0.5, null,
                    Prop.block(Material.OAK_SLAB, 0, 0.0, 0, 1.0f), Prop.block(Material.OAK_SLAB, 0, 0.0, 0.7, 0.6f),
                    Prop.item(Material.OAK_BOAT, 0.9, 0.4, 0.5, 0.7f));
            case FIGHT -> fight();
            case DEATH -> scene(Pose.STANDING, null, null, Material.SKELETON_SKULL, null, 0, null,
                    Prop.block(Material.COBBLESTONE, -1.0, 0, 0.2, 0.7f), Prop.item(Material.TOTEM_OF_UNDYING, -1.0, 0.9, 0.2, 0.6f),
                    Prop.block(Material.SOUL_CAMPFIRE, 1.0, 0, 0.2, 0.5f));
            case NEMESIS -> scene(Pose.SNEAKING, Material.WOODEN_SWORD, Material.SHIELD, null, null, 0, null,
                    Prop.block(Material.COBWEB, 0.8, 0.2, 0.9, 0.7f), Prop.block(Material.SKELETON_SKULL, 0, 0.05, 1.0, 0.5f));
            case TRADE -> scene(Pose.STANDING, Material.EMERALD, null, null, null, 0, null,
                    Prop.block(Material.LECTERN, 0.9, 0, 0.5, 0.7f), Prop.item(Material.EMERALD, 0, 1.0, 0.7, 0.5f),
                    Prop.block(Material.EMERALD_BLOCK, -0.9, 0, 0.5, 0.5f));
            case FISH -> fish();
            case BLOCK -> scene(Pose.STANDING, Material.IRON_PICKAXE, null, null, null, 0, null,
                    Prop.block(Material.STONE, 0.9, 0, 0.5, 0.8f), Prop.block(Material.COBBLESTONE, 0.9, 0.8, 0.5, 0.6f),
                    Prop.block(Material.DEEPSLATE, -0.9, 0, 0.5, 0.6f));
            case PORTAL -> scene(Pose.STANDING, Material.FLINT_AND_STEEL, null, null, null, 0, null,
                    Prop.block(Material.OBSIDIAN, 0.8, 0, -0.9, 0.6f), Prop.block(Material.OBSIDIAN, -0.8, 0, -0.9, 0.6f),
                    Prop.block(Material.OBSIDIAN, 0.8, 1.2, -0.9, 0.6f), Prop.block(Material.OBSIDIAN, -0.8, 1.2, -0.9, 0.6f),
                    Prop.block(Material.PURPLE_STAINED_GLASS, 0, 0.1, -0.9, 0.9f));
            case BIOME -> scene(Pose.STANDING, Material.FILLED_MAP, null, null, null, 0, null,
                    Prop.block(Material.SAND, 0.9, 0, 0.5, 0.5f), Prop.block(Material.SNOW_BLOCK, 0, 0, 0.9, 0.5f),
                    Prop.block(Material.GRASS_BLOCK, -0.9, 0, 0.5, 0.5f), Prop.block(Material.CHERRY_LEAVES, -0.9, 0.5, 0.5, 0.5f));
            case CAKE -> scene(Pose.STANDING, Material.CAKE, null, null, null, 0, null,
                    Prop.block(Material.OAK_PLANKS, 0, 0, 0.8, 0.9f), Prop.block(Material.CAKE, 0, 0.9, 0.8, 0.6f));
            case BELL -> scene(Pose.STANDING, Material.BELL, null, null, null, 0, null,
                    Prop.block(Material.OAK_FENCE, 0.9, 0, 0.4, 0.5f), Prop.block(Material.OAK_FENCE, 0.9, 0.5, 0.4, 0.5f),
                    Prop.block(Material.BELL, 0.9, 1.0, 0.4, 0.6f));
            case RAID -> scene(Pose.STANDING, Material.IRON_SWORD, null, Material.WHITE_BANNER, null, 0, null,
                    Prop.block(Material.RED_BANNER, 0.9, 0, 0.5, 0.6f), Prop.item(Material.TOTEM_OF_UNDYING, -0.9, 0.9, 0.5, 0.6f));
            case SLEEP -> scene(Pose.STANDING, null, null, Material.WHITE_WOOL, null, 0, null,
                    Prop.block(Material.RED_BED, 0, 0, 0.9, 0.7f), Prop.block(Material.LANTERN, 1.0, 0, 0.3, 0.4f));
            case JUMP -> scene(Pose.STANDING, null, null, null, null, 0.8, null,
                    Prop.block(Material.SLIME_BLOCK, 0, 0.0, 0, 0.8f), Prop.block(Material.SLIME_BLOCK, 0.9, 0.0, 0.2, 0.5f));
            case INFO -> scene(null, null, null, null, null, 0, null,
                    Prop.block(Material.SPRUCE_LOG, 0, 0, 0, 0.25f), Prop.block(Material.SPRUCE_LEAVES, 0, 0.25, 0, 0.9f),
                    Prop.block(Material.SPRUCE_LEAVES, 0, 1.15, 0, 0.6f), Prop.item(Material.NETHER_STAR, 0, 1.95, 0, 0.5f));
        };
    }

    private static SceneSpec mine(Material tool, Material hit) {
        return scene(Pose.STANDING, tool, null, Material.IRON_HELMET, null, 0, hit,
                Prop.block(Material.STONE, 0, 0.0, 1.0, 0.6f), Prop.block(Material.DIAMOND_ORE, 0.6, 0.0, 1.0, 0.6f),
                Prop.block(Material.IRON_ORE, -0.6, 0.0, 1.0, 0.6f), Prop.block(Material.DEEPSLATE_COAL_ORE, 0, 0.6, 1.0, 0.6f),
                Prop.block(Material.STONE, 0.6, 0.6, 1.0, 0.6f), Prop.block(Material.DEEPSLATE, -0.6, 0.6, 1.0, 0.6f),
                Prop.block(Material.GOLD_ORE, 0, 1.2, 1.0, 0.6f), Prop.block(Material.STONE, 0.6, 1.2, 1.0, 0.6f),
                Prop.block(Material.REDSTONE_ORE, -0.6, 1.2, 1.0, 0.6f));
    }

    private static SceneSpec fight() {
        return scene(Pose.STANDING, Material.DIAMOND_SWORD, Material.SHIELD, Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                0, Material.REDSTONE_BLOCK, Prop.block(Material.HAY_BLOCK, 0, 0, 1.1, 0.7f),
                Prop.block(Material.CARVED_PUMPKIN, 0, 0.7, 1.1, 0.5f));
    }

    private static SceneSpec fish() {
        return scene(Pose.STANDING, Material.FISHING_ROD, null, null, null, 0, null,
                Prop.block(Material.WATER, 0, -0.5, 1.2, 1.4f), Prop.item(Material.COD, 0.5, 0.2, 1.0, 0.5f),
                Prop.item(Material.SALMON, -0.5, 0.0, 1.2, 0.5f), Prop.block(Material.OAK_FENCE, 1.0, 0, 0.2, 0.4f));
    }

    private static SceneSpec scene(Pose pose, Material mainHand, Material offHand, Material helmet, Material chest,
                                   double lift, Material swingBlock, Prop... props) {
        List<Prop> list = new ArrayList<>(List.of(props));
        return new SceneSpec(pose, mainHand, offHand, helmet, chest, lift, swingBlock, 180, List.copyOf(list));
    }

    private static SceneSpec turned(SceneSpec spec, double turn) {
        return new SceneSpec(spec.pose(), spec.mainHand(), spec.offHand(), spec.helmet(), spec.chestplate(), spec.lift(),
                spec.swingBlock(), turn, spec.props());
    }
}
