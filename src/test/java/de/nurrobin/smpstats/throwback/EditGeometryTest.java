package de.nurrobin.smpstats.throwback;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void rulesAndAPrivacyBoardOnTheFloorFaceTheBuilder() {
        assertEquals(180f, EditGeometry.mark(EditKind.RULES, 0, 63, 0, null, null, 0f).yaw());
        assertEquals(-90f, EditGeometry.privacy(0, 63, 0, BlockFace.UP, 90f, 1.0).yaw());
    }

    @Test
    void aPrivacyBoardClickedOnTheFloorStandsOnIt() {
        assertEquals(new EditMark(EditKind.PRIVACY, 4.5, 64.0, 9.5, 180f, null), EditGeometry.privacy(4, 63, 9, BlockFace.UP, 10f, 1.4));
    }

    @Test
    void aPrivacyBoardClickedOnAWallHangsFlatOnItCentredOnTheClickedBlock() {
        // the north side of the block at z 10 was clicked: the board hangs just north of it and faces north
        EditMark m = EditGeometry.privacy(4, 66, 10, BlockFace.NORTH, 33f, 1.4);

        assertEquals(4.5, m.x(), 1e-9);
        assertEquals(10.5 - 0.5 - PanelMetrics.THICKNESS / 2, m.z(), 1e-9, "flat against the wall");
        assertEquals(66.5 - 0.7, m.y(), 1e-9, "the middle of the board is the middle of the clicked block");
        assertEquals(180f, m.yaw(), "readable from the north, whatever way the builder looked");
    }

    @Test
    void aPrivacyBoardClickedOnACeilingHangsFromIt() {
        assertEquals(70 - 1.4, EditGeometry.privacy(0, 70, 0, BlockFace.DOWN, 0f, 1.4).y(), 1e-9);
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
    void aPortalCornerOnASolidBlockIsTheBlockOnTheClickedSide() {
        EditMark top = EditGeometry.portalCorner(EditKind.PORTAL_1, 1, 63, 5, false, BlockFace.UP);
        EditMark side = EditGeometry.portalCorner(EditKind.PORTAL_2, 1, 65, 5, false, BlockFace.EAST);

        assertEquals(List.of(1, 64, 5), List.of(top.blockX(), top.blockY(), top.blockZ()), "the floor: the block above it");
        assertEquals(List.of(2, 65, 5), List.of(side.blockX(), side.blockY(), side.blockZ()), "a frame: the block next to it");
        assertEquals(EditKind.PORTAL_2, side.kind());
    }

    @Test
    void aPortalCornerOnAPassableBlockIsThatBlock() {
        EditMark m = EditGeometry.portalCorner(EditKind.PORTAL_1, 1, 64, 5, true, BlockFace.SOUTH);

        assertEquals(List.of(1, 64, 5), List.of(m.blockX(), m.blockY(), m.blockZ()));
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
    void wallsPortalsAndPrivacyAreNotMadeByMark() {
        assertThrows(IllegalArgumentException.class, () -> EditGeometry.mark(EditKind.WALL, 0, 0, 0, null, null, 0f));
        assertThrows(IllegalArgumentException.class, () -> EditGeometry.mark(EditKind.PORTAL_1, 0, 0, 0, null, null, 0f));
        assertThrows(IllegalArgumentException.class, () -> EditGeometry.mark(EditKind.PRIVACY, 0, 0, 0, null, null, 0f));
    }
}
