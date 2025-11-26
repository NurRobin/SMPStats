package de.nurrobin.smpstats;

import de.nurrobin.smpstats.heatmap.HotspotDefinition;
import de.nurrobin.smpstats.moments.MomentDefinition;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @Test
    void exposesAllConfiguredValues() {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(1),
                new SkillWeights.CombatWeights(2, 3, 4),
                new SkillWeights.ExplorationWeights(5, 6),
                new SkillWeights.BuilderWeights(7),
                new SkillWeights.FarmerWeights(8, 9)
        );
        MomentDefinition moment = new MomentDefinition("id", MomentDefinition.TriggerType.BLOCK_BREAK, "t", "d", 1, false, Set.of(), 0, 0, Set.of(), false, Set.of());
        HotspotDefinition hotspot = new HotspotDefinition("name", "world", 1, 2, 3, 4);

        de.nurrobin.smpstats.health.HealthThresholds thresholds = de.nurrobin.smpstats.health.HealthThresholds.defaults();
        Settings settings = new Settings(true, true, true, true, true, true, true,
                true, 1234, "KEY", 10, weights,
                true, 11L, 12L, true, 13, 1.0, List.of(moment), List.of(hotspot),
                true, 14, 15, true,
                true, true, 16, 17,
                true, 18, 0.1, 0.2, 0.3, 0.4, thresholds,
                true, 19, 20, "url", 21, 22);

        assertTrue(settings.isTrackMovement());
        assertTrue(settings.isTrackBlocks());
        assertTrue(settings.isTrackKills());
        assertTrue(settings.isTrackBiomes());
        assertTrue(settings.isTrackCrafting());
        assertTrue(settings.isTrackDamage());
        assertTrue(settings.isTrackConsumption());
        assertTrue(settings.isApiEnabled());
        assertEquals(1234, settings.getApiPort());
        assertEquals("KEY", settings.getApiKey());
        assertEquals(10, settings.getAutosaveMinutes());
        assertEquals(weights, settings.getSkillWeights());
        assertTrue(settings.isMomentsEnabled());
        assertEquals(11, settings.getDiamondWindowSeconds());
        assertEquals(12, settings.getMomentsFlushSeconds());
        assertTrue(settings.isHeatmapEnabled());
        assertEquals(13, settings.getHeatmapFlushMinutes());
        assertEquals(List.of(moment), settings.getMomentDefinitions());
        assertEquals(List.of(hotspot), settings.getHeatmapHotspots());
        assertTrue(settings.isSocialEnabled());
        assertEquals(14, settings.getSocialSampleSeconds());
        assertEquals(15, settings.getSocialNearbyRadius());
        assertTrue(settings.isTimelineEnabled());
        assertTrue(settings.isDeathReplayEnabled());
        assertTrue(settings.isDeathReplayInventoryItems());
        assertEquals(16, settings.getDeathReplayNearbyRadius());
        assertEquals(17, settings.getDeathReplayLimit());
        assertTrue(settings.isHealthEnabled());
        assertEquals(18, settings.getHealthSampleMinutes());
        assertEquals(0.1, settings.getHealthChunkWeight());
        assertEquals(0.2, settings.getHealthEntityWeight());
        assertEquals(0.3, settings.getHealthHopperWeight());
        assertEquals(0.4, settings.getHealthRedstoneWeight());
        assertTrue(settings.isStoryEnabled());
        assertEquals(19, settings.getStoryIntervalDays());
        assertEquals(20, settings.getStorySummaryHour());
        assertEquals("url", settings.getStoryWebhookUrl());
        assertEquals(21, settings.getStoryTopLimit());
        assertEquals(22, settings.getStoryRecentMoments());
    }
}
