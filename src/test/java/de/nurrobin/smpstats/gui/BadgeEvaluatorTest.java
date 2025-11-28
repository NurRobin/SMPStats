package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeCategory;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the BadgeEvaluator class.
 */
class BadgeEvaluatorTest {

    private StatsRecord createBasicRecord(UUID uuid) {
        return new StatsRecord(uuid, "TestPlayer");
    }
    
    private StatsRecord createRecordWithMobKills(UUID uuid, long mobKills) {
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        record.setMobKills(mobKills);
        return record;
    }
    
    private StatsRecord createRecordWithDistance(UUID uuid, double overworld, double nether, double end) {
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        record.setDistanceOverworld(overworld);
        record.setDistanceNether(nether);
        record.setDistanceEnd(end);
        return record;
    }
    
    private StatsRecord createRecordWithBlocks(UUID uuid, long broken, long placed) {
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        record.setBlocksBroken(broken);
        record.setBlocksPlaced(placed);
        return record;
    }
    
    private StatsRecord createRecordWithPlaytime(UUID uuid, long playtimeMillis) {
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        record.setPlaytimeMillis(playtimeMillis);
        return record;
    }

    @Test
    void testNoStatsNoBadges() {
        StatsRecord record = createBasicRecord(UUID.randomUUID());
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        
        // Should have zero or only badges for 0-threshold things
        // The "survivor" badge requires <10 deaths but also >1 hour playtime
        assertTrue(badges.isEmpty() || badges.stream().allMatch(b -> b.tier() == BadgeTier.BRONZE));
    }

    @Test
    void testFirstBloodBadge() {
        StatsRecord record = createRecordWithMobKills(UUID.randomUUID(), 1);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("first_blood")),
                "Should earn 'First Blood' badge with 1 mob kill");
    }

    @Test
    void testHunterBadge() {
        StatsRecord record = createRecordWithMobKills(UUID.randomUUID(), 100);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("hunter")),
                "Should earn 'Hunter' badge with 100 mob kills");
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("first_blood")),
                "Should also earn 'First Blood' badge");
    }

    @Test
    void testTravelerBadge() {
        StatsRecord record = createRecordWithDistance(UUID.randomUUID(), 10000, 0, 0);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("traveler")),
                "Should earn 'Traveler' badge with 10km traveled");
    }

    @Test
    void testMiningBadges() {
        StatsRecord record = createRecordWithBlocks(UUID.randomUUID(), 10000, 0);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("stone_breaker")),
                "Should earn 'Stone Breaker' badge");
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("miner")),
                "Should earn 'Miner' badge");
    }

    @Test
    void testBuildingBadges() {
        StatsRecord record = createRecordWithBlocks(UUID.randomUUID(), 0, 10000);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("placer")),
                "Should earn 'Placer' badge");
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("builder")),
                "Should earn 'Builder' badge");
    }

    @Test
    void testDedicationBadges() {
        long oneHour = TimeUnit.HOURS.toMillis(1);
        StatsRecord record = createRecordWithPlaytime(UUID.randomUUID(), oneHour);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("newcomer")),
                "Should earn 'Newcomer' badge with 1 hour playtime");
    }

    @Test
    void testVeteranBadge() {
        long hundredHours = TimeUnit.HOURS.toMillis(100);
        StatsRecord record = createRecordWithPlaytime(UUID.randomUUID(), hundredHours);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("veteran")),
                "Should earn 'Veteran' badge with 100 hours playtime");
    }

    @Test
    void testBiomesBadges() {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        
        // Add 10 biomes
        for (String biome : Set.of("PLAINS", "FOREST", "DESERT", "TAIGA", "SWAMP",
                "JUNGLE", "BEACH", "OCEAN", "RIVER", "MOUNTAIN")) {
            record.addBiome(biome);
        }
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("biome_seeker")),
                "Should earn 'Biome Seeker' badge with 10 biomes");
    }

    @Test
    void testBadgesSortedByTier() {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        record.setMobKills(1000);
        record.setBlocksBroken(100000);
        record.setBlocksPlaced(100000);
        record.setDistanceOverworld(100000);
        record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(100));
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        
        // Verify sorting: highest tiers should come first
        for (int i = 1; i < badges.size(); i++) {
            int prevLevel = badges.get(i - 1).tier().getLevel();
            int currLevel = badges.get(i).tier().getLevel();
            assertTrue(prevLevel >= currLevel,
                    "Badges should be sorted by tier (highest first)");
        }
    }

    @Test
    void testGetAllBadges() {
        List<BadgeEvaluator.BadgeDefinition> allBadges = BadgeEvaluator.getAllBadges();
        
        assertNotNull(allBadges);
        assertFalse(allBadges.isEmpty());
        assertTrue(allBadges.size() >= 30, "Should have at least 30 badge definitions");
    }

    @Test
    void testGetTotalBadgeCount() {
        int count = BadgeEvaluator.getTotalBadgeCount();
        
        assertTrue(count >= 30, "Should have at least 30 badges defined");
        assertEquals(BadgeEvaluator.getAllBadges().size(), count);
    }

    @Test
    void testCountBadgesInCategory() {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        record.setMobKills(100);
        record.setDistanceOverworld(10000);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        
        int combatCount = BadgeEvaluator.countBadgesInCategory(badges, BadgeCategory.COMBAT);
        int explorationCount = BadgeEvaluator.countBadgesInCategory(badges, BadgeCategory.EXPLORATION);
        
        assertTrue(combatCount >= 1, "Should have at least 1 combat badge");
        assertTrue(explorationCount >= 1, "Should have at least 1 exploration badge");
    }

    @Test
    void testGetHighestTierInCategory() {
        StatsRecord record = createRecordWithMobKills(UUID.randomUUID(), 1000);
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        BadgeTier highestCombat = BadgeEvaluator.getHighestTierInCategory(badges, BadgeCategory.COMBAT);
        
        assertNotNull(highestCombat);
        assertEquals(BadgeTier.GOLD, highestCombat, 
                "1000 kills should give Gold tier combat badge (slayer)");
    }

    @Test
    void testNoCategoryBadgesReturnsNull() {
        StatsRecord record = createBasicRecord(UUID.randomUUID());
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        BadgeTier highestCombat = BadgeEvaluator.getHighestTierInCategory(badges, BadgeCategory.COMBAT);
        
        assertNull(highestCombat, "Should return null when no badges in category");
    }

    @Test
    void testImmortalBadge() {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        record.setDeaths(0);
        record.setPlaytimeMillis(TimeUnit.HOURS.toMillis(10));
        
        List<AchievementBadge> badges = BadgeEvaluator.evaluateBadges(record);
        assertTrue(badges.stream().anyMatch(b -> b.id().equals("immortal")),
                "Should earn 'Immortal' badge with 0 deaths and 10+ hours");
        
        // Verify it's legendary tier
        AchievementBadge immortal = badges.stream()
                .filter(b -> b.id().equals("immortal"))
                .findFirst()
                .orElse(null);
        assertNotNull(immortal);
        assertEquals(BadgeTier.LEGENDARY, immortal.tier());
    }
}
