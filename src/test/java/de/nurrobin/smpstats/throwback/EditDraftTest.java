package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditDraftTest {

    private static EditMark stage(double x, double z) {
        return EditMark.of(EditKind.STAGE, x, 64, z, 90f);
    }

    @Test
    void singletonsReplaceTheirPredecessor() {
        EditDraft d = new EditDraft();

        assertEquals(EditDraft.Placed.ADDED, d.place(EditMark.of(EditKind.START, 0.5, 64, 0.5, 0f)));
        assertEquals(EditDraft.Placed.REPLACED, d.place(EditMark.of(EditKind.START, 9.5, 64, 0.5, 90f)));

        assertEquals(1, d.count(EditKind.START));
        assertEquals(9.5, d.single(EditKind.START).orElseThrow().x());
    }

    @Test
    void stagesGetTheNextNumberUpToTwelve() {
        EditDraft d = new EditDraft();
        for (int i = 0; i < HallLayout.MAX_STAGES; i++) {
            assertEquals(EditDraft.Placed.ADDED, d.place(stage(i, 0)));
        }

        assertEquals(EditDraft.Placed.STAGE_LIMIT, d.place(stage(99, 0)));
        assertEquals(12, d.stages().size());
        assertEquals(5.0, d.stages().get(5).x());
    }

    @Test
    void removingAStageClosesTheGapInTheNumbering() {
        EditDraft d = new EditDraft();
        d.place(stage(0, 0));
        d.place(stage(10, 0));
        d.place(stage(20, 0));

        d.removeNearest(EditKind.STAGE, 10, 64, 0, 1);

        assertEquals(20.0, d.stages().get(1).x());
        assertEquals(2, d.stages().size());
        assertEquals(2, d.toLayout().stages().size());
    }

    @Test
    void removeTakesTheNearestOfThatKindWithinTwoBlocks() {
        EditDraft d = new EditDraft();
        d.place(stage(0, 0));
        d.place(stage(1, 0));
        d.place(EditMark.of(EditKind.START, 0.6, 64, 0, 0f));

        assertTrue(d.removeNearest(EditKind.STAGE, 0.9, 64, 0, 2.0).isPresent());
        assertEquals(0.0, d.stages().get(0).x(), "the stage at 1 was closer to 0.9");
        assertTrue(d.removeNearest(EditKind.STAGE, 5, 64, 0, 2.0).isEmpty(), "too far");
        assertEquals(1, d.count(EditKind.START), "other kinds are left alone");
        assertTrue(d.removeNearest(EditKind.STAGE, 2.0, 64, 0, 2.0).isPresent(), "exactly two blocks away still counts");
    }

    @Test
    void undoRemovesTheLastMarkerPlaced() {
        EditDraft d = new EditDraft();
        d.place(stage(0, 0));
        d.place(EditMark.of(EditKind.RULES, 0, 64, 0, 0f));

        assertEquals(EditKind.RULES, d.undo().orElseThrow().kind());
        assertEquals(EditKind.STAGE, d.undo().orElseThrow().kind());
        assertTrue(d.undo().isEmpty());
    }

    @Test
    void textsCanBePlacedAnyNumberOfTimes() {
        EditDraft d = new EditDraft();

        for (int i = 0; i < 20; i++) {
            assertEquals(EditDraft.Placed.ADDED, d.place(EditMark.ofText(new HallText(new Pos(i, 64, 0, 90f), "Titel " + i))));
        }

        assertEquals(20, d.texts().size());
        assertEquals("Titel 19", d.texts().get(19).text());
    }

    @Test
    void textsSurviveTheRoundTripThroughADraftInOrder() {
        HallLayout layout = PlaceholderHall.layout();
        layout = new HallLayout(layout.start(), layout.stages(), layout.rules(), layout.privacy(), layout.portal(),
                List.of(new HallText(new Pos(1.5, 64, 2.5, 90f), "<gold>Eins"), new HallText(new Pos(-3, 65, 7, -90f), "Zwei<br>Zeilen")));

        EditDraft d = EditDraft.fromLayout(layout);

        assertEquals(2, d.texts().size());
        assertEquals(EditKind.TEXT, d.texts().get(0).kind());
        assertEquals(layout, d.toLayout());
    }

    @Test
    void layoutSurvivesTheRoundTripThroughADraft() {
        HallLayout layout = PlaceholderHall.layout().withPrivacy(new Pos(3.5, 64, 90.5, -90f));

        HallLayout back = EditDraft.fromLayout(layout).toLayout();

        assertEquals(layout, back);
    }

    @Test
    void emptyLayoutGivesAnEmptyDraftAndBack() {
        assertTrue(EditDraft.fromLayout(HallLayout.empty()).marks().isEmpty());
        assertEquals(HallLayout.empty(), new EditDraft().toLayout());
    }

    @Test
    void aPortalNeedsBothCorners() {
        EditDraft d = new EditDraft();
        d.place(EditMark.of(EditKind.PORTAL_1, 0.5, 64, 10.5, 0f));
        assertNull(d.toLayout().portal());

        d.place(EditMark.of(EditKind.PORTAL_2, 3.5, 66, 11.5, 0f));

        assertEquals(Region.of(0, 64, 10, 3, 66, 11), d.toLayout().portal());
    }
}
