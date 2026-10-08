package de.nurrobin.smpstats.throwback;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Lantern;

import java.util.ArrayList;
import java.util.List;

/**
 * A plain stand-in corridor so the hall can be tested before the builders deliver theirs: start platform,
 * a corridor with six alcoves per side, three picture walls, a rules panel and a portal frame at the end.
 * Each alcove holds a stage floor of {@link #STAGE_DEPTH} by {@link #STAGE_WIDTH} with {@link #MARGIN} free
 * blocks to the sides and the back, because the hologram above a stage is up to 6.5 blocks wide.
 * Everything is derived from the constants below, and the matching {@link HallLayout} is returned.
 */
public final class PlaceholderHall {
    static final int FLOOR_Y = 63;
    static final int Y0 = FLOOR_Y + 1;
    static final int HEIGHT = 5;
    static final int STAGE_DEPTH = 3;
    static final int STAGE_WIDTH = 4;
    static final int MARGIN = 2;
    static final int CORRIDOR_HALF = 2;
    static final int FIRST_ALCOVE_Z = 12;
    static final int ALCOVE_SPACING = STAGE_WIDTH + 2 * MARGIN + 1;
    static final int ALCOVES_PER_SIDE = 6;
    /** First z behind the last alcove; picture walls, rules panel and portal are placed relative to it. */
    static final int AFTER_ALCOVES_Z = FIRST_ALCOVE_Z + ALCOVE_SPACING * (ALCOVES_PER_SIDE - 1) + STAGE_WIDTH / 2 + MARGIN;
    static final int END_Z = AFTER_ALCOVES_Z + 23;
    static final int OUTER_X = CORRIDOR_HALF + STAGE_DEPTH + MARGIN;

    private PlaceholderHall() {
    }

    /** The layout this class builds; also usable without a world, for tests. */
    public static HallLayout layout() {
        List<Pos> stages = new ArrayList<>();
        // The corridor spans blocks -CORRIDOR_HALF..CORRIDOR_HALF, so it is mirrored around x = 0.5, not 0.
        // Both figures stand half a block in front of their alcove's stage back edge.
        double westX = -(CORRIDOR_HALF + STAGE_DEPTH) + 0.5;
        double eastX = 1.0 - westX;
        for (int k = 0; k < ALCOVES_PER_SIDE; k++) {
            double z = FIRST_ALCOVE_Z + ALCOVE_SPACING * k;
            stages.add(new Pos(westX, Y0, z, 90f));
            stages.add(new Pos(eastX, Y0, z, -90f));
        }
        int a = AFTER_ALCOVES_Z;
        List<WallDef> walls = List.of(
                new WallDef("bauten-1", -2, Y0, a + 6, BlockFace.EAST, 4, 3, "Bauten 2025: Bau 1", "Erbauer"),
                new WallDef("bauten-2", -2, Y0, a + 11, BlockFace.EAST, 4, 3, "Bauten 2025: Bau 2", "Erbauer"),
                new WallDef("bauten-3", -2, Y0, a + 16, BlockFace.EAST, 4, 3, "Bauten 2025: Bau 3", "Erbauer"));
        return new HallLayout(new Pos(0.5, Y0, -6.5, 0f), List.copyOf(stages), new Pos(2.4, Y0, a + 7.5, 90f), null,
                Region.of(-1, Y0, END_Z - 3, 1, Y0 + 2, END_Z - 2), walls);
    }

    /** Builds the blocks into the world and returns the layout that belongs to them. */
    public static HallLayout build(World world) {
        fill(world, -OUTER_X - 1, FLOOR_Y, -9, OUTER_X + 1, FLOOR_Y + HEIGHT + 1, END_Z + 2, Material.DEEPSLATE_BRICKS);
        carve(world, -5, Y0, -8, 5, Y0 + HEIGHT - 1, 6);
        carve(world, -CORRIDOR_HALF, Y0, 7, CORRIDOR_HALF, Y0 + HEIGHT - 1, END_Z);
        int stageIn = CORRIDOR_HALF + 1;
        int stageOut = CORRIDOR_HALF + STAGE_DEPTH;
        for (int k = 0; k < ALCOVES_PER_SIDE; k++) {
            int c = FIRST_ALCOVE_Z + ALCOVE_SPACING * k;
            int z1 = c - STAGE_WIDTH / 2;
            int z2 = c + STAGE_WIDTH / 2 - 1;
            carve(world, -OUTER_X, Y0, z1 - MARGIN, -stageIn, Y0 + HEIGHT - 1, z2 + MARGIN);
            carve(world, stageIn, Y0, z1 - MARGIN, OUTER_X, Y0 + HEIGHT - 1, z2 + MARGIN);
            lantern(world, -OUTER_X, Y0 + HEIGHT - 1, c);
            lantern(world, OUTER_X, Y0 + HEIGHT - 1, c);
            fill(world, -stageOut, FLOOR_Y, z1, -stageIn, FLOOR_Y, z2, Material.SPRUCE_PLANKS);
            fill(world, stageIn, FLOOR_Y, z1, stageOut, FLOOR_Y, z2, Material.SPRUCE_PLANKS);
        }
        fill(world, -1, Y0, 7, 1, Y0, END_Z - 4, Material.RED_CARPET);
        fill(world, -1, Y0, -8, 1, Y0, 6, Material.RED_CARPET);
        for (int z = -6; z <= END_Z; z += 6) {
            lantern(world, 0, Y0 + HEIGHT - 1, z);
        }
        fill(world, 3, Y0, AFTER_ALCOVES_Z + 3, 3, Y0 + 3, AFTER_ALCOVES_Z + 11, Material.DARK_OAK_PLANKS);
        portalFrame(world);
        return layout();
    }

    private static void portalFrame(World world) {
        int z = END_Z;
        fill(world, -2, FLOOR_Y, z, 2, FLOOR_Y, z, Material.CRYING_OBSIDIAN);
        fill(world, -2, Y0 + 3, z, 2, Y0 + 3, z, Material.CRYING_OBSIDIAN);
        fill(world, -2, Y0, z, -2, Y0 + 2, z, Material.CRYING_OBSIDIAN);
        fill(world, 2, Y0, z, 2, Y0 + 2, z, Material.CRYING_OBSIDIAN);
        fill(world, -1, Y0, z, 1, Y0 + 2, z, Material.PURPLE_STAINED_GLASS);
    }

    private static void lantern(World world, int x, int y, int z) {
        var block = world.getBlockAt(x, y, z);
        Lantern data = (Lantern) Material.LANTERN.createBlockData();
        data.setHanging(true);
        block.setBlockData(data, false);
    }

    private static void fill(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private static void carve(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(world, x1, y1, z1, x2, y2, z2, Material.AIR);
    }
}
