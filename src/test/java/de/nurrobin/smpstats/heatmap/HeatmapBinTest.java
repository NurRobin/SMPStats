package de.nurrobin.smpstats.heatmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeatmapBinTest {

    @Test
    void exposesConstructorValues() {
        HeatmapBin bin = new HeatmapBin("BREAK", "world", 5, -3, 42.5);

        assertEquals("BREAK", bin.getType());
        assertEquals("world", bin.getWorld());
        assertEquals(5, bin.getChunkX());
        assertEquals(-3, bin.getChunkZ());
        assertEquals(42.5, bin.getCount());
    }
}
