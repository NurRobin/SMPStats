package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeCategory;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeTier;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AchievementBadge record and its enums.
 */
class AchievementBadgeTest {

    @Test
    void testBadgeTierLevels() {
        // Verify tiers are ordered correctly
        assertTrue(BadgeTier.BRONZE.getLevel() < BadgeTier.SILVER.getLevel());
        assertTrue(BadgeTier.SILVER.getLevel() < BadgeTier.GOLD.getLevel());
        assertTrue(BadgeTier.GOLD.getLevel() < BadgeTier.DIAMOND.getLevel());
        assertTrue(BadgeTier.DIAMOND.getLevel() < BadgeTier.LEGENDARY.getLevel());
    }

    @Test
    void testBadgeTierDisplayNames() {
        assertNotNull(BadgeTier.BRONZE.getDisplayName());
        assertNotNull(BadgeTier.SILVER.getDisplayName());
        assertNotNull(BadgeTier.GOLD.getDisplayName());
        assertNotNull(BadgeTier.DIAMOND.getDisplayName());
        assertNotNull(BadgeTier.LEGENDARY.getDisplayName());
        
        // All should contain emoji indicators
        assertTrue(BadgeTier.BRONZE.getDisplayName().contains("Bronze"));
        assertTrue(BadgeTier.LEGENDARY.getDisplayName().contains("Legendary"));
    }

    @Test
    void testBadgeCategoryDisplayNames() {
        for (BadgeCategory category : BadgeCategory.values()) {
            assertNotNull(category.getDisplayName());
            assertNotNull(category.getDescription());
            assertFalse(category.getDisplayName().isEmpty());
            assertFalse(category.getDescription().isEmpty());
        }
    }

    @Test
    void testAllCategoriesExist() {
        // Verify we have all expected categories
        assertEquals(7, BadgeCategory.values().length);
        assertNotNull(BadgeCategory.COMBAT);
        assertNotNull(BadgeCategory.EXPLORATION);
        assertNotNull(BadgeCategory.MINING);
        assertNotNull(BadgeCategory.BUILDING);
        assertNotNull(BadgeCategory.SOCIAL);
        assertNotNull(BadgeCategory.SURVIVAL);
        assertNotNull(BadgeCategory.DEDICATION);
    }

    @Test
    void testBadgeRecordProperties() {
        AchievementBadge badge = new AchievementBadge(
                "test_badge",
                "Test Badge",
                "A test badge for testing",
                org.bukkit.Material.DIAMOND,
                BadgeCategory.COMBAT,
                BadgeTier.GOLD
        );

        assertEquals("test_badge", badge.id());
        assertEquals("Test Badge", badge.name());
        assertEquals("A test badge for testing", badge.description());
        assertEquals(org.bukkit.Material.DIAMOND, badge.icon());
        assertEquals(BadgeCategory.COMBAT, badge.category());
        assertEquals(BadgeTier.GOLD, badge.tier());
    }
}
