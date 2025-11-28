package de.nurrobin.smpstats.gui;

import org.bukkit.Material;

/**
 * Represents a visual achievement badge that can be displayed in GUIs.
 * Badges are earned based on player statistics and milestones.
 */
public record AchievementBadge(
        String id,
        String name,
        String description,
        Material icon,
        BadgeCategory category,
        BadgeTier tier
) {
    
    /**
     * Categories of achievement badges.
     */
    public enum BadgeCategory {
        COMBAT("⚔ Combat", "Combat-related achievements"),
        EXPLORATION("🧭 Explorer", "Exploration achievements"),
        MINING("⛏ Mining", "Mining achievements"),
        BUILDING("🏗 Builder", "Building achievements"),
        SOCIAL("👥 Social", "Social interaction achievements"),
        SURVIVAL("💀 Survival", "Survival-related achievements"),
        DEDICATION("⏱ Dedication", "Time and dedication achievements");
        
        private final String displayName;
        private final String description;
        
        BadgeCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Tier levels for badges, representing difficulty/rarity.
     */
    public enum BadgeTier {
        BRONZE(1, "🥉 Bronze", 0xCD7F32),
        SILVER(2, "🥈 Silver", 0xC0C0C0),
        GOLD(3, "🥇 Gold", 0xFFD700),
        DIAMOND(4, "💎 Diamond", 0x00FFFF),
        LEGENDARY(5, "🌟 Legendary", 0xFF00FF);
        
        private final int level;
        private final String displayName;
        private final int color;
        
        BadgeTier(int level, String displayName, int color) {
            this.level = level;
            this.displayName = displayName;
            this.color = color;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getColor() {
            return color;
        }
    }
}
