package de.nurrobin.smpstats.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AnimatedBorderService class.
 */
class AnimatedBorderServiceTest {

    @Test
    void testRainbowColorsContainsAllColors() {
        Material[] colors = AnimatedBorderService.RAINBOW_COLORS;
        
        assertEquals(10, colors.length);
        assertEquals(Material.RED_STAINED_GLASS_PANE, colors[0]);
        assertEquals(Material.ORANGE_STAINED_GLASS_PANE, colors[1]);
        assertEquals(Material.YELLOW_STAINED_GLASS_PANE, colors[2]);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, colors[3]);
        assertEquals(Material.CYAN_STAINED_GLASS_PANE, colors[4]);
        assertEquals(Material.PINK_STAINED_GLASS_PANE, colors[colors.length - 1]);
    }

    @Test
    void testGoldPulseColors() {
        Material[] colors = AnimatedBorderService.GOLD_PULSE;
        
        assertEquals(4, colors.length);
        assertEquals(Material.YELLOW_STAINED_GLASS_PANE, colors[0]);
        assertEquals(Material.ORANGE_STAINED_GLASS_PANE, colors[1]);
    }

    @Test
    void testSuccessPulseColors() {
        Material[] colors = AnimatedBorderService.SUCCESS_PULSE;
        
        assertEquals(4, colors.length);
        assertEquals(Material.LIME_STAINED_GLASS_PANE, colors[0]);
        assertEquals(Material.GREEN_STAINED_GLASS_PANE, colors[1]);
    }

    @Test
    void testWarningPulseColors() {
        Material[] colors = AnimatedBorderService.WARNING_PULSE;
        
        assertEquals(4, colors.length);
        assertEquals(Material.RED_STAINED_GLASS_PANE, colors[0]);
        assertEquals(Material.ORANGE_STAINED_GLASS_PANE, colors[1]);
    }

    @Test
    void testInfoPulseColors() {
        Material[] colors = AnimatedBorderService.INFO_PULSE;
        
        assertEquals(4, colors.length);
        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS_PANE, colors[0]);
        assertEquals(Material.CYAN_STAINED_GLASS_PANE, colors[1]);
    }

    @Test
    void testAnimationPresetEnum() {
        assertEquals(5, AnimatedBorderService.AnimationPreset.values().length);
        
        assertArrayEquals(AnimatedBorderService.RAINBOW_COLORS, 
                AnimatedBorderService.AnimationPreset.RAINBOW.getColors());
        assertArrayEquals(AnimatedBorderService.GOLD_PULSE, 
                AnimatedBorderService.AnimationPreset.GOLD_PULSE.getColors());
        assertArrayEquals(AnimatedBorderService.SUCCESS_PULSE, 
                AnimatedBorderService.AnimationPreset.SUCCESS.getColors());
        assertArrayEquals(AnimatedBorderService.WARNING_PULSE, 
                AnimatedBorderService.AnimationPreset.WARNING.getColors());
        assertArrayEquals(AnimatedBorderService.INFO_PULSE, 
                AnimatedBorderService.AnimationPreset.INFO.getColors());
    }

    @Test
    void testGetBorderSlots54() {
        int[] slots = AnimatedBorderService.getBorderSlots54();
        
        // Should have all border slots for 54-slot inventory
        // Top row (9) + side edges (8) + bottom row (9) = 26 slots
        assertEquals(26, slots.length);
        
        // Check first and last
        assertEquals(0, slots[0]);
        assertEquals(53, slots[slots.length - 1]);
        
        // Check top row is complete
        for (int i = 0; i < 9; i++) {
            assertEquals(i, slots[i]);
        }
    }

    @Test
    void testGetBorderSlots45() {
        int[] slots = AnimatedBorderService.getBorderSlots45();
        
        // Should have all border slots for 45-slot inventory
        assertEquals(24, slots.length);
        
        // Check first and last
        assertEquals(0, slots[0]);
        assertEquals(44, slots[slots.length - 1]);
    }

    @Test
    void testGetTopRowSlots() {
        int[] slots = AnimatedBorderService.getTopRowSlots();
        
        assertEquals(9, slots.length);
        for (int i = 0; i < 9; i++) {
            assertEquals(i, slots[i]);
        }
    }

    @Test
    void testGetBottomRowSlots54() {
        int[] slots = AnimatedBorderService.getBottomRowSlots54();
        
        assertEquals(9, slots.length);
        assertEquals(45, slots[0]);
        assertEquals(53, slots[8]);
    }

    @Test
    void testGetBottomRowSlots45() {
        int[] slots = AnimatedBorderService.getBottomRowSlots45();
        
        assertEquals(9, slots.length);
        assertEquals(36, slots[0]);
        assertEquals(44, slots[8]);
    }

    @Test
    void testCombineSlots() {
        int[] first = {0, 1, 2};
        int[] second = {10, 11, 12};
        int[] third = {20};
        
        int[] combined = AnimatedBorderService.combineSlots(first, second, third);
        
        assertEquals(7, combined.length);
        assertArrayEquals(new int[]{0, 1, 2, 10, 11, 12, 20}, combined);
    }

    @Test
    void testCombineSlotsEmpty() {
        int[] result = AnimatedBorderService.combineSlots();
        assertEquals(0, result.length);
    }

    @Test
    void testGetHighlightSlotsCenter() {
        // Test highlighting around center slot 22 in a 54-slot inventory
        int[] slots = AnimatedBorderService.getHighlightSlots(22, 9, 54);
        
        // Should have 8 adjacent slots
        assertEquals(8, slots.length);
        
        // Check that center slot (22) is not included
        for (int slot : slots) {
            assertNotEquals(22, slot);
        }
        
        // Should include slots around 22: 12,13,14,21,23,30,31,32
        assertTrue(contains(slots, 12)); // above-left
        assertTrue(contains(slots, 13)); // above
        assertTrue(contains(slots, 14)); // above-right
        assertTrue(contains(slots, 21)); // left
        assertTrue(contains(slots, 23)); // right
        assertTrue(contains(slots, 30)); // below-left
        assertTrue(contains(slots, 31)); // below
        assertTrue(contains(slots, 32)); // below-right
    }

    @Test
    void testGetHighlightSlotsCorner() {
        // Test highlighting around corner slot 0 (top-left)
        int[] slots = AnimatedBorderService.getHighlightSlots(0, 9, 54);
        
        // Should only have 3 adjacent slots (right, below, below-right)
        assertEquals(3, slots.length);
        
        assertTrue(contains(slots, 1));  // right
        assertTrue(contains(slots, 9));  // below
        assertTrue(contains(slots, 10)); // below-right
    }

    @Test
    void testGetHighlightSlotsEdge() {
        // Test highlighting around edge slot 4 (top center)
        int[] slots = AnimatedBorderService.getHighlightSlots(4, 9, 54);
        
        // Should have 5 adjacent slots (left, right, below-left, below, below-right)
        assertEquals(5, slots.length);
        
        assertTrue(contains(slots, 3));  // left
        assertTrue(contains(slots, 5));  // right
        assertTrue(contains(slots, 12)); // below-left
        assertTrue(contains(slots, 13)); // below
        assertTrue(contains(slots, 14)); // below-right
    }

    private boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) return true;
        }
        return false;
    }
}
