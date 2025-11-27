package de.nurrobin.smpstats;

import de.nurrobin.smpstats.health.HealthThresholds;
import de.nurrobin.smpstats.skills.SkillWeights;

public class Settings {
    private final boolean trackMovement;
    private final boolean trackBlocks;
    private final boolean trackKills;
    private final boolean trackBiomes;
    private final boolean trackCrafting;
    private final boolean trackDamage;
    private final boolean trackConsumption;
    private final boolean apiEnabled;
    private final String apiBindAddress;
    private final int apiPort;
    private final String apiKey;
    private final int autosaveMinutes;
    private final SkillWeights skillWeights;
    private final boolean momentsEnabled;
    private final long diamondWindowSeconds;
    private final long momentsFlushSeconds;
    private final boolean heatmapEnabled;
    private final int heatmapFlushMinutes;
    private final double heatmapDecayHalfLifeHours;
    private final java.util.List<de.nurrobin.smpstats.moments.MomentDefinition> momentDefinitions;
    private final java.util.List<de.nurrobin.smpstats.heatmap.HotspotDefinition> heatmapHotspots;
    private final boolean socialEnabled;
    private final int socialSampleSeconds;
    private final int socialNearbyRadius;
    private final boolean timelineEnabled;
    private final boolean deathReplayEnabled;
    private final boolean deathReplayInventoryItems;
    private final int deathReplayNearbyRadius;
    private final int deathReplayLimit;
    private final boolean healthEnabled;
    private final int healthSampleMinutes;
    private final double healthChunkWeight;
    private final double healthEntityWeight;
    private final double healthHopperWeight;
    private final double healthRedstoneWeight;
    private final HealthThresholds healthThresholds;
    private final boolean storyEnabled;
    private final int storyIntervalDays;
    private final int storySummaryHour;
    private final String storyWebhookUrl;
    private final int storyTopLimit;
    private final int storyRecentMoments;
    
    // Dashboard settings
    private final DashboardSettings dashboardSettings;
    
    /**
     * Holds all dashboard-related configuration.
     */
    public record DashboardSettings(
            boolean enabled,
            String bindAddress,
            int port,
            PublicSettings publicSettings,
            AdminSettings adminSettings
    ) {
        public static DashboardSettings defaults() {
            return new DashboardSettings(
                    true,
                    "0.0.0.0",
                    8080,
                    PublicSettings.defaults(),
                    AdminSettings.defaults()
            );
        }
    }
    
    /**
     * Configuration for the public (unauthenticated) section of the dashboard.
     */
    public record PublicSettings(
            boolean enabled,
            boolean showOnlinePlayers,
            boolean showLeaderboards,
            boolean showRecentMoments,
            boolean showServerStats
    ) {
        public static PublicSettings defaults() {
            return new PublicSettings(true, true, true, true, true);
        }
    }
    
    /**
     * Configuration for the admin (authenticated) section of the dashboard.
     */
    public record AdminSettings(
            boolean enabled,
            String password,
            int sessionTimeoutMinutes,
            boolean showHealthMetrics,
            boolean showHeatmaps,
            boolean showSocialData,
            boolean showDeathReplays
    ) {
        public static AdminSettings defaults() {
            return new AdminSettings(true, "ChangeThisAdminPassword", 60, true, true, true, true);
        }
    }

    public Settings(boolean trackMovement,
                    boolean trackBlocks,
                    boolean trackKills,
                    boolean trackBiomes,
                    boolean trackCrafting,
                    boolean trackDamage,
                    boolean trackConsumption,
                    boolean apiEnabled,
                    String apiBindAddress,
                    int apiPort,
                    String apiKey,
                    int autosaveMinutes,
                    SkillWeights skillWeights,
                    boolean momentsEnabled,
                    long diamondWindowSeconds,
                    long momentsFlushSeconds,
                    boolean heatmapEnabled,
                    int heatmapFlushMinutes,
                    double heatmapDecayHalfLifeHours,
                    java.util.List<de.nurrobin.smpstats.moments.MomentDefinition> momentDefinitions,
                    java.util.List<de.nurrobin.smpstats.heatmap.HotspotDefinition> heatmapHotspots,
                    boolean socialEnabled,
                    int socialSampleSeconds,
                    int socialNearbyRadius,
                    boolean timelineEnabled,
                    boolean deathReplayEnabled,
                    boolean deathReplayInventoryItems,
                    int deathReplayNearbyRadius,
                    int deathReplayLimit,
                    boolean healthEnabled,
                    int healthSampleMinutes,
                    double healthChunkWeight,
                    double healthEntityWeight,
                    double healthHopperWeight,
                    double healthRedstoneWeight,
                    HealthThresholds healthThresholds,
                    boolean storyEnabled,
                    int storyIntervalDays,
                    int storySummaryHour,
                    String storyWebhookUrl,
                    int storyTopLimit,
                    int storyRecentMoments,
                    DashboardSettings dashboardSettings) {
        this.trackMovement = trackMovement;
        this.trackBlocks = trackBlocks;
        this.trackKills = trackKills;
        this.trackBiomes = trackBiomes;
        this.trackCrafting = trackCrafting;
        this.trackDamage = trackDamage;
        this.trackConsumption = trackConsumption;
        this.apiEnabled = apiEnabled;
        this.apiBindAddress = apiBindAddress;
        this.apiPort = apiPort;
        this.apiKey = apiKey;
        this.autosaveMinutes = autosaveMinutes;
        this.skillWeights = skillWeights;
        this.momentsEnabled = momentsEnabled;
        this.diamondWindowSeconds = diamondWindowSeconds;
        this.momentsFlushSeconds = momentsFlushSeconds;
        this.heatmapEnabled = heatmapEnabled;
        this.heatmapFlushMinutes = heatmapFlushMinutes;
        this.heatmapDecayHalfLifeHours = heatmapDecayHalfLifeHours;
        this.momentDefinitions = momentDefinitions;
        this.heatmapHotspots = heatmapHotspots;
        this.socialEnabled = socialEnabled;
        this.socialSampleSeconds = socialSampleSeconds;
        this.socialNearbyRadius = socialNearbyRadius;
        this.timelineEnabled = timelineEnabled;
        this.deathReplayEnabled = deathReplayEnabled;
        this.deathReplayInventoryItems = deathReplayInventoryItems;
        this.deathReplayNearbyRadius = deathReplayNearbyRadius;
        this.deathReplayLimit = deathReplayLimit;
        this.healthEnabled = healthEnabled;
        this.healthSampleMinutes = healthSampleMinutes;
        this.healthChunkWeight = healthChunkWeight;
        this.healthEntityWeight = healthEntityWeight;
        this.healthHopperWeight = healthHopperWeight;
        this.healthRedstoneWeight = healthRedstoneWeight;
        this.healthThresholds = healthThresholds;
        this.storyEnabled = storyEnabled;
        this.storyIntervalDays = storyIntervalDays;
        this.storySummaryHour = storySummaryHour;
        this.storyWebhookUrl = storyWebhookUrl;
        this.storyTopLimit = storyTopLimit;
        this.storyRecentMoments = storyRecentMoments;
        this.dashboardSettings = dashboardSettings;
    }

    public boolean isTrackMovement() {
        return trackMovement;
    }

    public boolean isTrackBlocks() {
        return trackBlocks;
    }

    public boolean isTrackKills() {
        return trackKills;
    }

    public boolean isTrackBiomes() {
        return trackBiomes;
    }

    public boolean isTrackCrafting() {
        return trackCrafting;
    }

    public boolean isTrackDamage() {
        return trackDamage;
    }

    public boolean isTrackConsumption() {
        return trackConsumption;
    }

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    public int getApiPort() {
        return apiPort;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getAutosaveMinutes() {
        return autosaveMinutes;
    }

    public SkillWeights getSkillWeights() {
        return skillWeights;
    }

    public boolean isMomentsEnabled() {
        return momentsEnabled;
    }

    public long getDiamondWindowSeconds() {
        return diamondWindowSeconds;
    }

    public long getMomentsFlushSeconds() {
        return momentsFlushSeconds;
    }

    public boolean isHeatmapEnabled() {
        return heatmapEnabled;
    }

    public int getHeatmapFlushMinutes() {
        return heatmapFlushMinutes;
    }

    public double getHeatmapDecayHalfLifeHours() {
        return heatmapDecayHalfLifeHours;
    }

    public java.util.List<de.nurrobin.smpstats.moments.MomentDefinition> getMomentDefinitions() {
        return momentDefinitions;
    }

    public java.util.List<de.nurrobin.smpstats.heatmap.HotspotDefinition> getHeatmapHotspots() {
        return heatmapHotspots;
    }

    public boolean isSocialEnabled() {
        return socialEnabled;
    }

    public int getSocialSampleSeconds() {
        return socialSampleSeconds;
    }

    public int getSocialNearbyRadius() {
        return socialNearbyRadius;
    }

    public boolean isTimelineEnabled() {
        return timelineEnabled;
    }

    public boolean isDeathReplayEnabled() {
        return deathReplayEnabled;
    }

    public boolean isDeathReplayInventoryItems() {
        return deathReplayInventoryItems;
    }

    public int getDeathReplayNearbyRadius() {
        return deathReplayNearbyRadius;
    }

    public int getDeathReplayLimit() {
        return deathReplayLimit;
    }

    public boolean isHealthEnabled() {
        return healthEnabled;
    }

    public int getHealthSampleMinutes() {
        return healthSampleMinutes;
    }

    public double getHealthChunkWeight() {
        return healthChunkWeight;
    }

    public double getHealthEntityWeight() {
        return healthEntityWeight;
    }

    public double getHealthHopperWeight() {
        return healthHopperWeight;
    }

    public double getHealthRedstoneWeight() {
        return healthRedstoneWeight;
    }

    public HealthThresholds getHealthThresholds() {
        return healthThresholds;
    }

    public boolean isStoryEnabled() {
        return storyEnabled;
    }

    public int getStoryIntervalDays() {
        return storyIntervalDays;
    }

    public int getStorySummaryHour() {
        return storySummaryHour;
    }

    public String getStoryWebhookUrl() {
        return storyWebhookUrl;
    }

    public int getStoryTopLimit() {
        return storyTopLimit;
    }

    public int getStoryRecentMoments() {
        return storyRecentMoments;
    }
    
    public String getApiBindAddress() {
        return apiBindAddress;
    }
    
    public DashboardSettings getDashboardSettings() {
        return dashboardSettings;
    }
}
