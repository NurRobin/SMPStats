package de.nurrobin.smpstats.throwback;

import java.util.ArrayList;
import java.util.List;

/**
 * A box standing on the ground plane, turned by a yaw. {@code s} runs along the facing, {@code t} to the right
 * of it (the same convention as the scene code: forward is {@code (-sin, cos)}, right is {@code (-cos, -sin)}),
 * both measured from the origin; {@code y0..y1} are absolute heights.
 */
record OrientedBox(double ox, double oz, double fx, double fz, double s0, double s1, double t0, double t1,
                   double y0, double y1) {
    private static final double EPS = 1e-6;

    record Pt(double x, double y, double z) {
    }

    /** A box in the frame of {@code yaw}; the unit vectors are cleaned so quarter turns stay exact. */
    static OrientedBox of(double ox, double oz, float yaw, double s0, double s1, double t0, double t1,
                          double y0, double y1) {
        double rad = Math.toRadians(yaw);
        return new OrientedBox(ox, oz, clean(-Math.sin(rad)), clean(Math.cos(rad)), s0, s1, t0, t1, y0, y1);
    }

    /** An axis-aligned box over whole coordinates. */
    static OrientedBox aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new OrientedBox((minX + maxX) / 2, (minZ + maxZ) / 2, 0, 1, -(maxZ - minZ) / 2, (maxZ - minZ) / 2,
                -(maxX - minX) / 2, (maxX - minX) / 2, minY, maxY);
    }

    private static double clean(double v) {
        if (Math.abs(v) < 1e-9) {
            return 0;
        }
        return Math.abs(Math.abs(v) - 1) < 1e-9 ? Math.signum(v) : v;
    }

    double rx() {
        return -fz;
    }

    double rz() {
        return fx;
    }

    Pt corner(double s, double t, double y) {
        return new Pt(ox + fx * s + rx() * t, y, oz + fz * s + rz() * t);
    }

    /** Horizontal bounds of the turned box: {minX, maxX, minZ, maxZ}. */
    double[] hull() {
        double[] h = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double s : new double[] {s0, s1}) {
            for (double t : new double[] {t0, t1}) {
                Pt c = corner(s, t, 0);
                h[0] = Math.min(h[0], c.x());
                h[1] = Math.max(h[1], c.x());
                h[2] = Math.min(h[2], c.z());
                h[3] = Math.max(h[3], c.z());
            }
        }
        return h;
    }

    Pt center() {
        return corner((s0 + s1) / 2, (t0 + t1) / 2, (y0 + y1) / 2);
    }

    /** True if the block's cube overlaps the box by more than a hair; boxes that only touch a block do not count. */
    boolean intersectsBlock(int bx, int by, int bz) {
        if (by + 1 <= y0 + EPS || by >= y1 - EPS) {
            return false;
        }
        double cx = bx + 0.5 - ox;
        double cz = bz + 0.5 - oz;
        double half = 0.5 * (Math.abs(fx) + Math.abs(fz));
        double sc = cx * fx + cz * fz;
        double tc = cx * rx() + cz * rz();
        if (sc + half <= s0 + EPS || sc - half >= s1 - EPS || tc + half <= t0 + EPS || tc - half >= t1 - EPS) {
            return false;
        }
        double[] h = hull();
        return bx + 1 > h[0] + EPS && bx < h[1] - EPS && bz + 1 > h[2] + EPS && bz < h[3] - EPS;
    }

    /** All blocks the box overlaps, as {x, y, z}. */
    List<int[]> blocks() {
        double[] h = hull();
        List<int[]> out = new ArrayList<>();
        for (int x = (int) Math.floor(h[0]); x <= (int) Math.floor(h[1]); x++) {
            for (int z = (int) Math.floor(h[2]); z <= (int) Math.floor(h[3]); z++) {
                for (int y = (int) Math.floor(y0); y < (int) Math.ceil(y1); y++) {
                    if (intersectsBlock(x, y, z)) {
                        out.add(new int[] {x, y, z});
                    }
                }
            }
        }
        return out;
    }

    /** True if the box reaches within {@code margin} blocks of the given block (for change notices). */
    boolean near(int bx, int by, int bz, double margin) {
        double[] h = hull();
        return bx + 1 >= h[0] - margin && bx <= h[1] + margin && bz + 1 >= h[2] - margin && bz <= h[3] + margin
                && by + 1 >= y0 - margin && by <= y1 + margin;
    }

    /** True if the point lies in the box grown by {@code margin} on every side. */
    boolean contains(double x, double y, double z, double margin) {
        double[] h = hull();
        return x >= h[0] - margin && x <= h[1] + margin && z >= h[2] - margin && z <= h[3] + margin
                && y >= y0 - margin && y <= y1 + margin;
    }

    /** The 12 edges as pairs of corners. */
    List<Pt[]> edges() {
        double[] ss = {s0, s1};
        double[] ts = {t0, t1};
        double[] ys = {y0, y1};
        List<Pt[]> out = new ArrayList<>();
        for (double t : ts) {
            for (double y : ys) {
                out.add(new Pt[] {corner(s0, t, y), corner(s1, t, y)});
            }
        }
        for (double s : ss) {
            for (double y : ys) {
                out.add(new Pt[] {corner(s, t0, y), corner(s, t1, y)});
            }
        }
        for (double s : ss) {
            for (double t : ts) {
                out.add(new Pt[] {corner(s, t, y0), corner(s, t, y1)});
            }
        }
        return out;
    }
}
