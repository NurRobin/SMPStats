package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrientedBoxTest {

    @Test
    void aBoxHasTwelveEdgesThatRunAlongOneAxisEach() {
        OrientedBox box = OrientedBox.aabb(0, 0, 0, 8, 5, 3);

        List<OrientedBox.Pt[]> edges = box.edges();

        assertEquals(12, edges.size());
        Set<String> distinct = new HashSet<>();
        double total = 0;
        for (OrientedBox.Pt[] e : edges) {
            int axes = (e[0].x() != e[1].x() ? 1 : 0) + (e[0].y() != e[1].y() ? 1 : 0) + (e[0].z() != e[1].z() ? 1 : 0);
            assertEquals(1, axes, "an edge runs along exactly one axis");
            total += Math.abs(e[1].x() - e[0].x()) + Math.abs(e[1].y() - e[0].y()) + Math.abs(e[1].z() - e[0].z());
            distinct.add(List.of(e[0], e[1]).toString());
        }
        assertEquals(12, distinct.size());
        assertEquals(4 * (8 + 5 + 3), total, 1e-9);
    }

    @Test
    void containsTakesTheMarginIntoAccount() {
        OrientedBox box = OrientedBox.aabb(0, 64, 0, 2, 66, 1);

        assertTrue(box.contains(1, 65, 0.5, 0));
        assertFalse(box.contains(2.2, 65, 0.5, 0));
        assertTrue(box.contains(2.2, 65, 0.5, 0.25));
        assertFalse(box.contains(1, 66.5, 0.5, 0.25));
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
