package de.nurrobin.smpstats.gui;

import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeCategory;
import de.nurrobin.smpstats.gui.AchievementBadge.BadgeTier;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates player statistics to determine earned achievement badges.
 * Provides a centralized system for badge definitions and evaluation logic.
 */
public class BadgeEvaluator {
    
    // Singleton list of all available badges
    private static final List<BadgeDefinition> BADGE_DEFINITIONS = new ArrayList<>();
    
    static {
        // === COMBAT BADGES ===
        registerBadge("first_blood", "First Blood", "Get your first mob kill",
                Material.WOODEN_SWORD, BadgeCategory.COMBAT, BadgeTier.BRONZE,
                record -> record.getMobKills() >= 1);
        
        registerBadge("hunter", "Hunter", "Kill 100 mobs",
                Material.IRON_SWORD, BadgeCategory.COMBAT, BadgeTier.SILVER,
                record -> record.getMobKills() >= 100);
        
        registerBadge("slayer", "Slayer", "Kill 1,000 mobs",
                Material.DIAMOND_SWORD, BadgeCategory.COMBAT, BadgeTier.GOLD,
                record -> record.getMobKills() >= 1000);
        
        registerBadge("exterminator", "Exterminator", "Kill 10,000 mobs",
                Material.NETHERITE_SWORD, BadgeCategory.COMBAT, BadgeTier.DIAMOND,
                record -> record.getMobKills() >= 10000);
        
        registerBadge("pvp_initiate", "PvP Initiate", "Get your first player kill",
                Material.STONE_SWORD, BadgeCategory.COMBAT, BadgeTier.BRONZE,
                record -> record.getPlayerKills() >= 1);
        
        registerBadge("pvp_warrior", "PvP Warrior", "Kill 50 players",
                Material.GOLDEN_SWORD, BadgeCategory.COMBAT, BadgeTier.GOLD,
                record -> record.getPlayerKills() >= 50);
        
        registerBadge("damage_dealer", "Damage Dealer", "Deal 10,000 damage",
                Material.BLAZE_POWDER, BadgeCategory.COMBAT, BadgeTier.SILVER,
                record -> record.getDamageDealt() >= 10000);
        
        registerBadge("berserker", "Berserker", "Deal 100,000 damage",
                Material.FIRE_CHARGE, BadgeCategory.COMBAT, BadgeTier.GOLD,
                record -> record.getDamageDealt() >= 100000);
        
        // === EXPLORATION BADGES ===
        registerBadge("traveler", "Traveler", "Travel 10km total",
                Material.LEATHER_BOOTS, BadgeCategory.EXPLORATION, BadgeTier.BRONZE,
                record -> getTotalDistance(record) >= 10000);
        
        registerBadge("wanderer", "Wanderer", "Travel 100km total",
                Material.CHAINMAIL_BOOTS, BadgeCategory.EXPLORATION, BadgeTier.SILVER,
                record -> getTotalDistance(record) >= 100000);
        
        registerBadge("explorer", "Explorer", "Travel 500km total",
                Material.IRON_BOOTS, BadgeCategory.EXPLORATION, BadgeTier.GOLD,
                record -> getTotalDistance(record) >= 500000);
        
        registerBadge("globetrotter", "Globetrotter", "Travel 1,000km total",
                Material.DIAMOND_BOOTS, BadgeCategory.EXPLORATION, BadgeTier.DIAMOND,
                record -> getTotalDistance(record) >= 1000000);
        
        registerBadge("nether_tourist", "Nether Tourist", "Travel 10km in the Nether",
                Material.NETHERRACK, BadgeCategory.EXPLORATION, BadgeTier.SILVER,
                record -> record.getDistanceNether() >= 10000);
        
        registerBadge("end_visitor", "End Visitor", "Travel 5km in the End",
                Material.END_STONE, BadgeCategory.EXPLORATION, BadgeTier.SILVER,
                record -> record.getDistanceEnd() >= 5000);
        
        registerBadge("biome_seeker", "Biome Seeker", "Discover 10 different biomes",
                Material.FILLED_MAP, BadgeCategory.EXPLORATION, BadgeTier.BRONZE,
                record -> record.getBiomesVisited().size() >= 10);
        
        registerBadge("biome_collector", "Biome Collector", "Discover 30 different biomes",
                Material.MAP, BadgeCategory.EXPLORATION, BadgeTier.SILVER,
                record -> record.getBiomesVisited().size() >= 30);
        
        registerBadge("biome_master", "Biome Master", "Discover 50+ different biomes",
                Material.CARTOGRAPHY_TABLE, BadgeCategory.EXPLORATION, BadgeTier.GOLD,
                record -> record.getBiomesVisited().size() >= 50);
        
        // === MINING BADGES ===
        registerBadge("stone_breaker", "Stone Breaker", "Break 1,000 blocks",
                Material.WOODEN_PICKAXE, BadgeCategory.MINING, BadgeTier.BRONZE,
                record -> record.getBlocksBroken() >= 1000);
        
        registerBadge("miner", "Miner", "Break 10,000 blocks",
                Material.IRON_PICKAXE, BadgeCategory.MINING, BadgeTier.SILVER,
                record -> record.getBlocksBroken() >= 10000);
        
        registerBadge("excavator", "Excavator", "Break 100,000 blocks",
                Material.DIAMOND_PICKAXE, BadgeCategory.MINING, BadgeTier.GOLD,
                record -> record.getBlocksBroken() >= 100000);
        
        registerBadge("strip_miner", "Strip Miner", "Break 500,000 blocks",
                Material.NETHERITE_PICKAXE, BadgeCategory.MINING, BadgeTier.DIAMOND,
                record -> record.getBlocksBroken() >= 500000);
        
        // === BUILDING BADGES ===
        registerBadge("placer", "Placer", "Place 1,000 blocks",
                Material.OAK_PLANKS, BadgeCategory.BUILDING, BadgeTier.BRONZE,
                record -> record.getBlocksPlaced() >= 1000);
        
        registerBadge("builder", "Builder", "Place 10,000 blocks",
                Material.BRICKS, BadgeCategory.BUILDING, BadgeTier.SILVER,
                record -> record.getBlocksPlaced() >= 10000);
        
        registerBadge("architect", "Architect", "Place 100,000 blocks",
                Material.QUARTZ_BLOCK, BadgeCategory.BUILDING, BadgeTier.GOLD,
                record -> record.getBlocksPlaced() >= 100000);
        
        registerBadge("master_builder", "Master Builder", "Place 500,000 blocks",
                Material.CHISELED_QUARTZ_BLOCK, BadgeCategory.BUILDING, BadgeTier.DIAMOND,
                record -> record.getBlocksPlaced() >= 500000);
        
        registerBadge("crafter", "Crafter", "Craft 500 items",
                Material.CRAFTING_TABLE, BadgeCategory.BUILDING, BadgeTier.BRONZE,
                record -> record.getItemsCrafted() >= 500);
        
        registerBadge("artisan", "Artisan", "Craft 5,000 items",
                Material.LOOM, BadgeCategory.BUILDING, BadgeTier.SILVER,
                record -> record.getItemsCrafted() >= 5000);
        
        // === SURVIVAL BADGES ===
        registerBadge("survivor", "Survivor", "Stay alive (less than 10 deaths)",
                Material.GOLDEN_APPLE, BadgeCategory.SURVIVAL, BadgeTier.BRONZE,
                record -> record.getDeaths() < 10 && record.getPlaytimeMillis() > TimeUnit.HOURS.toMillis(1));
        
        registerBadge("immortal", "Immortal", "No deaths and 10+ hours playtime",
                Material.ENCHANTED_GOLDEN_APPLE, BadgeCategory.SURVIVAL, BadgeTier.LEGENDARY,
                record -> record.getDeaths() == 0 && record.getPlaytimeMillis() >= TimeUnit.HOURS.toMillis(10));
        
        registerBadge("tank", "Tank", "Take 50,000+ damage and survive",
                Material.SHIELD, BadgeCategory.SURVIVAL, BadgeTier.SILVER,
                record -> record.getDamageTaken() >= 50000);
        
        registerBadge("iron_stomach", "Iron Stomach", "Consume 500 items",
                Material.COOKED_BEEF, BadgeCategory.SURVIVAL, BadgeTier.BRONZE,
                record -> record.getItemsConsumed() >= 500);
        
        registerBadge("glutton", "Glutton", "Consume 5,000 items",
                Material.GOLDEN_CARROT, BadgeCategory.SURVIVAL, BadgeTier.SILVER,
                record -> record.getItemsConsumed() >= 5000);
        
        // === DEDICATION BADGES ===
        registerBadge("newcomer", "Newcomer", "Play for 1 hour",
                Material.CLOCK, BadgeCategory.DEDICATION, BadgeTier.BRONZE,
                record -> record.getPlaytimeMillis() >= TimeUnit.HOURS.toMillis(1));
        
        registerBadge("regular", "Regular", "Play for 24 hours",
                Material.COMPASS, BadgeCategory.DEDICATION, BadgeTier.SILVER,
                record -> record.getPlaytimeMillis() >= TimeUnit.HOURS.toMillis(24));
        
        registerBadge("veteran", "Veteran", "Play for 100 hours",
                Material.RECOVERY_COMPASS, BadgeCategory.DEDICATION, BadgeTier.GOLD,
                record -> record.getPlaytimeMillis() >= TimeUnit.HOURS.toMillis(100));
        
        registerBadge("addict", "Addict", "Play for 500 hours",
                Material.NETHER_STAR, BadgeCategory.DEDICATION, BadgeTier.DIAMOND,
                record -> record.getPlaytimeMillis() >= TimeUnit.HOURS.toMillis(500));
        
        registerBadge("legend", "Legend", "Play for 1,000+ hours",
                Material.DRAGON_EGG, BadgeCategory.DEDICATION, BadgeTier.LEGENDARY,
                record -> record.getPlaytimeMillis() >= TimeUnit.HOURS.toMillis(1000));
        
        // === SOCIAL BADGES (require additional data, but can use basic stats) ===
        registerBadge("sociable", "Sociable", "Be active with positive K/D ratio",
                Material.PLAYER_HEAD, BadgeCategory.SOCIAL, BadgeTier.BRONZE,
                record -> {
                    long kills = record.getMobKills() + record.getPlayerKills();
                    return kills > 0 && (record.getDeaths() == 0 || (double) kills / record.getDeaths() >= 1.0);
                });
        
        registerBadge("well_rounded", "Well Rounded", "Score 100+ in each skill area",
                Material.TOTEM_OF_UNDYING, BadgeCategory.SOCIAL, BadgeTier.GOLD,
                record -> {
                    // Approximate skill check: balanced activity
                    return record.getBlocksBroken() >= 1000 
                            && record.getBlocksPlaced() >= 1000 
                            && record.getMobKills() >= 100
                            && getTotalDistance(record) >= 10000;
                });
    }
    
    private static double getTotalDistance(StatsRecord record) {
        return record.getDistanceOverworld() + record.getDistanceNether() + record.getDistanceEnd();
    }
    
    private static void registerBadge(String id, String name, String description,
                                      Material icon, BadgeCategory category, BadgeTier tier,
                                      BadgeCondition condition) {
        BADGE_DEFINITIONS.add(new BadgeDefinition(id, name, description, icon, category, tier, condition));
    }
    
    /**
     * Evaluates which badges a player has earned based on their stats.
     * 
     * @param record The player's stats record
     * @return List of earned badges, sorted by tier (highest first)
     */
    public static List<AchievementBadge> evaluateBadges(StatsRecord record) {
        List<AchievementBadge> earned = new ArrayList<>();
        
        for (BadgeDefinition def : BADGE_DEFINITIONS) {
            if (def.condition().test(record)) {
                earned.add(new AchievementBadge(
                        def.id(),
                        def.name(),
                        def.description(),
                        def.icon(),
                        def.category(),
                        def.tier()
                ));
            }
        }
        
        // Sort by tier (highest first), then by category
        earned.sort((a, b) -> {
            int tierCompare = Integer.compare(b.tier().getLevel(), a.tier().getLevel());
            if (tierCompare != 0) return tierCompare;
            return a.category().name().compareTo(b.category().name());
        });
        
        return earned;
    }
    
    /**
     * Gets all available badges (for displaying progress toward unearned badges).
     * 
     * @return List of all badge definitions
     */
    public static List<BadgeDefinition> getAllBadges() {
        return new ArrayList<>(BADGE_DEFINITIONS);
    }
    
    /**
     * Gets the total number of available badges.
     * 
     * @return Total badge count
     */
    public static int getTotalBadgeCount() {
        return BADGE_DEFINITIONS.size();
    }
    
    /**
     * Counts badges earned by a player per category.
     * 
     * @param earnedBadges List of earned badges
     * @param category The category to count
     * @return Number of badges earned in that category
     */
    public static int countBadgesInCategory(List<AchievementBadge> earnedBadges, BadgeCategory category) {
        return (int) earnedBadges.stream()
                .filter(b -> b.category() == category)
                .count();
    }
    
    /**
     * Gets the highest tier badge earned in a category.
     * 
     * @param earnedBadges List of earned badges
     * @param category The category to check
     * @return The highest tier, or null if no badges in that category
     */
    public static BadgeTier getHighestTierInCategory(List<AchievementBadge> earnedBadges, BadgeCategory category) {
        return earnedBadges.stream()
                .filter(b -> b.category() == category)
                .map(AchievementBadge::tier)
                .max((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
                .orElse(null);
    }
    
    /**
     * Internal record for badge definition with evaluation condition.
     */
    public record BadgeDefinition(
            String id,
            String name,
            String description,
            Material icon,
            BadgeCategory category,
            BadgeTier tier,
            BadgeCondition condition
    ) {}
    
    /**
     * Functional interface for badge condition evaluation.
     */
    @FunctionalInterface
    public interface BadgeCondition {
        boolean test(StatsRecord record);
    }
}
