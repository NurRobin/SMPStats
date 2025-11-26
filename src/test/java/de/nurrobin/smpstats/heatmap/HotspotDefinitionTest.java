package de.nurrobin.smpstats.heatmap;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotspotDefinitionTest {

    @Test
    void containsChecksBoundsAndWorld() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        Location inside = new Location(world, 0, 64, 0);
        Location outside = new Location(world, 200, 64, 0);

        HotspotDefinition hs = new HotspotDefinition("spawn", "world", -100, -100, 100, 100);
        assertTrue(hs.contains(inside));
        assertFalse(hs.contains(outside));
    }

    @Test
    void handlesNullsAndWorldMismatch() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");
        Location location = new Location(world, 10, 64, 10);

        HotspotDefinition hs = new HotspotDefinition("spawn", "world", -50, -50, 50, 50);

        assertFalse(hs.contains(null));
        assertFalse(hs.contains(new Location(null, 0, 0, 0)));

        World otherWorld = Mockito.mock(World.class);
        Mockito.when(otherWorld.getName()).thenReturn("nether");
        assertFalse(hs.contains(new Location(otherWorld, 10, 64, 10)));

        // bounds are normalized even when min/max are swapped in constructor
        HotspotDefinition reversed = new HotspotDefinition("spawn", "world", 50, 50, -50, -50);
        assertTrue(reversed.contains(location));
    }
}
