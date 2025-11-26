package de.nurrobin.smpstats.skills;

import de.nurrobin.smpstats.StatsRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillCalculatorTest {

    @Test
    void calculatesSkillScoresUsingWeights() {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(1.0),
                new SkillWeights.CombatWeights(2.0, 1.0, 0.5),
                new SkillWeights.ExplorationWeights(0.1, 3.0),
                new SkillWeights.BuilderWeights(0.2),
                new SkillWeights.FarmerWeights(0.3, 0.1)
        );

        StatsRecord record = new StatsRecord(UUID.randomUUID(), "Alex");
        record.setBlocksBroken(10);
        record.setPlayerKills(2);
        record.setMobKills(5);
        record.setDamageDealt(20);
        record.setDistanceOverworld(100);
        record.setDistanceNether(50);
        record.setDistanceEnd(25);
        record.setBlocksPlaced(8);
        record.setItemsCrafted(7);
        record.setItemsConsumed(4);
        record.addBiome("plains");
        record.addBiome("desert");

        SkillCalculator calculator = new SkillCalculator(weights);
        SkillProfile profile = calculator.calculate(record);

        assertEquals(10.0, profile.mining());
        assertEquals(19.0, profile.combat());
        assertEquals(23.5, profile.exploration());
        assertEquals(1.6, profile.builder());
        assertEquals(2.5, profile.farmer());
        assertEquals(56.6, profile.total(), 0.0001);
    }
}
