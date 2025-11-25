package de.nurrobin.smpstats.moments;

import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

public class MomentDefinition {
    public enum TriggerType {
        BLOCK_BREAK,
        DEATH,
        DEATH_FALL,
        FIRST_DEATH,
        DAMAGE_LOW_HP,
        DEATH_EXPLOSION
    }

    private final String id;
    private final TriggerType trigger;
    private final String title;
    private final String detail;
    private final long mergeSeconds;
    private final boolean firstOnly;
    private final Set<Material> materials;
    private final double minFallDistance;
    private final double maxHealthAfterDamage;
    private final Set<String> causes;
    private final boolean requireSelf;

    public MomentDefinition(String id,
                            TriggerType trigger,
                            String title,
                            String detail,
                            long mergeSeconds,
                            boolean firstOnly,
                            Set<Material> materials,
                            double minFallDistance,
                            double maxHealthAfterDamage,
                            Set<String> causes,
                            boolean requireSelf) {
        this.id = id;
        this.trigger = trigger;
        this.title = title;
        this.detail = detail;
        this.mergeSeconds = mergeSeconds;
        this.firstOnly = firstOnly;
        this.materials = materials != null ? materials : new HashSet<>();
        this.minFallDistance = minFallDistance;
        this.maxHealthAfterDamage = maxHealthAfterDamage;
        this.causes = causes != null ? causes : new HashSet<>();
        this.requireSelf = requireSelf;
    }

    public String getId() {
        return id;
    }

    public TriggerType getTrigger() {
        return trigger;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public long getMergeSeconds() {
        return mergeSeconds;
    }

    public boolean isFirstOnly() {
        return firstOnly;
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    public double getMinFallDistance() {
        return minFallDistance;
    }

    public double getMaxHealthAfterDamage() {
        return maxHealthAfterDamage;
    }

    public Set<String> getCauses() {
        return causes;
    }

    public boolean isRequireSelf() {
        return requireSelf;
    }

    public boolean matchesMaterial(Material material) {
        return materials.isEmpty() || materials.contains(material);
    }

    public boolean matchesCause(String cause) {
        return causes.isEmpty() || causes.contains(cause);
    }
}
