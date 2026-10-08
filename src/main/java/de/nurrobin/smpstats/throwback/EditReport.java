package de.nurrobin.smpstats.throwback;

import java.util.ArrayList;
import java.util.List;

/**
 * What is still missing before the hall can be saved. {@code have/need} is shown for countable parts (stages,
 * portal corners); parts that are only there or not use 1 of 1. Blocked boxes are only warnings.
 */
record EditReport(List<Check> checks, List<EditElement> blocked) {

    record Check(String id, boolean ok, int have, int need) {
    }

    boolean complete() {
        return checks.stream().allMatch(Check::ok);
    }

    static EditReport of(EditDraft draft, int minStages, List<EditElement> elements) {
        List<Check> checks = new ArrayList<>();
        checks.add(single("start", draft.count(EditKind.START)));
        checks.add(single("rules", draft.count(EditKind.RULES)));
        checks.add(single("privacy", draft.count(EditKind.PRIVACY)));
        int corners = draft.count(EditKind.PORTAL_1) + draft.count(EditKind.PORTAL_2);
        checks.add(new Check("portal", corners == 2, corners, 2));
        int stages = draft.count(EditKind.STAGE);
        checks.add(new Check("stages", stages >= minStages && stages <= HallLayout.MAX_STAGES, stages, minStages));
        return new EditReport(List.copyOf(checks), elements.stream().filter(EditElement::blocked).toList());
    }

    private static Check single(String id, int count) {
        return new Check(id, count == 1, count, 1);
    }
}
