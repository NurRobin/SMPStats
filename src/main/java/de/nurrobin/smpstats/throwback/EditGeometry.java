package de.nurrobin.smpstats.throwback;

import org.bukkit.block.BlockFace;

/**
 * Turns a click on a block and the builder's look direction into a marker. Facing conventions (yaw 0 looks
 * south, 90 west, 180 north, -90 east):
 * <ul>
 * <li>start and stage: the builder's own look direction (a stage looks at its back wall)</li>
 * <li>rules and privacy board: look direction + 180, the board faces the reader</li>
 * <li>picture wall: the frames face against the look direction</li>
 * </ul>
 */
final class EditGeometry {
    private EditGeometry() {
    }

    /** Rounds to the nearest of 0, 90, 180 and -90. */
    static float snapYaw(float yaw) {
        return normalize(Math.round(yaw / 90f) * 90f);
    }

    /** Maps any angle to one of the values -90, 0, 90, 180 for multiples of 90, else into (-180, 180]. */
    static float normalize(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        return y > 180f ? y - 360f : y;
    }

    static float towardViewerYaw(float lookYaw) {
        return normalize(snapYaw(lookYaw) + 180f);
    }

    /** The horizontal direction a snapped yaw looks to. */
    static BlockFace faceOf(float yaw) {
        return switch ((int) snapYaw(yaw)) {
            case 90 -> BlockFace.WEST;
            case 180 -> BlockFace.NORTH;
            case -90 -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
    }

    /**
     * The marker for a right click on block {@code (bx, by, bz)}. {@code hitX/hitZ} is the exact click point
     * (null if unknown); it only matters for stages, which stand on a block edge across their width.
     */
    static EditMark mark(EditKind kind, int bx, int by, int bz, Double hitX, Double hitZ, float lookYaw) {
        double x = bx + 0.5;
        double y = by + 1.0;
        double z = bz + 0.5;
        switch (kind) {
            case START:
                return EditMark.of(kind, x, y, z, snapYaw(lookYaw));
            case STAGE:
                return stage(bx, by, bz, hitX, hitZ, lookYaw);
            case RULES:
            case PRIVACY:
                return EditMark.of(kind, x, y, z, towardViewerYaw(lookYaw));
            case PORTAL_1:
            case PORTAL_2:
                return EditMark.of(kind, x, y, z, 0f);
            default:
                throw new IllegalArgumentException("Bilderwand: EditGeometry.wall nutzen");
        }
    }

    /**
     * A stage stands on the middle of the back row of its floor: centred on that block along the facing, and on
     * the block edge nearest the click across the width, because the 4 block wide floor has no middle block.
     */
    private static EditMark stage(int bx, int by, int bz, Double hitX, Double hitZ, float lookYaw) {
        float yaw = snapYaw(lookYaw);
        BlockFace face = faceOf(yaw);
        double x = bx + 0.5;
        double z = bz + 0.5;
        if (face == BlockFace.EAST || face == BlockFace.WEST) {
            z = edge(bz, hitZ);
        } else {
            x = edge(bx, hitX);
        }
        return EditMark.of(EditKind.STAGE, x, by + 1.0, z, yaw);
    }

    private static double edge(int block, Double hit) {
        if (hit == null) {
            return block;
        }
        return Math.max(block, Math.min(block + 1, Math.round(hit)));
    }

    /** A picture wall whose lower-left front block sits on top of the clicked block, facing the builder. */
    static WallDef wall(int bx, int by, int bz, float lookYaw, String name, int width, int height, String title,
                        String builders) {
        return new WallDef(name, bx, by + 1, bz, faceOf(lookYaw).getOppositeFace(), width, height, title, builders);
    }
}
