package de.nurrobin.smpstats.throwback;

import org.bukkit.block.BlockFace;

/**
 * Turns a click on a block and the builder's look direction into a marker. Facing conventions (yaw 0 looks
 * south, 90 west, 180 north, -90 east):
 * <ul>
 * <li>start and stage: the builder's own look direction (a stage looks at its back wall)</li>
 * <li>rules board: look direction + 180, the board faces the reader</li>
 * <li>privacy board: away from a clicked wall, else look direction + 180</li>
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
                return EditMark.of(kind, x, y, z, towardViewerYaw(lookYaw));
            default:
                throw new IllegalArgumentException(kind + ": eigene Methode in EditGeometry nutzen");
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

    /**
     * A portal corner names the block a player's feet pass through, because the portal fires on the feet. That is
     * the clicked block itself if one can walk through it (portal filling, carpet, air-like blocks), else the block
     * on the clicked side, the same block a placed block would go to.
     */
    static EditMark portalCorner(EditKind kind, int bx, int by, int bz, boolean passable, BlockFace face) {
        int x = bx;
        int y = by;
        int z = bz;
        if (!passable && face != null) {
            x += face.getModX();
            y += face.getModY();
            z += face.getModZ();
        }
        return EditMark.of(kind, x + 0.5, y, z + 0.5, 0f);
    }

    /**
     * The privacy board, as the bottom middle of the board. Clicked on a wall it hangs flat against that wall with
     * its middle at the clicked block; clicked on a floor it stands on it, on a ceiling it hangs from it.
     */
    static EditMark privacy(int bx, int by, int bz, BlockFace face, float lookYaw, double height) {
        if (face == null || face == BlockFace.UP) {
            return EditMark.of(EditKind.PRIVACY, bx + 0.5, by + 1.0, bz + 0.5, towardViewerYaw(lookYaw));
        }
        if (face == BlockFace.DOWN) {
            return EditMark.of(EditKind.PRIVACY, bx + 0.5, by - height, bz + 0.5, towardViewerYaw(lookYaw));
        }
        double inset = 0.5 + PanelMetrics.THICKNESS / 2;
        return EditMark.of(EditKind.PRIVACY, bx + 0.5 + face.getModX() * inset, by + 0.5 - height / 2,
                bz + 0.5 + face.getModZ() * inset, yawOf(face));
    }

    /** The yaw that looks towards {@code face}. */
    static float yawOf(BlockFace face) {
        return switch (face) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    /** A picture wall whose lower-left front block sits on top of the clicked block, facing the builder. */
    static WallDef wall(int bx, int by, int bz, float lookYaw, String name, int width, int height, String title,
                        String builders) {
        return new WallDef(name, bx, by + 1, bz, faceOf(lookYaw).getOppositeFace(), width, height, title, builders);
    }
}
