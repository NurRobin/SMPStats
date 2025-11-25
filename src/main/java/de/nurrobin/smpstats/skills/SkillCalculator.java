package de.nurrobin.smpstats.skills;

import de.nurrobin.smpstats.StatsRecord;

public class SkillCalculator {
    private SkillWeights weights;

    public SkillCalculator(SkillWeights weights) {
        this.weights = weights;
    }

    public void updateWeights(SkillWeights weights) {
        this.weights = weights;
    }

    public SkillProfile calculate(StatsRecord record) {
        double mining = record.getBlocksBroken() * weights.mining().blocksBrokenWeight();

        double combat = record.getPlayerKills() * weights.combat().playerKillWeight()
                + record.getMobKills() * weights.combat().mobKillWeight()
                + record.getDamageDealt() * weights.combat().damageDealtWeight();

        double totalDistance = record.getDistanceOverworld() + record.getDistanceNether() + record.getDistanceEnd();
        double exploration = totalDistance * weights.exploration().distanceWeight()
                + record.getBiomesVisited().size() * weights.exploration().biomesWeight();

        double builder = record.getBlocksPlaced() * weights.builder().blocksPlacedWeight();

        double farmer = record.getItemsCrafted() * weights.farmer().itemsCraftedWeight()
                + record.getItemsConsumed() * weights.farmer().itemsConsumedWeight();

        return new SkillProfile(mining, combat, exploration, builder, farmer);
    }
}
