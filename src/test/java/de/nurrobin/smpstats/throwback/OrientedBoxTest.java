package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrientedBoxTest {

    @Test
    void unitBoxHasTwelveEdgesAndEveryPointOnce() {
        OrientedBox box = OrientedBox.aabb(0, 0, 0, 1, 1, 1);

        List<OrientedBox.Pt> points = box.edgePoints(0.5);

        assertEquals(8 + 12, points.size(), "8 corners and one point in the middle of each of the 12 edges");
        assertEquals(points.size(), new HashSet<>(points).size());
        for (OrientedBox.Pt p : points) {
            assertTrue(onEdge(p.x(), 0, 1) && onEdge(p.y(), 0, 1) && onEdge(p.z(), 0, 1));
        }
    }

    private static boolean onEdge(double v, double lo, double hi) {
        return Math.abs(v - lo) < 1e-9 || Math.abs(v - hi) < 1e-9 || (v > lo && v < hi);
    }

    @Test
    void pointSpacingIsAboutHalfABlock() {
        OrientedBox box = OrientedBox.aabb(0, 0, 0, 8, 5, 5);

        // 4 edges of 8 blocks (15 inner points each), 8 edges of 5 blocks (9 each), plus the 8 corners
        assertEquals(4 * 15 + 8 * 9 + 8, box.edgePoints(0.5).size());
    }

    @Test
    void quarterTurnsStayExact() {
        OrientedBox west = OrientedBox.of(-4.5, 12, 90f, -2.5, 2.5, -4, 4, 64, 69);

        double[] hull = west.hull();

        assertEquals(-7.0, hull[0], 1e-9);
        assertEquals(-2.0, hull[1], 1e-9);
        assertEquals(8.0, hull[2], 1e-9);
        assertEquals(16.0, hull[3], 1e-9);
        assertEquals(-1.0, west.fx());
        assertEquals(0.0, west.fz());
    }

    @Test
    void turningMovesTheBoxToTheOtherSideOfTheOrigin() {
        OrientedBox south = OrientedBox.of(0, 0, 0f, 0, 2, -1, 1, 0, 1);
        OrientedBox east = OrientedBox.of(0, 0, -90f, 0, 2, -1, 1, 0, 1);

        assertTrue(south.intersectsBlock(0, 0, 1));
        assertFalse(south.intersectsBlock(1, 0, 3));
        assertTrue(east.intersectsBlock(1, 0, 0));
        assertFalse(east.intersectsBlock(0, 0, 1));
        assertEquals(2 * 2, south.blocks().size());
        assertEquals(2 * 2, east.blocks().size());
    }

    @Test
    void aBoxOnlyTouchingABlockDoesNotCollide() {
        OrientedBox box = OrientedBox.aabb(0, 64, 0, 3, 69, 3);

        assertTrue(box.intersectsBlock(2, 64, 2));
        assertFalse(box.intersectsBlock(3, 64, 0), "next to the box");
        assertFalse(box.intersectsBlock(0, 63, 0), "the floor under the box");
        assertFalse(box.intersectsBlock(0, 69, 0), "the ceiling over the box");
        assertEquals(3 * 3 * 5, box.blocks().size());
    }

    @Test
    void anOddAngleStillFindsTheBlocksItCovers() {
        OrientedBox tilted = OrientedBox.of(0.5, 0.5, 45f, -0.5, 0.5, -0.5, 0.5, 0, 1);

        assertTrue(tilted.intersectsBlock(0, 0, 0));
        assertFalse(tilted.intersectsBlock(3, 0, 3));
        assertTrue(tilted.blocks().size() >= 1);
    }

    @Test
    void panelSlabsAreFlatAndStillFindTheirBlocks() {
        OrientedBox slab = OrientedBox.of(10.5, 20.5, 0f, -0.1, 0.1, -2, 2, 65, 66);

        Set<String> cells = new HashSet<>();
        for (int[] b : slab.blocks()) {
            cells.add(b[0] + "," + b[2]);
        }

        assertEquals(Set.of("8,20", "9,20", "10,20", "11,20", "12,20"), cells);
    }
}
