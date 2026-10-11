package de.nurrobin.smpstats.throwback;

/**
 * One marker of an edit draft. {@code x/y/z} is the spot on top of the clicked block (for stages the figure's
 * spot, see {@link EditGeometry}); boards and texts use the bottom middle of what they show. {@code yaw} is already
 * snapped. Portal corners use the block they name as {@code floor(x), floor(y), floor(z)}; {@code text} is set
 * for {@link EditKind#TEXT} only.
 */
record EditMark(EditKind kind, double x, double y, double z, float yaw, String text) {

    static EditMark of(EditKind kind, double x, double y, double z, float yaw) {
        return new EditMark(kind, x, y, z, yaw, null);
    }

    static EditMark ofText(HallText text) {
        Pos p = text.pos();
        return new EditMark(EditKind.TEXT, p.x(), p.y(), p.z(), p.yaw(), text.text());
    }

    HallText hallText() {
        return new HallText(pos(), text);
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
