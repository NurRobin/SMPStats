package de.nurrobin.smpstats;

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
    private final int apiPort;
    private final String apiKey;
    private final int autosaveMinutes;
    private final SkillWeights skillWeights;

    public Settings(boolean trackMovement,
                    boolean trackBlocks,
                    boolean trackKills,
                    boolean trackBiomes,
                    boolean trackCrafting,
                    boolean trackDamage,
                    boolean trackConsumption,
                    boolean apiEnabled,
                    int apiPort,
                    String apiKey,
                    int autosaveMinutes,
                    SkillWeights skillWeights) {
        this.trackMovement = trackMovement;
        this.trackBlocks = trackBlocks;
        this.trackKills = trackKills;
        this.trackBiomes = trackBiomes;
        this.trackCrafting = trackCrafting;
        this.trackDamage = trackDamage;
        this.trackConsumption = trackConsumption;
        this.apiEnabled = apiEnabled;
        this.apiPort = apiPort;
        this.apiKey = apiKey;
        this.autosaveMinutes = autosaveMinutes;
        this.skillWeights = skillWeights;
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
}
