package de.nurrobin.smpstats.throwback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The markers of one edit session, in the order they were placed. Singletons (start, rules, privacy, portal
 * corners) replace their predecessor; stages are numbered by their order, so removing one closes the gap. Texts
 * may be placed any number of times.
 */
final class EditDraft {
    enum Placed { ADDED, REPLACED, STAGE_LIMIT }

    private final List<EditMark> marks = new ArrayList<>();

    EditDraft() {
    }

    EditDraft(List<EditMark> marks) {
        this.marks.addAll(marks);
    }

    /** The draft that matches a saved layout, so editing continues from what the hall uses now. */
    static EditDraft fromLayout(HallLayout layout) {
        EditDraft d = new EditDraft();
        if (layout.start() != null) {
            d.marks.add(fromPos(EditKind.START, layout.start()));
        }
        for (Pos stage : layout.stages()) {
            d.marks.add(fromPos(EditKind.STAGE, stage));
        }
        if (layout.rules() != null) {
            d.marks.add(fromPos(EditKind.RULES, layout.rules()));
        }
        if (layout.privacy() != null) {
            d.marks.add(fromPos(EditKind.PRIVACY, layout.privacy()));
        }
        Region portal = layout.portal();
        if (portal != null) {
            d.marks.add(EditMark.of(EditKind.PORTAL_1, portal.minX() + 0.5, portal.minY(), portal.minZ() + 0.5, 0f));
            d.marks.add(EditMark.of(EditKind.PORTAL_2, portal.maxX() + 0.5, portal.maxY(), portal.maxZ() + 0.5, 0f));
        }
        for (HallText text : layout.texts()) {
            d.marks.add(EditMark.ofText(text));
        }
        return d;
    }

    private static EditMark fromPos(EditKind kind, Pos p) {
        return EditMark.of(kind, p.x(), p.y(), p.z(), p.yaw());
    }

    /** The layout this draft describes; a portal needs both corners. */
    HallLayout toLayout() {
        EditMark portal1 = single(EditKind.PORTAL_1).orElse(null);
        EditMark portal2 = single(EditKind.PORTAL_2).orElse(null);
        Region portal = portal1 == null || portal2 == null ? null
                : Region.of(portal1.blockX(), portal1.blockY(), portal1.blockZ(), portal2.blockX(), portal2.blockY(), portal2.blockZ());
        return new HallLayout(single(EditKind.START).map(EditMark::pos).orElse(null), stages().stream().map(EditMark::pos).toList(),
                single(EditKind.RULES).map(EditMark::pos).orElse(null), single(EditKind.PRIVACY).map(EditMark::pos).orElse(null),
                portal, texts().stream().map(EditMark::hallText).toList());
    }

    List<EditMark> marks() {
        return Collections.unmodifiableList(marks);
    }

    Optional<EditMark> single(EditKind kind) {
        return marks.stream().filter(m -> m.kind() == kind).findFirst();
    }

    List<EditMark> stages() {
        return of(EditKind.STAGE);
    }

    List<EditMark> texts() {
        return of(EditKind.TEXT);
    }

    List<EditMark> of(EditKind kind) {
        return marks.stream().filter(m -> m.kind() == kind).toList();
    }

    int count(EditKind kind) {
        return (int) marks.stream().filter(m -> m.kind() == kind).count();
    }

    Placed place(EditMark mark) {
        switch (mark.kind()) {
            case STAGE:
                if (count(EditKind.STAGE) >= HallLayout.MAX_STAGES) {
                    return Placed.STAGE_LIMIT;
                }
                marks.add(mark);
                return Placed.ADDED;
            case TEXT:
                marks.add(mark);
                return Placed.ADDED;
            default:
                boolean replaced = marks.removeIf(m -> m.kind() == mark.kind());
                marks.add(mark);
                return replaced ? Placed.REPLACED : Placed.ADDED;
        }
    }

    /** The marker of {@code kind} closest to the point, if one is within {@code radius}. */
    Optional<EditMark> nearest(EditKind kind, double x, double y, double z, double radius) {
        EditMark best = null;
        double bestDistance = radius * radius;
        for (EditMark m : marks) {
            double d = m.distanceSquared(x, y, z);
            if (m.kind() == kind && d <= bestDistance) {
                best = m;
                bestDistance = d;
            }
        }
        return Optional.ofNullable(best);
    }

    boolean remove(EditMark mark) {
        return marks.remove(mark);
    }

    /** Removes the marker of {@code kind} closest to the point, if one is within {@code radius}. */
    Optional<EditMark> removeNearest(EditKind kind, double x, double y, double z, double radius) {
        Optional<EditMark> found = nearest(kind, x, y, z, radius);
        found.ifPresent(marks::remove);
        return found;
    }

    /** Takes the marker placed last off again. */
    Optional<EditMark> undo() {
        if (marks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(marks.remove(marks.size() - 1));
    }
}
