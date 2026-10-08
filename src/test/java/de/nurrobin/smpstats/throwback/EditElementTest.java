package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EditElementTest {
    private static final EditElement.PanelSizes SIZES = new EditElement.PanelSizes(1.5, 0.5);

    private WorldMock world;

    @BeforeEach
    void setUp() {
        world = MockBukkit.mock().addSimpleWorld("hall");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static EditElement single(EditDraft d, String type) {
        return EditElement.of(d, SIZES).stream().filter(e -> e.type().equals(type)).findFirst().orElseThrow();
    }

    private static EditDraft draftOf(EditMark... marks) {
        EditDraft d = new EditDraft();
        for (EditMark m : marks) {
            d.place(m);
        }
        return d;
    }

    @Test
    void stageNeedsTheFloorPlusTwoBlocksToTheSidesAndBackAndFiveUp() {
        EditElement e = single(draftOf(EditMark.of(EditKind.STAGE, -4.5, 64, 12, 90f)), "stage");

        double[] hull = e.clearance().get(0).hull();

        assertEquals(-7.0, hull[0], 1e-9, "back wall of the alcove");
        assertEquals(-2.0, hull[1], 1e-9, "front edge of the floor, open to the corridor");
        assertEquals(8.0, hull[2], 1e-9, "floor is 4 wide, 2 free blocks on each side");
        assertEquals(16.0, hull[3], 1e-9);
        assertEquals(64.0, e.clearance().get(0).y0());
        assertEquals(69.0, e.clearance().get(0).y1());
    }

    @Test
    void stageFloorIsAnExtraBoxOfThreeByFour() {
        EditElement e = single(draftOf(EditMark.of(EditKind.STAGE, -4.5, 64, 12, 90f)), "stage");

        assertEquals(1, e.footprint().size());
        double[] hull = e.footprint().get(0).hull();
        assertEquals(-5.0, hull[0], 1e-9);
        assertEquals(-2.0, hull[1], 1e-9);
        assertEquals(10.0, hull[2], 1e-9);
        assertEquals(14.0, hull[3], 1e-9);
        assertEquals(63.0, e.footprint().get(0).y0());
        assertEquals(64.0, e.footprint().get(0).y1());
    }

    @Test
    void stageTurnsWithItsYaw() {
        for (float yaw : new float[] {0f, 90f, 180f, -90f}) {
            EditElement e = single(draftOf(EditMark.of(EditKind.STAGE, 0.5, 64, 0, yaw)), "stage");
            double[] h = e.clearance().get(0).hull();
            boolean alongX = yaw == 90f || yaw == -90f;
            assertEquals(alongX ? 5.0 : 8.0, h[1] - h[0], 1e-9, "x extent at yaw " + yaw);
            assertEquals(alongX ? 8.0 : 5.0, h[3] - h[2], 1e-9, "z extent at yaw " + yaw);
        }
        double[] south = single(draftOf(EditMark.of(EditKind.STAGE, 0.5, 64, 0, 0f)), "stage").clearance().get(0).hull();
        assertEquals(-2.5, south[2], 1e-9, "front");
        assertEquals(2.5, south[3], 1e-9, "back, behind the figure");
        double[] north = single(draftOf(EditMark.of(EditKind.STAGE, 0.5, 64, 0, 180f)), "stage").clearance().get(0).hull();
        assertEquals(-2.5, north[2], 1e-9);
    }

    @Test
    void startIsAOneByTwoColumn() {
        EditElement e = single(draftOf(EditMark.of(EditKind.START, 0.5, 64, -6.5, 0f)), "start");

        assertEquals(1, e.clearance().get(0).blocks().stream().filter(b -> b[1] == 64).count());
        assertEquals(2, e.clearance().get(0).blocks().size());
    }

    @Test
    void portalBoxSpansBothCornersIncludingTheirBlocks() {
        EditDraft d = draftOf(EditMark.of(EditKind.PORTAL_1, 1.5, 64, 10.5, 0f), EditMark.of(EditKind.PORTAL_2, -0.5, 66, 11.5, 0f));

        EditElement e = single(d, "portal");

        assertEquals(3 * 3 * 2, e.clearance().get(0).blocks().size());
        assertEquals(2, e.marks().size());
    }

    @Test
    void aSinglePortalCornerIsOneBlock() {
        EditElement e = single(draftOf(EditMark.of(EditKind.PORTAL_2, 1.5, 64, 10.5, 0f)), "portal_corner");

        assertEquals("2", e.detail());
        assertEquals(1, e.clearance().get(0).blocks().size());
    }

    @Test
    void wallBoxIsTheFrameAreaInFrontOfTheWall() {
        WallDef wall = new WallDef("w", 5, 64, 40, BlockFace.EAST, 4, 3, "T", "");

        EditElement e = single(draftOf(EditMark.ofWall(wall)), "wall");

        // facing east: the viewer's right is north, so the wall runs from z 40 to z 37
        double[] h = e.clearance().get(0).hull();
        assertEquals(5.0, h[0], 1e-9);
        assertEquals(6.0, h[1], 1e-9);
        assertEquals(37.0, h[2], 1e-9);
        assertEquals(41.0, h[3], 1e-9);
        assertEquals(4 * 3, e.clearance().get(0).blocks().size());
        assertEquals("w", e.detail());
    }

    @Test
    void rulesBoardBoxIsAsWideAsTheRealPanelAndAsHighAsItsText() {
        EditMark rules = EditMark.of(EditKind.RULES, 2.5, 64, 100.5, -90f);
        EditDraft withPrivacy = draftOf(rules, EditMark.of(EditKind.PRIVACY, 20.5, 64, 100.5, -90f));

        EditElement e = single(withPrivacy, "rules");

        assertEquals(1, e.clearance().size(), "the privacy board has its own place, so no second box here");
        OrientedBox box = e.clearance().get(0);
        assertEquals(PanelMetrics.width(), box.t1() - box.t0(), 1e-9);
        assertEquals(4.0, PanelMetrics.width(), 1e-9);
        assertEquals(64 + PanelMetrics.RULES_Y, box.y0(), 1e-9);
        assertEquals(64 + PanelMetrics.RULES_Y + SIZES.rules(), box.y1(), 1e-9);
    }

    @Test
    void withoutAPrivacyPlaceTheFallbackBoardIsPartOfTheRulesElement() {
        EditElement e = single(draftOf(EditMark.of(EditKind.RULES, 2.5, 64, 100.5, -90f)), "rules");

        assertEquals(2, e.clearance().size());
        OrientedBox rulesBox = e.clearance().get(0);
        OrientedBox privacyBox = e.clearance().get(1);
        assertEquals(PanelMetrics.OFFSET * 2, Math.abs(privacyBox.center().z() - rulesBox.center().z()), 1e-9,
                "the two boards sit two blocks to either side of the station");
    }

    @Test
    void privacyBoardStandsAtItsOwnPlace() {
        EditElement e = single(draftOf(EditMark.of(EditKind.PRIVACY, 20.5, 64, 100.5, 180f)), "privacy");

        OrientedBox box = e.clearance().get(0);
        assertEquals(20.5, box.center().x(), 1e-9);
        assertEquals(100.5, box.center().z(), 1e-9);
        assertEquals(64 + PanelMetrics.PRIVACY_Y, box.y0(), 1e-9);
    }

    @Test
    void theBoardsOfTheTourAndOfThePreviewHangAtTheSamePlace() {
        Pos rules = new Pos(2.4, 64, 100.5, 90f);

        var board = PanelMetrics.rulesBoard(world, rules);
        var fallback = PanelMetrics.privacyBoard(world, rules, null);
        var own = PanelMetrics.privacyBoard(world, rules, new Pos(9, 64, 9, 0f));

        assertEquals(2.4 - Math.cos(Math.toRadians(90)) * 2, board.getX(), 1e-9);
        assertEquals(100.5 - 2.0, board.getZ(), 1e-9);
        assertEquals(100.5 + 2.0, fallback.getZ(), 1e-9);
        assertEquals(64 + 1.5, fallback.getY(), 1e-9);
        assertEquals(9.0, own.getX());
        assertEquals(64 + 1.5, own.getY());
    }

    // ------------------------------------------------------------------ collision

    /**
     * The blocks of the stand-in hall as {@link PlaceholderHall#build} lays them out: a solid shell with the
     * start room, the corridor and the alcoves carved out. (MockBukkit cannot place lanterns, so the real
     * build method cannot run here.) Extra solid blocks can be added to see the boxes react.
     */
    private static BlockProbe standInHall(Set<List<Integer>> extra) {
        int y0 = PlaceholderHall.Y0;
        return (x, y, z) -> {
            if (extra.contains(List.of(x, y, z))) {
                return true;
            }
            boolean shell = x >= -PlaceholderHall.OUTER_X - 1 && x <= PlaceholderHall.OUTER_X + 1 && y >= PlaceholderHall.FLOOR_Y
                    && y <= PlaceholderHall.FLOOR_Y + PlaceholderHall.HEIGHT + 1 && z >= -9 && z <= PlaceholderHall.END_Z + 2;
            if (!shell) {
                return false;
            }
            int top = y0 + PlaceholderHall.HEIGHT - 1;
            boolean carved = y >= y0 && y <= top
                    && ((x >= -5 && x <= 5 && z >= -8 && z <= 6)
                    || (Math.abs(x) <= PlaceholderHall.CORRIDOR_HALF && z >= 7 && z <= PlaceholderHall.END_Z));
            for (int k = 0; k < PlaceholderHall.ALCOVES_PER_SIDE && !carved; k++) {
                int c = PlaceholderHall.FIRST_ALCOVE_Z + PlaceholderHall.ALCOVE_SPACING * k;
                int z1 = c - PlaceholderHall.STAGE_WIDTH / 2 - PlaceholderHall.MARGIN;
                int z2 = c + PlaceholderHall.STAGE_WIDTH / 2 - 1 + PlaceholderHall.MARGIN;
                carved = y >= y0 && y <= top && z >= z1 && z <= z2 && Math.abs(x) >= PlaceholderHall.CORRIDOR_HALF + 1
                        && Math.abs(x) <= PlaceholderHall.OUTER_X;
            }
            return !carved;
        };
    }

    @Test
    void theStandInCorridorLeavesEveryStageFree() {
        EditDraft d = EditDraft.fromLayout(PlaceholderHall.layout());

        List<EditElement> elements = EditElement.of(d, SIZES);
        elements.forEach(e -> e.recompute(standInHall(Set.of())));

        assertEquals(12, elements.stream().filter(e -> e.type().equals("stage")).count());
        for (EditElement e : elements) {
            if (e.type().equals("stage") || e.type().equals("start") || e.type().equals("portal")) {
                assertFalse(e.blocked(), e.type() + " " + e.detail() + " should have free space in the stand-in hall");
            }
        }
    }

    @Test
    void aSolidBlockInTheFreeSpaceTurnsTheBoxRed() {
        EditElement stage = EditElement.of(EditDraft.fromLayout(PlaceholderHall.layout()), SIZES).get(1);
        stage.recompute(standInHall(Set.of()));
        assertFalse(stage.blocked());

        stage.recompute(standInHall(Set.of(List.of(-4, 66, 12))));

        assertTrue(stage.blocked());
    }

    @Test
    void theFloorUnderAStageDoesNotCount() {
        EditElement stage = single(draftOf(EditMark.of(EditKind.STAGE, 0.5, 64, 0, 0f)), "stage");
        BlockProbe onlyFloor = (x, y, z) -> y < 64;

        stage.recompute(onlyFloor);

        assertFalse(stage.blocked());
    }

    @Test
    void aRotatedStageChecksTheTurnedArea() {
        EditElement south = single(draftOf(EditMark.of(EditKind.STAGE, 0.5, 64, 0, 0f)), "stage");
        BlockProbe behind = (x, y, z) -> y >= 64 && x == 0 && z == 2;
        BlockProbe sideways = (x, y, z) -> y >= 64 && x == 4 && z == 0;
        BlockProbe tooFar = (x, y, z) -> y >= 64 && x == 5 && z == 0;

        south.recompute(behind);
        assertTrue(south.blocked(), "2 blocks behind the figure is inside the back margin");
        south.recompute(sideways);
        assertTrue(south.blocked(), "the side margin reaches x 4");
        south.recompute(tooFar);
        assertFalse(south.blocked());

        EditElement west = single(draftOf(EditMark.of(EditKind.STAGE, 0.5, 64, 0, 90f)), "stage");
        west.recompute((x, y, z) -> y >= 64 && x == -2 && z == 0);
        assertTrue(west.blocked(), "turned to the west, the back is at negative x");
        west.recompute((x, y, z) -> y >= 64 && x == 2 && z == 0);
        assertTrue(west.blocked(), "the floor in front of the figure reaches x 2");
        west.recompute((x, y, z) -> y >= 64 && x == 3 && z == 0);
        assertFalse(west.blocked());
    }

    @Test
    void changesNearAnElementAreFoundAndFarOnesAreNot() {
        EditElement stage = single(draftOf(EditMark.of(EditKind.STAGE, -4.5, 64, 12, 90f)), "stage");

        assertTrue(stage.near(-4, 66, 12));
        assertTrue(stage.near(-8, 64, 12), "one block outside still triggers a re-check");
        assertFalse(stage.near(30, 64, 12));
        assertFalse(stage.near(-4, 90, 12));
    }

    @Test
    void panelHeightGrowsWithTheNumberOfLines() {
        List<Component> two = List.of(Component.text("eins"), Component.text("zwei"));
        List<Component> wrapped = List.of(Component.text("x".repeat(200)));

        assertEquals(2, PanelMetrics.lines(two));
        assertEquals(4, PanelMetrics.lines(wrapped), "200 characters do not fit one 320 px line");
        assertTrue(PanelMetrics.height(wrapped) > PanelMetrics.height(two));
    }
}
