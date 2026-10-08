package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditReportTest {

    private static EditDraft draftWith(int stages) {
        EditDraft d = EditDraft.fromLayout(PlaceholderHall.layout().withPrivacy(new Pos(3.5, 64, 90.5, -90f)));
        while (d.stages().size() > stages) {
            d.remove(d.stages().get(d.stages().size() - 1));
        }
        return d;
    }

    private static EditReport.Check check(EditReport r, String id) {
        return r.checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void completeDraftMayBeSaved() {
        EditReport r = EditReport.of(draftWith(12), 8, List.of());

        assertTrue(r.complete());
        assertEquals(List.of("start", "rules", "privacy", "portal", "stages"), r.checks().stream().map(EditReport.Check::id).toList());
    }

    @Test
    void tooFewStagesShowHowManyAreThere() {
        EditReport r = EditReport.of(draftWith(6), 8, List.of());

        assertFalse(r.complete());
        EditReport.Check stages = check(r, "stages");
        assertFalse(stages.ok());
        assertEquals(6, stages.have());
        assertEquals(8, stages.need());
        assertTrue(check(r, "start").ok());
    }

    @Test
    void exactlyTheMinimumIsEnough() {
        assertTrue(EditReport.of(draftWith(8), 8, List.of()).complete());
        assertFalse(EditReport.of(draftWith(7), 8, List.of()).complete());
    }

    @Test
    void everyMissingPartIsReported() {
        EditReport r = EditReport.of(new EditDraft(), 8, List.of());

        assertEquals(5, r.checks().stream().filter(c -> !c.ok()).count());
        assertEquals(0, check(r, "portal").have());
        assertEquals(2, check(r, "portal").need());
    }

    @Test
    void withoutAPrivacyPlaceTheBoardBesideTheRulesCounts() {
        EditDraft d = EditDraft.fromLayout(PlaceholderHall.layout());

        EditReport r = EditReport.of(d, 8, List.of());

        assertTrue(r.complete());
        assertTrue(check(r, "privacy_default").ok());
        d.remove(d.single(EditKind.RULES).orElseThrow());
        EditReport noRules = EditReport.of(d, 8, List.of());
        assertFalse(check(noRules, "privacy").ok(), "without a rules board there is nothing to hang beside");
    }

    @Test
    void onePortalCornerIsNotEnough() {
        EditDraft d = draftWith(12);
        d.undo();
        d.remove(d.single(EditKind.PORTAL_2).orElseThrow());

        EditReport r = EditReport.of(d, 8, List.of());

        assertFalse(check(r, "portal").ok());
        assertEquals(1, check(r, "portal").have());
    }

    @Test
    void picturesAreOptional() {
        EditDraft d = draftWith(12);
        d.marks().stream().filter(m -> m.kind() == EditKind.WALL).toList().forEach(d::remove);

        assertTrue(EditReport.of(d, 8, List.of()).complete());
    }

    @Test
    void blockedBoxesOnlyWarn() {
        EditElement e = EditElement.of(draftWith(12), new EditElement.PanelSizes(1, 1)).get(1);
        e.blocked(true);

        EditReport r = EditReport.of(draftWith(12), 8, List.of(e));

        assertTrue(r.complete());
        assertEquals(List.of(e), r.blocked());
    }
}
