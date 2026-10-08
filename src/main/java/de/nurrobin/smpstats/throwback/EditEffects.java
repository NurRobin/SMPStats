package de.nurrobin.smpstats.throwback;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Draws the edit boxes; an interface because MockBukkit cannot show what a single player sees. */
@FunctionalInterface
interface EditEffects {
    /** One dust point, visible to {@code viewer} only. */
    void dust(Player viewer, Location at, Color color);

    EditEffects PAPER = (viewer, at, color) ->
            viewer.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.0f));
}
