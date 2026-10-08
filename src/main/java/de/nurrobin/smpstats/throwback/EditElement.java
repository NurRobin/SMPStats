package de.nurrobin.smpstats.throwback;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * Something the builder sees as one box group: a stage, the rules board, the portal. {@code clearance} boxes are
 * the space that must stay free (green, red when {@link #blocked()}); {@code footprint} boxes only show where
 * something stands. The blocked flag is computed on change and read by the particle tick, which never touches blocks.
 */
final class EditElement {
    /** Space of a stage: floor 3 deep and 4 wide, plus 2 free blocks to the sides and the back, 5 high. */
    static final double STAGE_FLOOR_FRONT = -2.5;
    static final double STAGE_FLOOR_BACK = 0.5;
    static final double STAGE_FLOOR_HALF_WIDTH = 2.0;
    static final double STAGE_MARGIN = 2.0;
    static final double STAGE_HEIGHT = 5.0;
    static final double START_HEIGHT = 2.0;

    /** Heights of the two boards, from their real text. */
    record PanelSizes(double rules, double privacy) {
    }

    private final String type;
    private final String detail;
    private final List<EditMark> marks;
    private final List<OrientedBox> clearance;
    private final List<OrientedBox> footprint;
    private boolean blocked;

    EditElement(String type, String detail, List<EditMark> marks, List<OrientedBox> clearance, List<OrientedBox> footprint) {
        this.type = type;
        this.detail = detail;
        this.marks = List.copyOf(marks);
        this.clearance = List.copyOf(clearance);
        this.footprint = List.copyOf(footprint);
    }

    /** One of start, stage, rules, privacy, portal, portal_corner, wall. */
    String type() {
        return type;
    }

    /** Stage number or wall name; empty for the rest. */
    String detail() {
        return detail;
    }

    List<EditMark> marks() {
        return marks;
    }

    List<OrientedBox> clearance() {
        return clearance;
    }

    List<OrientedBox> footprint() {
        return footprint;
    }

    boolean blocked() {
        return blocked;
    }

    void blocked(boolean blocked) {
        this.blocked = blocked;
    }

    /** Looks at every block the clearance boxes cover; solid ones make the element blocked. */
    void recompute(BlockProbe probe) {
        for (OrientedBox box : clearance) {
            for (int[] b : box.blocks()) {
                if (probe.solid(b[0], b[1], b[2])) {
                    blocked = true;
                    return;
                }
            }
        }
        blocked = false;
    }

    boolean near(int x, int y, int z) {
        for (OrientedBox box : clearance) {
            if (box.near(x, y, z, 1.0)) {
                return true;
            }
        }
        return false;
    }

    /** A point in the middle of the element, for the distance cut-off of the particles. */
    OrientedBox.Pt center() {
        OrientedBox first = !clearance.isEmpty() ? clearance.get(0) : footprint.get(0);
        return first.center();
    }

    /** All elements of a draft; call {@link #recompute} on them to fill in the blocked flags. */
    static List<EditElement> of(EditDraft draft, PanelSizes sizes) {
        List<EditElement> out = new ArrayList<>();
        draft.single(EditKind.START).ifPresent(m -> out.add(start(m)));
        List<EditMark> stages = draft.stages();
        for (int i = 0; i < stages.size(); i++) {
            out.add(stage(stages.get(i), i + 1));
        }
        boolean privacySet = draft.single(EditKind.PRIVACY).isPresent();
        draft.single(EditKind.RULES).ifPresent(m -> out.add(rules(m, sizes, !privacySet)));
        draft.single(EditKind.PRIVACY).ifPresent(m -> out.add(privacy(m, sizes)));
        EditMark p1 = draft.single(EditKind.PORTAL_1).orElse(null);
        EditMark p2 = draft.single(EditKind.PORTAL_2).orElse(null);
        if (p1 != null && p2 != null) {
            Region r = Region.of(p1.blockX(), p1.blockY(), p1.blockZ(), p2.blockX(), p2.blockY(), p2.blockZ());
            out.add(new EditElement("portal", "", List.of(p1, p2), List.of(OrientedBox.aabb(r.minX(), r.minY(), r.minZ(),
                    r.maxX() + 1, r.maxY() + 1, r.maxZ() + 1)), List.of()));
        } else {
            for (EditMark m : new EditMark[] {p1, p2}) {
                if (m != null) {
                    out.add(new EditElement("portal_corner", m.kind() == EditKind.PORTAL_1 ? "1" : "2", List.of(m), List.of(OrientedBox.aabb(m.blockX(), m.blockY(),
                            m.blockZ(), m.blockX() + 1, m.blockY() + 1, m.blockZ() + 1)), List.of()));
                }
            }
        }
        for (EditMark m : draft.walls()) {
            out.add(wall(m));
        }
        return out;
    }

    private static EditElement start(EditMark m) {
        return new EditElement("start", "", List.of(m),
                List.of(OrientedBox.of(m.x(), m.z(), m.yaw(), -0.5, 0.5, -0.5, 0.5, m.y(), m.y() + START_HEIGHT)), List.of());
    }

    private static EditElement stage(EditMark m, int number) {
        float yaw = m.yaw();
        double back = STAGE_FLOOR_BACK + STAGE_MARGIN;
        double side = STAGE_FLOOR_HALF_WIDTH + STAGE_MARGIN;
        return new EditElement("stage", String.valueOf(number), List.of(m),
                List.of(OrientedBox.of(m.x(), m.z(), yaw, STAGE_FLOOR_FRONT, back, -side, side, m.y(), m.y() + STAGE_HEIGHT)),
                List.of(OrientedBox.of(m.x(), m.z(), yaw, STAGE_FLOOR_FRONT, STAGE_FLOOR_BACK, -STAGE_FLOOR_HALF_WIDTH,
                        STAGE_FLOOR_HALF_WIDTH, m.y() - 1, m.y())));
    }

    private static OrientedBox board(Location bottomMiddle, float yaw, double height) {
        double half = PanelMetrics.THICKNESS / 2;
        return OrientedBox.of(bottomMiddle.getX(), bottomMiddle.getZ(), yaw, -half, half, -PanelMetrics.width() / 2,
                PanelMetrics.width() / 2, bottomMiddle.getY(), bottomMiddle.getY() + height);
    }

    private static EditElement rules(EditMark m, PanelSizes sizes, boolean withFallbackPrivacy) {
        List<OrientedBox> boxes = new ArrayList<>();
        boxes.add(board(PanelMetrics.rulesBoard(null, m.pos()), m.yaw(), sizes.rules()));
        if (withFallbackPrivacy) {
            boxes.add(board(PanelMetrics.privacyBoard(null, m.pos(), null), m.yaw(), sizes.privacy()));
        }
        return new EditElement("rules", "", List.of(m), boxes, List.of());
    }

    private static EditElement privacy(EditMark m, PanelSizes sizes) {
        return new EditElement("privacy", "", List.of(m),
                List.of(board(PanelMetrics.privacyBoard(null, m.pos(), m.pos()), m.yaw(), sizes.privacy())), List.of());
    }

    private static EditElement wall(EditMark m) {
        WallDef w = m.wall();
        BlockFace right = w.right();
        int x2 = w.x() + right.getModX() * (w.width() - 1);
        int z2 = w.z() + right.getModZ() * (w.width() - 1);
        OrientedBox frames = OrientedBox.aabb(Math.min(w.x(), x2), w.y(), Math.min(w.z(), z2),
                Math.max(w.x(), x2) + 1, w.y() + w.height(), Math.max(w.z(), z2) + 1);
        return new EditElement("wall", w.name(), List.of(m), List.of(frames), List.of());
    }
}
