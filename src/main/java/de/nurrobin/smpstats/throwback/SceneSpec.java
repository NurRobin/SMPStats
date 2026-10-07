package de.nurrobin.smpstats.throwback;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Pose;

import java.util.List;

/**
 * Description of one stage set: how the figure stands and what stands around it. Positions of props are in the
 * figure's frame: {@code right} to its right, {@code up} above the floor, {@code forward} the way it looks.
 * The yaw of a stage points at the back wall of the alcove; {@code turn} rotates the figure from there
 * (0 = facing the wall with its back to the viewer, 180 = facing the viewer, 90 = side view).
 * A null {@code pose} means no figure, only the props (info boards).
 */
public record SceneSpec(Pose pose, Material mainHand, Material offHand, Material helmet, Material chestplate,
                        double lift, Material swingBlock, double turn, List<Prop> props) {

    public record Prop(Material material, boolean block, double right, double up, double forward, float scale) {
        public static Prop block(Material m, double right, double up, double forward, float scale) {
            return new Prop(m, true, right, up, forward, scale);
        }

        public static Prop item(Material m, double right, double up, double forward, float scale) {
            return new Prop(m, false, right, up, forward, scale);
        }
    }

    /** True when the figure swings its arm in a loop (mining, fighting, fishing). */
    public boolean animated() {
        return swingBlock != null;
    }

    public static Particle swingParticle() {
        return Particle.BLOCK;
    }
}
