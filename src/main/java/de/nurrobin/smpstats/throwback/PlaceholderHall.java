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
 * Everything is derived from the constants below, and the matching {@link HallLayout} is returned.
 */
public final class PlaceholderHall {
    static final int FLOOR_Y = 63;
    static final int Y0 = FLOOR_Y + 1;
    static final int HEIGHT = 5;
    static final int FIRST_ALCOVE_Z = 12;
    static final int ALCOVE_SPACING = 4;
    static final int ALCOVES_PER_SIDE = 6;
    static final int END_Z = 56;

    private PlaceholderHall() {
    }

    /** The layout this class builds; also usable without a world, for tests. */
    public static HallLayout layout() {
        List<Pos> stages = new ArrayList<>();
        for (int k = 0; k < ALCOVES_PER_SIDE; k++) {
            double z = FIRST_ALCOVE_Z + ALCOVE_SPACING * k + 0.5;
            stages.add(new Pos(-4.5, Y0, z, 90f));
            stages.add(new Pos(4.5, Y0, z, -90f));
        }
        List<WallDef> walls = List.of(
                new WallDef("bauten-1", -2, Y0, 39, BlockFace.EAST, 4, 3, "Bauten 2025: Bau 1", "Erbauer"),
                new WallDef("bauten-2", -2, Y0, 44, BlockFace.EAST, 4, 3, "Bauten 2025: Bau 2", "Erbauer"),
                new WallDef("bauten-3", -2, Y0, 49, BlockFace.EAST, 4, 3, "Bauten 2025: Bau 3", "Erbauer"));
        return new HallLayout(new Pos(0.5, Y0, -6.5, 0f), List.copyOf(stages), new Pos(2.4, Y0, 40.5, 90f),
                Region.of(-1, Y0, END_Z - 3, 1, Y0 + 2, END_Z - 2), walls);
    }

    /** Builds the blocks into the world and returns the layout that belongs to them. */
    public static HallLayout build(World world) {
        fill(world, -7, FLOOR_Y, -9, 7, FLOOR_Y + HEIGHT + 1, END_Z + 2, Material.DEEPSLATE_BRICKS);
        carve(world, -5, Y0, -8, 5, Y0 + HEIGHT - 1, 6);
        carve(world, -2, Y0, 7, 2, Y0 + HEIGHT - 1, END_Z);
        for (int k = 0; k < ALCOVES_PER_SIDE; k++) {
            int c = FIRST_ALCOVE_Z + ALCOVE_SPACING * k;
            carve(world, -6, Y0, c - 1, -3, Y0 + HEIGHT - 2, c + 1);
            carve(world, 3, Y0, c - 1, 6, Y0 + HEIGHT - 2, c + 1);
            lantern(world, -5, Y0 + HEIGHT - 2, c);
            lantern(world, 5, Y0 + HEIGHT - 2, c);
            fill(world, -6, FLOOR_Y, c - 1, -4, FLOOR_Y, c + 1, Material.SPRUCE_PLANKS);
            fill(world, 4, FLOOR_Y, c - 1, 6, FLOOR_Y, c + 1, Material.SPRUCE_PLANKS);
        }
        fill(world, -1, Y0, 7, 1, Y0, END_Z - 4, Material.RED_CARPET);
        fill(world, -1, Y0, -8, 1, Y0, 6, Material.RED_CARPET);
        for (int z = -6; z <= END_Z; z += 6) {
            lantern(world, 0, Y0 + HEIGHT - 1, z);
        }
        fill(world, 3, Y0, 36, 3, Y0 + 3, 44, Material.DARK_OAK_PLANKS);
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
