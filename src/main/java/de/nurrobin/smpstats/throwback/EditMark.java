package de.nurrobin.smpstats.throwback;

/**
 * One marker of an edit draft. {@code x/y/z} is the spot on top of the clicked block (for stages the figure's
 * spot, see {@link EditGeometry}); {@code yaw} is already snapped. Portal corners and walls use the block they
 * name as {@code floor(x), floor(y), floor(z)}; {@code wall} is set for {@link EditKind#WALL} only.
 */
record EditMark(EditKind kind, double x, double y, double z, float yaw, WallDef wall) {

    static EditMark of(EditKind kind, double x, double y, double z, float yaw) {
        return new EditMark(kind, x, y, z, yaw, null);
    }

    static EditMark ofWall(WallDef wall) {
        return new EditMark(EditKind.WALL, wall.x() + 0.5, wall.y(), wall.z() + 0.5, 0f, wall);
    }

    int blockX() {
        return (int) Math.floor(x);
    }

    int blockY() {
        return (int) Math.floor(y);
    }

    int blockZ() {
        return (int) Math.floor(z);
    }

    Pos pos() {
        return new Pos(x, y, z, yaw);
    }

    double distanceSquared(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
