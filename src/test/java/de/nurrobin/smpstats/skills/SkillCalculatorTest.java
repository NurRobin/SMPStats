package de.nurrobin.smpstats.skills;

import de.nurrobin.smpstats.StatsRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillCalculatorTest {

    @Test
    void calculatesScoresWithWeights() {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(0.1),
                new SkillWeights.CombatWeights(5, 2, 0.01),
                new SkillWeights.ExplorationWeights(0.01, 3),
                new SkillWeights.BuilderWeights(0.2),
                new SkillWeights.FarmerWeights(0.5, 0.1)
        );
        SkillCalculator calc = new SkillCalculator(weights);

        StatsRecord record = new StatsRecord(UUID.randomUUID(), "Test");
        record.setBlocksBroken(100);
        record.setBlocksPlaced(50);
        record.setPlayerKills(2);
        record.setMobKills(5);
        record.setDamageDealt(200);
        record.setDistanceOverworld(1000);
        record.setDistanceNether(100);
        record.setDistanceEnd(0);
        record.setBiomesVisited(java.util.Set.of("A", "B", "C"));
        record.setItemsCrafted(10);
        record.setItemsConsumed(20);

        SkillProfile profile = calc.calculate(record);

        assertEquals(10.0, profile.mining(), 0.0001);
        assertEquals(2 * 5 + 5 * 2 + 200 * 0.01, profile.combat(), 0.0001);
        assertEquals((1100) * 0.01 + 3 * 3, profile.exploration(), 0.0001);
        assertEquals(50 * 0.2, profile.builder(), 0.0001);
        assertEquals(10 * 0.5 + 20 * 0.1, profile.farmer(), 0.0001);
    }
}
