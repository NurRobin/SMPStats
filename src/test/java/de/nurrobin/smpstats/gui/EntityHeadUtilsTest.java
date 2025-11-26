package de.nurrobin.smpstats.gui;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityHeadUtilsTest {

    @Test
    void returnsCorrectMaterialForZombie() {
        assertEquals(Material.ZOMBIE_HEAD, EntityHeadUtils.getMaterial(EntityType.ZOMBIE));
    }

    @Test
    void returnsCorrectMaterialForSkeleton() {
        assertEquals(Material.SKELETON_SKULL, EntityHeadUtils.getMaterial(EntityType.SKELETON));
    }

    @Test
    void returnsCorrectMaterialForCreeper() {
        assertEquals(Material.CREEPER_HEAD, EntityHeadUtils.getMaterial(EntityType.CREEPER));
    }

    @Test
    void returnsBarrierForUnmappedType() {
        // PLAYER is explicitly not in the map
        assertEquals(Material.BARRIER, EntityHeadUtils.getMaterial(EntityType.PLAYER));
    }

    @Test
    void formatsNameCorrectly() {
        assertEquals("Zombie", EntityHeadUtils.formatName(EntityType.ZOMBIE));
        assertEquals("Wither Skeleton", EntityHeadUtils.formatName(EntityType.WITHER_SKELETON));
        assertEquals("Elder Guardian", EntityHeadUtils.formatName(EntityType.ELDER_GUARDIAN));
    }

    @Test
    void formatsNameWithSingleWord() {
        assertEquals("Pig", EntityHeadUtils.formatName(EntityType.PIG));
        assertEquals("Cow", EntityHeadUtils.formatName(EntityType.COW));
    }
}
