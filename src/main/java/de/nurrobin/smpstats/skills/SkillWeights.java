package de.nurrobin.smpstats.skills;

public class SkillWeights {
    private final MiningWeights mining;
    private final CombatWeights combat;
    private final ExplorationWeights exploration;
    private final BuilderWeights builder;
    private final FarmerWeights farmer;

    public SkillWeights(MiningWeights mining,
                        CombatWeights combat,
                        ExplorationWeights exploration,
                        BuilderWeights builder,
                        FarmerWeights farmer) {
        this.mining = mining;
        this.combat = combat;
        this.exploration = exploration;
        this.builder = builder;
        this.farmer = farmer;
    }

    public MiningWeights mining() {
        return mining;
    }

    public CombatWeights combat() {
        return combat;
    }

    public ExplorationWeights exploration() {
        return exploration;
    }

    public BuilderWeights builder() {
        return builder;
    }

    public FarmerWeights farmer() {
        return farmer;
    }

    public record MiningWeights(double blocksBrokenWeight) {
    }

    public record CombatWeights(double playerKillWeight, double mobKillWeight, double damageDealtWeight) {
    }

    public record ExplorationWeights(double distanceWeight, double biomesWeight) {
    }

    public record BuilderWeights(double blocksPlacedWeight) {
    }

    public record FarmerWeights(double itemsCraftedWeight, double itemsConsumedWeight) {
    }
}
