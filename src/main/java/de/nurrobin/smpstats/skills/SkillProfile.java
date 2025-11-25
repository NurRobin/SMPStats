package de.nurrobin.smpstats.skills;

public class SkillProfile {
    private final double mining;
    private final double combat;
    private final double exploration;
    private final double builder;
    private final double farmer;

    public SkillProfile(double mining, double combat, double exploration, double builder, double farmer) {
        this.mining = mining;
        this.combat = combat;
        this.exploration = exploration;
        this.builder = builder;
        this.farmer = farmer;
    }

    public double mining() {
        return mining;
    }

    public double combat() {
        return combat;
    }

    public double exploration() {
        return exploration;
    }

    public double builder() {
        return builder;
    }

    public double farmer() {
        return farmer;
    }

    public double total() {
        return mining + combat + exploration + builder + farmer;
    }
}
