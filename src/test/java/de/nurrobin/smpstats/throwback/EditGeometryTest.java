package de.nurrobin.smpstats.throwback;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditGeometryTest {

    @Test
    void yawSnapsToQuarterTurns() {
        assertEquals(0f, EditGeometry.snapYaw(0f));
        assertEquals(0f, EditGeometry.snapYaw(44f));
        assertEquals(90f, EditGeometry.snapYaw(46f));
        assertEquals(90f, EditGeometry.snapYaw(134f));
        assertEquals(180f, EditGeometry.snapYaw(136f));
        assertEquals(180f, EditGeometry.snapYaw(179f));
        assertEquals(180f, EditGeometry.snapYaw(-179f));
        assertEquals(-90f, EditGeometry.snapYaw(-91f));
        assertEquals(-90f, EditGeometry.snapYaw(270f));
        assertEquals(0f, EditGeometry.snapYaw(-10f));
        assertEquals(0f, EditGeometry.snapYaw(350f));
    }

    @Test
    void boardsLookBackAtTheBuilder() {
        assertEquals(180f, EditGeometry.towardViewerYaw(0f));
        assertEquals(-90f, EditGeometry.towardViewerYaw(90f));
        assertEquals(0f, EditGeometry.towardViewerYaw(180f));
        assertEquals(90f, EditGeometry.towardViewerYaw(-90f));
        assertEquals(-90f, EditGeometry.towardViewerYaw(80f), "the look direction is snapped first");
    }

    @Test
    void facesFollowTheMinecraftYaw() {
        assertEquals(BlockFace.SOUTH, EditGeometry.faceOf(0f));
        assertEquals(BlockFace.WEST, EditGeometry.faceOf(90f));
        assertEquals(BlockFace.NORTH, EditGeometry.faceOf(180f));
        assertEquals(BlockFace.EAST, EditGeometry.faceOf(-90f));
        assertEquals(BlockFace.WEST, EditGeometry.faceOf(100f));
    }

    @Test
    void startStandsOnTopOfTheBlockAndLooksLikeTheBuilder() {
        EditMark m = EditGeometry.mark(EditKind.START, 3, 63, -7, null, null, 130f);

        assertEquals(new EditMark(EditKind.START, 3.5, 64.0, -6.5, 90f, null), m);
    }

    @Test
    void rulesAndPrivacyFaceTheBuilder() {
        assertEquals(180f, EditGeometry.mark(EditKind.RULES, 0, 63, 0, null, null, 0f).yaw());
        assertEquals(-90f, EditGeometry.mark(EditKind.PRIVACY, 0, 63, 0, null, null, 90f).yaw());
    }

    @Test
    void stageLooksLikeTheBuilderAndSitsOnTheEdgeNearestTheClick() {
        // looking west (towards the back wall of a west alcove): width runs along z, so z snaps to an edge
        EditMark low = EditGeometry.mark(EditKind.STAGE, -5, 63, 12, -4.5, 12.2, 90f);
        EditMark high = EditGeometry.mark(EditKind.STAGE, -5, 63, 12, -4.5, 12.8, 90f);

        assertEquals(90f, low.yaw());
        assertEquals(-4.5, low.x(), 1e-9, "centred on the block along the facing");
        assertEquals(64.0, low.y(), 1e-9);
        assertEquals(12.0, low.z(), 1e-9);
        assertEquals(13.0, high.z(), 1e-9);
    }

    @Test
    void stageLookingNorthOrSouthSnapsX() {
        EditMark m = EditGeometry.mark(EditKind.STAGE, 4, 63, 9, 4.9, 9.5, 180f);

        assertEquals(5.0, m.x(), 1e-9);
        assertEquals(9.5, m.z(), 1e-9);
        assertEquals(180f, m.yaw());
    }

    @Test
    void stageWithoutAClickPointTakesTheLowerEdge() {
        assertEquals(12.0, EditGeometry.mark(EditKind.STAGE, -5, 63, 12, null, null, -90f).z(), 1e-9);
    }

    @Test
    void stageStaysInsideTheClickedBlockEvenForOddHitPoints() {
        assertEquals(13.0, EditGeometry.mark(EditKind.STAGE, -5, 63, 12, -4.5, 99.0, 90f).z(), 1e-9);
        assertEquals(12.0, EditGeometry.mark(EditKind.STAGE, -5, 63, 12, -4.5, -99.0, 90f).z(), 1e-9);
    }

    @Test
    void portalCornersSitOnTheBlockAboveTheClickedOne() {
        EditMark m = EditGeometry.mark(EditKind.PORTAL_1, 1, 63, 5, null, null, 33f);

        assertEquals(1, m.blockX());
        assertEquals(64, m.blockY());
        assertEquals(5, m.blockZ());
    }

    @Test
    void wallFramesFaceAgainstTheLookDirection() {
        WallDef west = EditGeometry.wall(2, 63, 40, 90f, "a", 4, 3, "T", "");
        WallDef south = EditGeometry.wall(2, 63, 40, 5f, "b", 4, 3, "T", "");

        assertEquals(BlockFace.EAST, west.facing(), "looking at a wall to the west, the frames face east");
        assertEquals(BlockFace.NORTH, south.facing());
        assertEquals(64, west.y(), "the corner is the block above the clicked one");
        assertEquals(40, west.z());
    }

    @Test
    void wallsAreNotMadeByMark() {
        assertThrows(IllegalArgumentException.class, () -> EditGeometry.mark(EditKind.WALL, 0, 0, 0, null, null, 0f));
    }
}
